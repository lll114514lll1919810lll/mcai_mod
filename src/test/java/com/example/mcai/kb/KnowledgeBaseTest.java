package com.example.mcai.kb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeBaseTest {

    private KnowledgeBase kb;

    @BeforeEach
    void setUp() {
        kb = new KnowledgeBase();
    }

    // ═══════════════════════════════════════════════════════════════
    // Basic state tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void initiallyNotLoaded() {
        assertFalse(kb.isLoaded());
        assertEquals(0, kb.size());
    }

    @Test
    void name_returnsLocal() {
        assertEquals("local", kb.name());
    }

    @Test
    void isAvailable_whenNotLoaded() {
        assertFalse(kb.isAvailable());
    }

    // ═══════════════════════════════════════════════════════════════
    // Search on empty KB
    // ═══════════════════════════════════════════════════════════════

    @Test
    void search_emptyQuery_returnsEmpty() {
        SearchResult result = kb.search("", 10);

        assertTrue(result.isEmpty());
        assertEquals("local", result.provider);
    }

    @Test
    void search_blankQuery_returnsEmpty() {
        SearchResult result = kb.search("   ", 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void search_nullQuery_returnsEmpty() {
        SearchResult result = kb.search(null, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void search_unloadedKb_returnsEmpty() {
        SearchResult result = kb.search("test", 10);

        assertTrue(result.isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════
    // Read on empty KB
    // ═══════════════════════════════════════════════════════════════

    @Test
    void read_emptyTitle_returnsError() {
        String result = kb.read("");

        assertEquals("标题为空", result);
    }

    @Test
    void read_nullTitle_returnsError() {
        String result = kb.read(null);

        assertEquals("标题为空", result);
    }

    @Test
    void read_unloadedKb_returnsError() {
        String result = kb.read("test");

        assertEquals("[本地] 知识库未加载", result);
    }

    // ═══════════════════════════════════════════════════════════════
    // SearchResult formatting tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void formatSearchResult_nullResult() {
        String formatted = KnowledgeBase.formatSearchResult(null);

        assertEquals("[unknown] 未找到相关信息", formatted);
    }

    @Test
    void formatSearchResult_emptyResult() {
        SearchResult result = SearchResult.empty("local", true);
        String formatted = KnowledgeBase.formatSearchResult(result);

        assertEquals("[local] 未找到相关信息", formatted);
    }

    @Test
    void formatSearchResult_withItems() {
        List<SearchResult.Item> items = List.of(
                new SearchResult.Item("Diamond Sword", "A powerful sword", "", 0.9),
                new SearchResult.Item("Diamond Pickaxe", "A mining tool", "", 0.8)
        );
        SearchResult result = new SearchResult("wiki", items, false);
        String formatted = KnowledgeBase.formatSearchResult(result);

        assertTrue(formatted.contains("[wiki] 找到 2 条相关条目"));
        assertTrue(formatted.contains("[1] Diamond Sword"));
        assertTrue(formatted.contains("[2] Diamond Pickaxe"));
        assertTrue(formatted.contains("A powerful sword"));
        assertTrue(formatted.contains("A mining tool"));
    }

    @Test
    void formatSearchResult_withUrl() {
        List<SearchResult.Item> items = List.of(
                new SearchResult.Item("Diamond", "A gem", "https://minecraft.wiki/Diamond", 0.9)
        );
        SearchResult result = new SearchResult("wiki", items, false);
        String formatted = KnowledgeBase.formatSearchResult(result);

        assertTrue(formatted.contains("https://minecraft.wiki/Diamond"));
    }

    @Test
    void formatSearchResult_longSummaryTruncated() {
        String longSummary = "A".repeat(300);
        List<SearchResult.Item> items = List.of(
                new SearchResult.Item("Test", longSummary, "", 0.9)
        );
        SearchResult result = new SearchResult("local", items, true);
        String formatted = KnowledgeBase.formatSearchResult(result);

        assertTrue(formatted.contains("..."));
        assertFalse(formatted.contains(longSummary));
    }

    // ═══════════════════════════════════════════════════════════════
    // SearchResult.Item tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void searchResultItem_constructor() {
        SearchResult.Item item = new SearchResult.Item("Title", "Summary", "https://example.com", 0.85);

        assertEquals("Title", item.title);
        assertEquals("Summary", item.summary);
        assertEquals("https://example.com", item.url);
        assertEquals(0.85, item.score);
    }

    @Test
    void searchResult_empty_factory() {
        SearchResult result = SearchResult.empty("wiki", false);

        assertTrue(result.isEmpty());
        assertEquals("wiki", result.provider);
        assertFalse(result.offline);
        assertNull(result.error);
    }

    @Test
    void searchResult_error_factory() {
        SearchResult result = SearchResult.error("wiki", "Connection timeout");

        assertTrue(result.isEmpty());
        assertEquals("wiki", result.provider);
        assertEquals("Connection timeout", result.error);
    }

    @Test
    void searchResult_constructor() {
        List<SearchResult.Item> items = List.of(
                new SearchResult.Item("Test", "Summary", "", 0.9)
        );
        SearchResult result = new SearchResult("local", items, true);

        assertFalse(result.isEmpty());
        assertEquals("local", result.provider);
        assertTrue(result.offline);
        assertEquals(1, result.items.size());
    }
}
