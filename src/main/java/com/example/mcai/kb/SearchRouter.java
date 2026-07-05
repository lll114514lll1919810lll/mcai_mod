package com.example.mcai.kb;

import com.example.mcai.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/**
 * 知识库搜索路由器。
 * 策略：本地优先 → Wiki 在线兜底 → 可选通用搜索；超时自动降级。
 */
public class SearchRouter implements SearchProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-SearchRouter");
    private static final long WIKI_TIMEOUT_MS = 8000;


    private final ModConfig config;
    private final SearchProvider localProvider;
    private final SearchProvider wikiProvider;
    private final ExecutorService executor;

    public SearchRouter(ModConfig config, SearchProvider localProvider, SearchProvider wikiProvider) {
        this.config = config;
        this.localProvider = localProvider;
        this.wikiProvider = wikiProvider;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "MCAI-SearchRouter");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public String name() { return "router"; }

    @Override
    public boolean isAvailable() {
        return localProvider.isAvailable() || wikiProvider.isAvailable();
    }

    @Override
    public SearchResult search(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return SearchResult.empty(name(), true);
        }

        List<SearchResult.Item> merged = new ArrayList<>();
        Set<String> seenTitles = new LinkedHashSet<>();
        boolean onlineUsed = false;

        // L1: Wiki 在线搜索（优先）
        if (config.isEnableOnlineWiki()) {
            try {
                Future<SearchResult> future = executor.submit(() -> wikiProvider.search(query, maxResults));
                SearchResult wiki = future.get(WIKI_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                onlineUsed = true;
                if (wiki != null && !wiki.isEmpty()) {
                    addUnique(wiki.items, merged, seenTitles);
                    LOGGER.debug("Wiki search hit for '{}' with {} results", query, wiki.items.size());
                } else if (wiki != null && wiki.error != null) {
                    LOGGER.warn("Wiki search returned error: {}", wiki.error);
                }
            } catch (TimeoutException e) {
                LOGGER.warn("Wiki search timeout for '{}', falling back to local KB", query);
            } catch (Exception e) {
                LOGGER.warn("Wiki search failed for '{}': {}", query, e.getMessage());
            }
        }

        // L2: 本地搜索兜底（Wiki 未开启、Wiki 无结果、Wiki 超时/失败时）
        if (merged.isEmpty()) {
            SearchResult local = localProvider.search(query, maxResults);
            if (local != null && !local.isEmpty()) {
                addUnique(local.items, merged, seenTitles);
            }
        }

        // 合并后按 score 排序并截断
        merged.sort(Comparator.comparingDouble((SearchResult.Item i) -> i.score).reversed());
        if (merged.size() > maxResults) {
            merged = merged.subList(0, maxResults);
        }

        if (merged.isEmpty()) {
            return SearchResult.empty(name(), !onlineUsed);
        }
        return new SearchResult(name(), merged, !onlineUsed);
    }

    @Override
    public String read(String title) {
        if (title == null || title.isBlank()) return "标题为空";

        // 在线优先：先读 Wiki，再读本地兜底
        if (config.isEnableOnlineWiki()) {
            try {
                Future<String> future = executor.submit(() -> wikiProvider.read(title));
                String wiki = future.get(WIKI_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (wiki != null && !wiki.contains("未找到")) return wiki;
            } catch (TimeoutException e) {
                LOGGER.warn("Wiki read timeout for '{}'", title);
            } catch (Exception e) {
                LOGGER.warn("Wiki read failed for '{}': {}", title, e.getMessage());
            }
        }

        String local = localProvider.read(title);
        if (local != null && !local.contains("未找到")) return local;
        return local != null ? local : "未找到条目: " + title;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void addUnique(List<SearchResult.Item> source, List<SearchResult.Item> target, Set<String> seen) {
        for (SearchResult.Item item : source) {
            String key = item.title.toLowerCase(java.util.Locale.ROOT).trim();
            if (key.isEmpty() || seen.contains(key)) continue;
            seen.add(key);
            target.add(item);
        }
    }

    public SearchProvider getLocalProvider() { return localProvider; }
    public SearchProvider getWikiProvider() { return wikiProvider; }
}
