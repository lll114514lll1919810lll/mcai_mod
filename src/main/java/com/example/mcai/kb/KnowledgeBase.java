package com.example.mcai.kb;

import com.example.mcai.api.WikiSearchClient;
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

public class KnowledgeBase {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-KB");
    private static final Gson GSON = new GsonBuilder().create();
    private static final String BUNDLED_PATH = "assets/mcai/kb/zh_wiki.json";

    private List<Entry> entries = List.of();
    private final WikiSearchClient wikiClient = new WikiSearchClient();

    public record Entry(String title, List<String> keywords, String summary, String content) {}

    /** Load from bundled resource first, then merge from external directory */
    public void load(Path externalDir) {
        List<Entry> all = new ArrayList<>();
        var seen = new HashSet<String>();
        int loaded = 0;

        // 1. Load bundled KB from JAR
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(BUNDLED_PATH)) {
            if (is != null) {
                try (Reader r = new InputStreamReader(is)) {
                    List<Entry> list = GSON.fromJson(r, new TypeToken<List<Entry>>() {}.getType());
                    if (list != null) {
                        for (Entry e : list) {
                            if (seen.add(e.title().toLowerCase(Locale.ROOT))) {
                                all.add(e);
                            }
                        }
                        loaded = list.size();
                    }
                }
            } else {
                LOGGER.warn("Bundled KB not found: {}", BUNDLED_PATH);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load bundled KB", e);
        }

        // 2. Merge from external directory (overrides/additions)
        if (externalDir != null && Files.isDirectory(externalDir)) {
            try (var files = Files.list(externalDir)) {
                files.filter(f -> f.toString().endsWith(".json")).forEach(f -> {
                    try (Reader r = Files.newBufferedReader(f)) {
                        List<Entry> list = GSON.fromJson(r, new TypeToken<List<Entry>>() {}.getType());
                        if (list != null) {
                            for (Entry e : list) {
                                if (seen.add(e.title().toLowerCase(Locale.ROOT))) {
                                    all.add(e);
                                }
                            }
                            LOGGER.info("Loaded {} external entries from {}", list.size(), f.getFileName());
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
        LOGGER.info("KB loaded: {} entries ({} bundled + external)", entries.size(), loaded);
    }

    public boolean isLoaded() { return !entries.isEmpty(); }
    public int size() { return entries.size(); }

    /**
     * 搜索知识库。优先使用在线 Wiki API，失败时回退到本地嵌入式知识库。
     */
    public String search(String query, int maxResults) {
        if (query.isBlank()) return "查询为空";

        // 1. 尝试在线搜索
        var onlineResults = wikiClient.search(query, maxResults);
        if (onlineResults != null && !onlineResults.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[在线] 找到 ").append(onlineResults.size()).append(" 条 Wiki 条目：\n");
            for (int i = 0; i < onlineResults.size(); i++) {
                var r = onlineResults.get(i);
                sb.append("[").append(i + 1).append("] ").append(r.title()).append("\n");
                sb.append(r.snippet()).append("\n\n");
            }
            sb.append("如需查看完整内容，使用 read_knowledge_base 工具传入标题。");
            return sb.toString().trim();
        }

        // 2. 回退到本地知识库
        LOGGER.info("Wiki API unavailable, falling back to local KB");
        return searchLocal(query, maxResults);
    }

    /**
     * 读取知识库条目。优先使用在线 Wiki API，失败时回退到本地。
     */
    public String read(String title) {
        if (title.isBlank()) return "标题为空";

        // 1. 尝试在线获取
        String onlineContent = wikiClient.fetchPage(title);
        if (onlineContent != null && !onlineContent.isEmpty()) {
            return "[在线] 【" + title + "】\n" + onlineContent;
        }

        // 2. 回退到本地知识库
        LOGGER.info("Wiki API unavailable for '{}', falling back to local KB", title);
        return readLocal(title);
    }

    // ── 本地知识库（原 search/read，保留作为离线后备） ──

    private String searchLocal(String query, int maxResults) {
        if (!isLoaded()) return "[本地] 知识库未加载";
        String[] tokens = tokenize(query);
        if (tokens.length == 0) return "[本地] 未找到相关信息";

        var scored = entries.parallelStream()
                .map(e -> new AbstractMap.SimpleEntry<>(e, score(e, tokens)))
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(maxResults)
                .collect(Collectors.toList());

        if (scored.isEmpty()) return "[本地] 未找到相关信息";

        StringBuilder sb = new StringBuilder();
        sb.append("[本地] 找到 ").append(scored.size()).append(" 条相关条目：\n");
        for (int i = 0; i < scored.size(); i++) {
            Entry e = scored.get(i).getKey();
            sb.append("[").append(i + 1).append("] ").append(e.title()).append("\n");
            String s = e.summary();
            if (s.length() > 200) s = s.substring(0, 200) + "...";
            sb.append(s).append("\n\n");
        }
        sb.append("如需查看完整内容，使用 read_knowledge_base 工具传入标题。");
        return sb.toString().trim();
    }

    private String readLocal(String title) {
        if (!isLoaded()) return "[本地] 知识库未加载";
        String t = title.trim().toLowerCase(Locale.ROOT);
        for (Entry e : entries) {
            if (e.title().toLowerCase(Locale.ROOT).equals(t)
                    || e.title().toLowerCase(Locale.ROOT).contains(t)
                    || t.contains(e.title().toLowerCase(Locale.ROOT))) {
                return "[本地] 【" + e.title() + "】\n" + e.content();
            }
        }
        return "[本地] 未找到条目: " + title;
    }

    private double score(Entry e, String[] tokens) {
        String text = (e.title() + " " + String.join(" ", e.keywords() != null ? e.keywords() : List.of())
                + " " + e.summary()).toLowerCase(Locale.ROOT);
        int matches = 0;
        for (String t : tokens) {
            if (text.contains(t)) matches++;
        }
        return (double) matches / Math.max(tokens.length, 1);
    }

    private String[] tokenize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\u4e00-\u9fff\\s]", " ")
                .trim()
                .split("\\s+");
    }
}
