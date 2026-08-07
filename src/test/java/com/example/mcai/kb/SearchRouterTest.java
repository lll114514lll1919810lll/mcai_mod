package com.example.mcai.kb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SearchRouter 测试（不依赖 Mockito）。
 * 使用简单的 SearchProvider 实现进行测试。
 */
class SearchRouterTest {

    private SearchRouter router;
    private StubSearchProvider stubProvider;

    @BeforeEach
    void setUp() {
        stubProvider = new StubSearchProvider();
        // SearchRouter 需要 ModConfig，但我们只测试不需要 ModConfig 的方法
        // 对于需要 ModConfig 的方法，我们使用 stub provider 直接测试
        router = new SearchRouter(null, stubProvider);
    }

    @AfterEach
    void tearDown() {
        if (router != null) {
            router.shutdown();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Basic tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void name_returnsWiki() {
        assertEquals("wiki", router.name());
    }

    @Test
    void isAvailable_delegatesToProvider() {
        stubProvider.available = true;
        assertTrue(router.isAvailable());

        stubProvider.available = false;
        assertFalse(router.isAvailable());
    }

    // ═══════════════════════════════════════════════════════════════
    // Search tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void search_nullQuery_returnsEmpty() {
        SearchResult result = router.search(null, 10);

        assertTrue(result.isEmpty());
        assertEquals("wiki", result.provider);
    }

    @Test
    void search_blankQuery_returnsEmpty() {
        SearchResult result = router.search("   ", 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void search_successfulResult() {
        List<SearchResult.Item> items = List.of(
                new SearchResult.Item("Diamond", "A gem", "", 0.9)
        );
        stubProvider.searchResult = new SearchResult("wiki", items, false);

        SearchResult result = router.search("diamond", 10);

        assertFalse(result.isEmpty());
        assertEquals(1, result.items.size());
        assertEquals("Diamond", result.items.get(0).title);
    }

    @Test
    void search_emptyResultFromProvider() {
        stubProvider.searchResult = SearchResult.empty("wiki", false);

        SearchResult result = router.search("nonexistent", 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void search_errorFromProvider() {
        stubProvider.searchResult = SearchResult.error("wiki", "HTTP 503");

        SearchResult result = router.search("test", 10);

        assertNotNull(result.error);
        assertEquals("HTTP 503", result.error);
    }

    // ═══════════════════════════════════════════════════════════════
    // Read tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void read_nullTitle_returnsError() {
        String result = router.read(null);

        assertEquals("标题为空", result);
    }

    @Test
    void read_blankTitle_returnsError() {
        String result = router.read("   ");

        assertEquals("标题为空", result);
    }

    @Test
    void read_successfulResult() {
        stubProvider.readResult = "Diamond is a mineral...";

        String result = router.read("Diamond");

        assertEquals("Diamond is a mineral...", result);
    }

    // ═══════════════════════════════════════════════════════════════
    // Shutdown test
    // ═══════════════════════════════════════════════════════════════

    @Test
    void shutdown_doesNotThrow() {
        assertDoesNotThrow(() -> router.shutdown());
    }

    @Test
    void getExecutor_returnsNonNull() {
        assertNotNull(router.getExecutor());
    }

    // ═══════════════════════════════════════════════════════════════
    // Stub implementation
    // ═══════════════════════════════════════════════════════════════

    private static class StubSearchProvider implements SearchProvider {
        boolean available = true;
        SearchResult searchResult = SearchResult.empty("wiki", false);
        String readResult = "未找到条目";

        @Override
        public String name() { return "wiki"; }

        @Override
        public boolean isAvailable() { return available; }

        @Override
        public SearchResult search(String query, int maxResults) { return searchResult; }

        @Override
        public String read(String title) { return readResult; }
    }
}
