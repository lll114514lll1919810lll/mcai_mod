package com.example.mcai.kb;

/**
 * 知识库搜索提供器抽象。
 */
public interface SearchProvider {
    /** 提供器名称，用于日志和返回结果标记 */
    String name();

    /** 当前是否可用（例如在线提供器检查网络开关） */
    boolean isAvailable();

    /**
     * 执行搜索。
     *
     * @param query      查询关键词
     * @param maxResults 最大返回条数
     * @return 搜索结果
     */
    SearchResult search(String query, int maxResults);

    /**
     * 读取某个条目的完整内容。若不支持则返回 null 或空结果。
     *
     * @param title 条目标题
     * @return 内容字符串；未找到时返回提示文本
     */
    default String read(String title) {
        return null;
    }
}
