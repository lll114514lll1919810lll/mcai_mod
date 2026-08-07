package com.example.mcai.kb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Minecraft Wiki 在线搜索提供器。
 * 通过 MediaWiki API 搜索 minecraft.wiki（英文）或 zh.minecraft.wiki（中文）。
 */
public class WikiSearchProvider implements SearchProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-WikiSearch");
    private static final Gson GSON = new GsonBuilder().create();
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WIKI_MARKUP = Pattern.compile("\\[\\[|\\]\\]|\\{|\\}|\\|");

    private final String language;
    private final HttpClient httpClient;
    private final int connectTimeoutSeconds;
    private final int requestTimeoutSeconds;

    public WikiSearchProvider(String language, int connectTimeoutSeconds, int requestTimeoutSeconds) {
        this.language = language != null ? language : "zh_cn";
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    @Override
    public String name() {
        return "wiki";
    }

    @Override
    public boolean isAvailable() {
        return true; // 网络状态在 search 中通过超时处理
    }

    @Override
    public SearchResult search(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return SearchResult.empty(name(), false);
        }
        try {
            List<SearchResult.Item> found = fullTextSearch(query, maxResults);
            if (found.isEmpty()) {
                LOGGER.debug("Wiki search for '{}' returned 0 results", query);
                return SearchResult.empty(name(), false);
            }
            // 获取前几个条目的摘要， enrich 结果
            List<SearchResult.Item> enriched = new ArrayList<>();
            int fetchCount = Math.min(found.size(), 3);
            for (int i = 0; i < fetchCount; i++) {
                SearchResult.Item item = found.get(i);
                String extract = fetchExtract(item.title);
                String summary = extract.isEmpty() ? item.summary : extract;
                enriched.add(new SearchResult.Item(item.title, summary, item.url, item.score));
            }
            if (found.size() > fetchCount) {
                enriched.addAll(found.subList(fetchCount, found.size()));
            }
            return new SearchResult(name(), enriched, false);
        } catch (Exception e) {
            LOGGER.warn("Wiki search failed: {}", e.getMessage());
            return SearchResult.error(name(), "Wiki 搜索失败: " + e.getMessage());
        }
    }

    @Override
    public String read(String title) {
        if (title == null || title.isBlank()) return "标题为空";
        try {
            String extract = fetchExtract(title);
            if (extract.isEmpty()) return "[Wiki] 未找到条目: " + title;
            return "[Wiki] 【" + title + "】\n" + extract + "\n\n§7完整页面: " + buildPageUrl(title);
        } catch (Exception e) {
            return "[Wiki] 读取失败: " + e.getMessage();
        }
    }

    private List<SearchResult.Item> fullTextSearch(String query, int maxResults) throws IOException, InterruptedException {
        String url = baseApiUrl()
                + "?action=query&list=search&srsearch=" + encode(query)
                + "&srlimit=" + maxResults
                + "&format=json";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "MCAI-Minecraft-Mod/1.5.1 (https://github.com/lll114514lll1919810lll/mcai_mod)")
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        LOGGER.debug("Wiki API response: status={}, body_length={}", response.statusCode(), response.body().length());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        JsonObject queryObj = json.getAsJsonObject("query");
        if (queryObj == null) return List.of();
        JsonArray search = queryObj.getAsJsonArray("search");
        if (search == null) return List.of();

        List<SearchResult.Item> items = new ArrayList<>();
        for (JsonElement el : search) {
            JsonObject obj = el.getAsJsonObject();
            String title = obj.has("title") ? obj.get("title").getAsString() : "";
            String snippet = obj.has("snippet") ? obj.get("snippet").getAsString() : "";
            if (title.isEmpty()) continue;
            snippet = cleanText(stripHtml(snippet));
            String pageUrl = buildPageUrl(title);
            double score = 1.0 - (items.size() * 0.1); // 按排名递减
            items.add(new SearchResult.Item(title, snippet, pageUrl, score));
        }
        return items;
    }

    private String fetchExtract(String title) throws IOException, InterruptedException {
        String url = baseApiUrl()
                + "?action=query&prop=extracts&exintro=true&explaintext=true&titles=" + encode(title)
                + "&format=json";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "MCAI-Minecraft-Mod/1.5.1 (https://github.com/lll114514lll1919810lll/mcai_mod)")
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return "";
        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        JsonObject query = json.getAsJsonObject("query");
        if (query == null) return "";
        JsonObject pages = query.getAsJsonObject("pages");
        if (pages == null) return "";
        for (String key : pages.keySet()) {
            JsonObject page = pages.getAsJsonObject(key);
            if (page.has("extract")) {
                String text = page.get("extract").getAsString();
                return cleanText(text);
            }
        }
        return "";
    }

    private String baseApiUrl() {
        return "zh_cn".equals(language) ? "https://zh.minecraft.wiki/api.php" : "https://minecraft.wiki/api.php";
    }

    private String buildPageUrl(String title) {
        String base = "zh_cn".equals(language) ? "https://zh.minecraft.wiki/w/" : "https://minecraft.wiki/w/";
        return base + encode(title.replace(" ", "_"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripHtml(String text) {
        if (text == null) return "";
        return HTML_TAG.matcher(text).replaceAll("");
    }

    private static String cleanText(String text) {
        if (text == null) return "";
        String s = WIKI_MARKUP.matcher(text).replaceAll("");
        s = s.replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }
}
