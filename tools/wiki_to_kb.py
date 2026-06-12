#!/usr/bin/env python3
"""
Wiki → MCAI Knowledge Base converter.

Scrapes a MediaWiki wiki via its API and converts pages into the MCAI KB JSON format.

Usage:
    python tools/wiki_to_kb.py https://biomesoplenty.wiki.gg biomesoplenty.json
    python tools/wiki_to_kb.py https://some-mod.wiki.gg output.json --limit 50
    python tools/wiki_to_kb.py https://some-mod.wiki.gg output.json --namespace 0 --skip-template --min-length 200
"""

import argparse
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from html.parser import HTMLParser


# ── Helpers ──────────────────────────────────────────────────────────────

class HTMLStripper(HTMLParser):
    """Strip HTML tags, keep text."""
    def __init__(self):
        super().__init__()
        self.text = []
    def handle_data(self, data):
        self.text.append(data)
    def get_text(self):
        return ''.join(self.text)


def strip_html(html):
    stripper = HTMLStripper()
    stripper.feed(html)
    return stripper.get_text()


def strip_wiki_markup(text):
    """Remove common MediaWiki markup for clean summary text."""
    text = re.sub(r"'''?|''?", '', text)                # bold/italic markers
    text = re.sub(r'\[\[([^\]|]+)(?:\|[^\]]+)?\]\]', r'\1', text)  # [[link|text]] → link
    text = re.sub(r'\{\{[^}]*\}\}', '', text)           # {{templates}}
    text = re.sub(r'<[^>]+>', '', text)                 # <html>
    text = re.sub(r'={2,}\s*[^=]+\s*={2,}', '', text)   # == headings ==
    text = re.sub(r'\*+', '', text)                     # * list markers
    text = re.sub(r'#+', '', text)                      # # numbered lists
    text = re.sub(r'\n\s*\n', '\n', text).strip()
    return text


def extract_keywords(title, content):
    """Generate keyword suggestions from title and content."""
    words = set()
    # Split on non-alphanumeric (keeping CJK as whole phrases)
    for part in re.split(r'[^a-zA-Z0-9\u4e00-\u9fff]+', title.lower()):
        if len(part) > 1:
            words.add(part)
    # Extract capitalized words from content (proper nouns)
    for match in re.finditer(r'\b([A-Z][a-z]+)\b', content):
        w = match.group(1).lower()
        if len(w) > 2 and w not in ('the', 'this', 'that', 'with', 'from'):
            words.add(w)
    return sorted(words)[:10]


# ── MediaWiki API ────────────────────────────────────────────────────────

def api_request(api_url, params):
    """Call the MediaWiki API and return parsed JSON."""
    params['format'] = 'json'
    url = api_url + '?' + urllib.parse.urlencode(params)
    headers = {'User-Agent': 'MCAI-KB-Scraper/1.0'}
    req = urllib.request.Request(url, headers=headers)
    print(f'  Fetching {url[:120]}... ', end='', flush=True)
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read())
        print('OK')
        return data


def list_all_pages(api_url, namespace=0, limit=500):
    """Yield (title, pageid) for all pages in the given namespace."""
    params = {
        'action': 'query',
        'list': 'allpages',
        'apnamespace': namespace,
        'aplimit': min(limit, 500),
    }
    while True:
        data = api_request(api_url, params)
        pages = data.get('query', {}).get('allpages', [])
        for p in pages:
            yield p['title'], p['pageid']
        if 'continue' in data and 'apcontinue' in data['continue']:
            params['apcontinue'] = data['continue']['apcontinue']
        else:
            break


def fetch_page_content(api_url, title):
    """Get full page content as plain text."""
    # try parse first (full rendered text)
    params = {'action': 'parse', 'page': title, 'prop': 'text', 'contentmodel': 'wikitext'}
    try:
        data = api_request(api_url, params)
        text = data.get('parse', {}).get('text', {}).get('*', '')
        if text:
            return strip_html(text)
    except Exception:
        pass
    # fallback: try query + extracts (no intro limit)
    params = {'action': 'query', 'titles': title, 'prop': 'extracts', 'explaintext': '1'}
    try:
        data = api_request(api_url, params)
        pages = data.get('query', {}).get('pages', {})
        for pid, page in pages.items():
            if pid != '-1':
                return page.get('extract', '')
    except Exception:
        pass
    return ''


# ── Entry generation ─────────────────────────────────────────────────────

def page_to_entry(api_url, title, min_length=100):
    """Fetch a page and convert to KB Entry dict, or None if too short."""
    content = fetch_page_content(api_url, title)
    if len(content) < min_length:
        return None

    summary = strip_wiki_markup(content[:500])
    if len(summary) > 300:
        summary = summary[:300] + '...'
    keywords = extract_keywords(title, content)

    return {
        'title': title,
        'keywords': keywords,
        'summary': summary,
        'content': content if content else extract,
    }


# ── CLI ──────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description='Scrape a MediaWiki wiki into MCAI KB JSON format')
    parser.add_argument('base_url', help='Wiki base URL, e.g. https://biomesoplenty.wiki.gg')
    parser.add_argument('output', help='Output JSON file path')
    parser.add_argument('--limit', type=int, default=0, help='Max pages to process (0 = all)')
    parser.add_argument('--namespace', type=int, default=0, help='Namespace to scrape (0 = articles)')
    parser.add_argument('--min-length', type=int, default=200, help='Minimum content length to include')
    parser.add_argument('--delay', type=float, default=0.5, help='Seconds between API calls')
    parser.add_argument('--skip-template', action='store_true', default=True,
                        help='Skip titles containing / (subpages/templates)')
    args = parser.parse_args()

    api_url = args.base_url.rstrip('/') + '/api.php'
    entries = []
    skipped = 0
    count = 0

    print(f'Scraping {api_url} ...')
    print(f'  Namespace: {args.namespace}, Min length: {args.min_length}')
    if args.limit:
        print(f'  Max pages: {args.limit}')
    print()
    for title, pageid in list_all_pages(api_url, args.namespace):
        if args.limit > 0 and count >= args.limit:
            break
        if args.skip_template and '/' in title and not title.startswith('Module:'):
            skipped += 1
            continue

        entry = page_to_entry(api_url, title, args.min_length)
        if entry:
            entries.append(entry)
            count += 1
            print(f'  [{count}] {title} ({len(entry["content"])} chars)')
        else:
            skipped += 1

        time.sleep(args.delay)

    # Write output
    with open(args.output, 'w', encoding='utf-8') as f:
        json.dump(entries, f, ensure_ascii=False, indent=2)

    print(f'\nDone: {len(entries)} entries written to {args.output}')
    print(f'Skipped: {skipped} pages (too short / subpages / missing)')


if __name__ == '__main__':
    main()
