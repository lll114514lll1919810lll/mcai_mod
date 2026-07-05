package com.example.mcai.kb;

import java.util.List;

/**
 * 搜索结果，由 SearchProvider 返回。本地和在线搜索都返回统一格式。
 */
public class SearchResult {
    public final String provider;
    public final List<Item> items;
    public final boolean offline;
    public final String error;

    public SearchResult(String provider, List<Item> items, boolean offline) {
        this(provider, items, offline, null);
    }

    public SearchResult(String provider, List<Item> items, boolean offline, String error) {
        this.provider = provider;
        this.items = items != null ? List.copyOf(items) : List.of();
        this.offline = offline;
        this.error = error;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public static SearchResult error(String provider, String error) {
        return new SearchResult(provider, List.of(), false, error);
    }

    public static SearchResult empty(String provider, boolean offline) {
        return new SearchResult(provider, List.of(), offline);
    }

    public static class Item {
        public final String title;
        public final String summary;
        public final String url;
        public final double score;

        public Item(String title, String summary, String url, double score) {
            this.title = title != null ? title : "";
            this.summary = summary != null ? summary : "";
            this.url = url != null ? url : "";
            this.score = score;
        }
    }
}
