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

public class KnowledgeBase {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-KB");
    private static final Gson GSON = new GsonBuilder().create();
    private static final String BUNDLED_PATH = "assets/mcai/kb/zh_wiki.json";
    private static final int MAX_ENTRIES = 50000;
    private static final long MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024; // 50MB per file

    private List<Entry> entries = List.of();

    public record Entry(String title, List<String> keywords, String summary, String content) {}

    public void load(Path externalDir) {
        List<Entry> all = new ArrayList<>();
        var seen = new HashSet<String>();
        int loaded = 0;

        if (externalDir != null) {
            try {
                Files.createDirectories(externalDir);
                exportExampleIfNeeded(externalDir);
            } catch (Exception e) {
                LOGGER.warn("Failed to setup external KB directory", e);
            }
        }

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
                                if (seen.add(e.title().toLowerCase(Locale.ROOT))) {
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
        LOGGER.info("KB loaded: {} entries ({} bundled + external)", entries.size(), loaded);
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

    public String search(String query, int maxResults) {
        if (query.isBlank()) return "查询为空";
        return searchLocal(query, maxResults);
    }

    public String read(String title) {
        if (title.isBlank()) return "标题为空";
        return readLocal(title);
    }

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
        String title = e.title().toLowerCase(Locale.ROOT);
        String keywords = String.join(" ", e.keywords() != null ? e.keywords() : List.of()).toLowerCase(Locale.ROOT);
        String summary = e.summary().toLowerCase(Locale.ROOT);
        double totalWeight = 0;
        for (String t : tokens) {
            boolean inTitle = title.contains(t);
            boolean inKeywords = !keywords.isEmpty() && keywords.contains(t);
            boolean inSummary = summary.contains(t);
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
}
