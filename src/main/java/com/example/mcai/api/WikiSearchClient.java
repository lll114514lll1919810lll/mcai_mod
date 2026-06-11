package com.example.mcai.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 通过 Minecraft Wiki 的 MediaWiki API 在线搜索。
 * 作为主搜索方式，比本地嵌入式知识库更全面、更新。
 */
public class WikiSearchClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-WikiSearch");
    private static final Gson GSON = new GsonBuilder().create();

    private static final String API_URL = "https://zh.minecraft.wiki/api.php";
    private final HttpClient httpClient;

    public WikiSearchClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public record SearchResult(String title, String snippet, String pageId) {}

    /**
     * 在线搜索 Wiki，返回匹配条目的标题和摘要片段。
     * 失败时返回 null，调用方应回退到本地知识库。
     */
    public List<SearchResult> search(String query, int limit) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = API_URL + "?action=query&list=search&srsearch=" + encoded
                    + "&srlimit=" + limit + "&format=json&srprop=snippet";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "MCAI/2.0")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                LOGGER.warn("Wiki API HTTP {}", resp.statusCode());
                return null;
            }

            JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
            JsonArray results = json.getAsJsonObject("query").getAsJsonArray("search");
            if (results == null || results.isEmpty()) return List.of();

            List<SearchResult> list = new ArrayList<>();
            for (JsonElement el : results) {
                JsonObject r = el.getAsJsonObject();
                String snippet = r.get("snippet").getAsString()
                        .replaceAll("<[^>]+>", "")  // 去掉 HTML 标签
                        .replaceAll("\\s+", " ")
                        .trim();
                list.add(new SearchResult(
                        r.get("title").getAsString(),
                        snippet,
                        r.get("pageid").getAsString()
                ));
            }
            return list;
        } catch (Exception e) {
            LOGGER.warn("Wiki API search failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 在线获取 Wiki 页面的完整文本内容。
     * 失败时返回 null。
     */
    public String fetchPage(String title) {
        try {
            String encoded = URLEncoder.encode(title, StandardCharsets.UTF_8);
            String url = API_URL + "?action=query&titles=" + encoded
                    + "&prop=extracts&explaintext&exsectionformat=plain&format=json";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "MCAI/2.0")
                    .timeout(Duration.ofSeconds(12))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                LOGGER.warn("Wiki API HTTP {} for page '{}'", resp.statusCode(), title);
                return null;
            }

            JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
            JsonObject pages = json.getAsJsonObject("query").getAsJsonObject("pages");
            for (String key : pages.keySet()) {
                if (key.equals("-1")) continue;
                JsonObject page = pages.getAsJsonObject(key);
                if (page.has("extract")) {
                    String text = page.get("extract").getAsString();
                    // 截断过长内容
                    if (text.length() > 12000) {
                        text = text.substring(0, 12000) + "\n\n... (内容过长，已截断)";
                    }
                    return text;
                }
            }
            return null;
        } catch (Exception e) {
            LOGGER.warn("Wiki API fetchPage '{}' failed: {}", title, e.getMessage());
            return null;
        }
    }
}
