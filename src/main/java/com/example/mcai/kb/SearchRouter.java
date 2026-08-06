package com.example.mcai.kb;

import com.example.mcai.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * 知识库搜索路由器 — 纯在线 Wiki 搜索。
 * 策略：仅通过 WikiSearchProvider 搜索在线 Wiki，不做本地兜底。
 */
public class SearchRouter implements SearchProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-SearchRouter");
    private static final long WIKI_TIMEOUT_MS = 8000;

    private final ModConfig config;
    private final SearchProvider wikiProvider;
    private final ExecutorService executor;

    public SearchRouter(ModConfig config, SearchProvider wikiProvider) {
        this.config = config;
        this.wikiProvider = wikiProvider;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "MCAI-SearchRouter");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public String name() { return "wiki"; }

    @Override
    public boolean isAvailable() {
        return wikiProvider.isAvailable();
    }

    @Override
    public SearchResult search(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return SearchResult.empty(name(), true);
        }

        try {
            Future<SearchResult> future = executor.submit(() -> wikiProvider.search(query, maxResults));
            SearchResult wiki = future.get(WIKI_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (wiki != null && !wiki.isEmpty()) {
                LOGGER.debug("Wiki search hit for '{}' with {} results", query, wiki.items.size());
                return wiki;
            } else if (wiki != null && wiki.error != null) {
                LOGGER.warn("Wiki search returned error: {}", wiki.error);
                return wiki; // 返回错误结果，不要丢弃
            } else if (wiki != null) {
                LOGGER.debug("Wiki search for '{}' returned 0 results", query);
            }
        } catch (TimeoutException e) {
            LOGGER.warn("Wiki search timeout for '{}'", query);
        } catch (Exception e) {
            LOGGER.warn("Wiki search failed for '{}': {}", query, e.getMessage());
        }

        return SearchResult.empty(name(), true);
    }

    @Override
    public String read(String title) {
        if (title == null || title.isBlank()) return "标题为空";

        try {
            Future<String> future = executor.submit(() -> wikiProvider.read(title));
            String wiki = future.get(WIKI_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (wiki != null && !wiki.contains("未找到")) return wiki;
            return wiki != null ? wiki : "未找到条目: " + title;
        } catch (TimeoutException e) {
            LOGGER.warn("Wiki read timeout for '{}'", title);
            return "Wiki 读取超时: " + title;
        } catch (Exception e) {
            LOGGER.warn("Wiki read failed for '{}': {}", title, e.getMessage());
            return "Wiki 读取失败: " + title;
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public ExecutorService getExecutor() { return executor; }
}