package com.example.mcai.kb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 本地 JSON 知识库。既是传统入口，也是 SearchProvider 实现。
 */
public class KnowledgeBase implements SearchProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-KB");
    private static final Gson GSON = new GsonBuilder().create();
    private static final int MAX_ENTRIES = 50000;
    private static final long MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024; // 50MB per file

    private List<Entry> entries = List.of();

    public static class Entry {
        private final String title;
        private final List<String> keywords;
        private final String summary;
        private final String content;
        private final String titleLower;
        private final String keywordsLower;
        private final String summaryLower;

        public Entry(String title, List<String> keywords, String summary, String content) {
            this.title = title != null ? title : "";
            this.keywords = keywords;
            this.summary = summary != null ? summary : "";
            this.content = content != null ? content : "";
            this.titleLower = this.title.toLowerCase(Locale.ROOT);
            this.keywordsLower = String.join(" ", keywords != null ? keywords : List.of()).toLowerCase(Locale.ROOT);
            this.summaryLower = this.summary.toLowerCase(Locale.ROOT);
        }

        public String title() { return title; }
        public List<String> keywords() { return keywords; }
        public String summary() { return summary; }
        public String content() { return content; }
        String titleLower() { return titleLower; }
        String keywordsLower() { return keywordsLower; }
        String summaryLower() { return summaryLower; }
    }

    @Override
    public String name() { return "local"; }

    @Override
    public boolean isAvailable() { return isLoaded(); }

    @Override
    public SearchResult search(String query, int maxResults) {
        if (query.isBlank()) return SearchResult.empty(name(), true);
        List<SearchResult.Item> items = searchItems(query, maxResults);
        if (items.isEmpty()) return SearchResult.empty(name(), true);
        return new SearchResult(name(), items, true);
    }

    @Override
    public String read(String title) {
        if (title == null || title.isBlank()) return "标题为空";
        return readLocal(title);
    }

    public void load(Path externalDir) {
        List<Entry> all = new ArrayList<>();
        var seen = new HashSet<String>();

        if (externalDir != null) {
            try {
                Files.createDirectories(externalDir);
                exportExampleIfNeeded(externalDir);
            } catch (Exception e) {
                LOGGER.warn("Failed to setup external KB directory", e);
            }
        }

        if (externalDir != null && Files.isDirectory(externalDir)) {
            try (var files = Files.list(externalDir)) {
                files.filter(f -> f.toString().endsWith(".json") && !f.getFileName().toString().startsWith("_"))
                        .forEach(f -> {
                    if (all.size() >= MAX_ENTRIES) {
                        LOGGER.warn("KB entry limit reached ({}), skipping remaining files", MAX_ENTRIES);
                        return;
                    }
                    try {
                        long fileSize = Files.size(f);
                        if (fileSize > MAX_FILE_SIZE_BYTES) {
                            LOGGER.warn("Skipping oversized KB file: {} ({}MB > {}MB)", f.getFileName(), fileSize / (1024*1024), MAX_FILE_SIZE_BYTES / (1024*1024));
                            return;
                        }
                    } catch (IOException e) {
                        LOGGER.warn("Failed to check file size: {}", f, e);
                        return;
                    }
                    try (Reader r = Files.newBufferedReader(f)) {
                        List<Entry> list = GSON.fromJson(r, new TypeToken<List<Entry>>() {}.getType());
                        if (list != null) {
                            int added = 0;
                            for (Entry e : list) {
                                if (all.size() >= MAX_ENTRIES) {
                                    LOGGER.warn("KB entry limit reached ({}), stopping", MAX_ENTRIES);
                                    break;
                                }
                                if (e.title() != null && seen.add(e.title().toLowerCase(Locale.ROOT))) {
                                    all.add(e);
                                    added++;
                                }
                            }
                            LOGGER.info("Loaded {} external entries from {}", added, f.getFileName());
                        }
                    } catch (IOException ex) {
                        LOGGER.error("Failed to read {}", f, ex);
                    }
                });
            } catch (IOException e) {
                LOGGER.error("Failed to list external KB directory", e);
            }
        }

        entries = all;
        LOGGER.info("KB loaded: {} entries (external only)", entries.size());
    }

    private void exportExampleIfNeeded(Path externalDir) {
        Path target = externalDir.resolve("_example_mod_wiki.json");
        if (Files.exists(target)) return;
        String example = """
                [
                  {
                    "title": "Example Mod: Bronze Ingot",
                    "keywords": ["bronze", "ingot", "example", "metal"],
                    "summary": "Bronze ingot is an alloy made from copper and tin, used for tools and armor.",
                    "content": "Bronze Ingot\\nObtained by smelting copper and tin together in a furnace.\\nUsed to craft: Bronze Sword, Bronze Pickaxe, Bronze Armor."
                  },
                  {
                    "title": "Example Mod: Bronze Sword",
                    "keywords": ["bronze", "sword", "weapon", "example"],
                    "summary": "A sword made from bronze ingots, stronger than stone but weaker than iron.",
                    "content": "Bronze Sword\\nDamage: 6\\nDurability: 250\\nCrafting: 2 bronze ingots + 1 stick"
                  }
                ]
                """;
        try {
            Files.writeString(target, example.trim(), java.nio.charset.StandardCharsets.UTF_8);
            LOGGER.info("Created example KB file: {}", target);
        } catch (Exception e) {
            LOGGER.warn("Failed to create example KB file", e);
        }
    }

    public boolean isLoaded() { return !entries.isEmpty(); }
    public int size() { return entries.size(); }

    /** 保留传统字符串返回入口，用于 /aikb 等旧命令（现在内部已使用 SearchResult）。 */
    public String searchText(String query, int maxResults) {
        SearchResult result = search(query, maxResults);
        return formatSearchResult(result);
    }

    private List<SearchResult.Item> searchItems(String query, int maxResults) {
        if (!isLoaded()) return List.of();
        String[] tokens = tokenize(query);
        if (tokens.length == 0) return List.of();

        String queryLower = query.toLowerCase(Locale.ROOT).trim();
        return entries.parallelStream()
                .map(e -> new AbstractMap.SimpleEntry<>(e, score(e, tokens, queryLower)))
                .filter(e -> e.getValue() >= 0.2)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(maxResults)
                .map(e -> new SearchResult.Item(e.getKey().title(), e.getKey().summary(), "", e.getValue()))
                .collect(Collectors.toList());
    }

    private String readLocal(String title) {
        if (!isLoaded()) return "[本地] 知识库未加载";
        String t = title.trim().toLowerCase(Locale.ROOT);
        for (Entry e : entries) {
            if (e.title() == null) continue;
            String lower = e.title().toLowerCase(Locale.ROOT);
            if (lower.equals(t) || lower.contains(t) || t.contains(lower)) {
                return "[本地] 【" + e.title() + "】\n" + e.content();
            }
        }
        return "[本地] 未找到条目: " + title;
    }

    private double score(Entry e, String[] tokens, String queryLower) {
        String title = e.titleLower() != null ? e.titleLower() : "";
        String keywords = e.keywordsLower() != null ? e.keywordsLower() : "";
        String summary = e.summaryLower() != null ? e.summaryLower() : "";

        // 精确标题匹配直接返回最高分
        if (title.equals(queryLower)) return 1.0;
        if (title.contains(queryLower)) return 0.9;

        double totalWeight = 0;
        for (String tok : tokens) {
            boolean inTitle = title.contains(tok);
            boolean inKeywords = !keywords.isEmpty() && keywords.contains(tok);
            boolean inSummary = summary.contains(tok);
            if (inTitle) totalWeight += 3.0;
            if (inKeywords) totalWeight += 2.0;
            if (inSummary) totalWeight += 1.0;
        }
        return totalWeight / (tokens.length * 3.0);
    }

    private String[] tokenize(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                if (cjk.length() > 0) { addCjkTokens(cjk.toString(), result); cjk.setLength(0); }
                int j = i;
                while (j < lower.length() && ((lower.charAt(j) >= 'a' && lower.charAt(j) <= 'z') || (lower.charAt(j) >= '0' && lower.charAt(j) <= '9'))) j++;
                result.add(lower.substring(i, j));
                i = j - 1;
            } else if (c >= '\u4e00' && c <= '\u9fff') {
                cjk.append(c);
            } else {
                if (cjk.length() > 0) { addCjkTokens(cjk.toString(), result); cjk.setLength(0); }
            }
        }
        if (cjk.length() > 0) addCjkTokens(cjk.toString(), result);
        return result.toArray(new String[0]);
    }

    private void addCjkTokens(String cjk, List<String> out) {
        if (cjk.length() <= 2) { out.add(cjk); return; }
        out.add(cjk);
        for (int i = 0; i < cjk.length() - 1; i++) {
            out.add(cjk.substring(i, i + 2));
        }
    }

    public static String formatSearchResult(SearchResult result) {
        if (result == null) {
            return "[unknown] 未找到相关信息";
        }
        if (result.isEmpty()) {
            return "[" + result.provider + "] 未找到相关信息";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(result.provider).append("] 找到 ").append(result.items.size()).append(" 条相关条目：\n");
        for (int i = 0; i < result.items.size(); i++) {
            SearchResult.Item item = result.items.get(i);
            sb.append("[").append(i + 1).append("] ").append(item.title);
            if (!item.url.isEmpty()) sb.append(" §7(").append(item.url).append(")");
            sb.append("\n");
            String s = item.summary;
            if (s.length() > 200) s = s.substring(0, 200) + "...";
            sb.append(s).append("\n\n");
        }
        sb.append("如需查看完整内容，使用 read_knowledge_base 工具传入标题。");
        return sb.toString().trim();
    }
}
