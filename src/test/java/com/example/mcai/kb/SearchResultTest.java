package com.example.mcai.kb;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchResultTest {

    // ═══════════════════════════════════════════════════════════════
    // Constructor tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void constructor_withItems() {
        List<SearchResult.Item> items = List.of(
                new SearchResult.Item("Diamond", "A gem", "", 0.9)
        );
        SearchResult result = new SearchResult("wiki", items, false);

        assertEquals("wiki", result.provider);
        assertEquals(1, result.items.size());
        assertFalse(result.offline);
        assertNull(result.error);
    }

    @Test
    void constructor_withError() {
        SearchResult result = new SearchResult("wiki", List.of(), false, "HTTP 503");

        assertEquals("wiki", result.provider);
        assertTrue(result.items.isEmpty());
        assertFalse(result.offline);
        assertEquals("HTTP 503", result.error);
    }

    @Test
    void constructor_nullItems_returnsEmptyList() {
        SearchResult result = new SearchResult("wiki", null, false);

        assertNotNull(result.items);
        assertTrue(result.items.isEmpty());
    }

    @Test
    void constructor_itemsAreImmutable() {
        List<SearchResult.Item> items = new ArrayList<>(List.of(
                new SearchResult.Item("Diamond", "A gem", "", 0.9)
        ));
        SearchResult result = new SearchResult("wiki", items, false);

        // 修改原始列表不应影响 result
        items.add(new SearchResult.Item("Iron", "A metal", "", 0.8));
        assertEquals(1, result.items.size());
    }

    // ═══════════════════════════════════════════════════════════════
    // isEmpty tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void isEmpty_emptyList_returnsTrue() {
        SearchResult result = new SearchResult("wiki", List.of(), false);

        assertTrue(result.isEmpty());
    }

    @Test
    void isEmpty_withItems_returnsFalse() {
        List<SearchResult.Item> items = List.of(
                new SearchResult.Item("Diamond", "A gem", "", 0.9)
        );
        SearchResult result = new SearchResult("wiki", items, false);

        assertFalse(result.isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════
    // Factory method tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void empty_factory() {
        SearchResult result = SearchResult.empty("wiki", false);

        assertTrue(result.isEmpty());
        assertEquals("wiki", result.provider);
        assertFalse(result.offline);
        assertNull(result.error);
    }

    @Test
    void empty_factory_offline() {
        SearchResult result = SearchResult.empty("local", true);

        assertTrue(result.isEmpty());
        assertEquals("local", result.provider);
        assertTrue(result.offline);
    }

    @Test
    void error_factory() {
        SearchResult result = SearchResult.error("wiki", "Connection timeout");

        assertTrue(result.isEmpty());
        assertEquals("wiki", result.provider);
        assertEquals("Connection timeout", result.error);
    }

    @Test
    void error_factory_nullError() {
        SearchResult result = SearchResult.error("wiki", null);

        assertTrue(result.isEmpty());
        assertNull(result.error);
    }

    // ═══════════════════════════════════════════════════════════════
    // Item tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void item_constructor() {
        SearchResult.Item item = new SearchResult.Item("Title", "Summary", "https://example.com", 0.85);

        assertEquals("Title", item.title);
        assertEquals("Summary", item.summary);
        assertEquals("https://example.com", item.url);
        assertEquals(0.85, item.score);
    }

    @Test
    void item_nullFields_defaultToEmpty() {
        SearchResult.Item item = new SearchResult.Item(null, null, null, 0.0);

        assertEquals("", item.title);
        assertEquals("", item.summary);
        assertEquals("", item.url);
    }

    @Test
    void item_emptyFields() {
        SearchResult.Item item = new SearchResult.Item("", "", "", 0.0);

        assertEquals("", item.title);
        assertEquals("", item.summary);
        assertEquals("", item.url);
    }
}
