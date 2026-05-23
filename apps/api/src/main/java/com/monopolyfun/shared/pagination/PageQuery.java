package com.monopolyfun.shared.pagination;

public record PageQuery(
        int limit,
        String cursor
) {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    public static PageQuery of(Integer limit, String cursor) {
        int requested = limit == null ? DEFAULT_LIMIT : limit;
        // 中文注释：所有列表入口统一限制 page size，防止单个接口绕开数据库分页边界。
        int bounded = Math.max(1, Math.min(requested, MAX_LIMIT));
        return new PageQuery(bounded, cursor == null || cursor.isBlank() ? null : cursor.trim());
    }

    public int fetchLimit() {
        return limit + 1;
    }

    public CursorKey cursorKey() {
        return CursorCodec.decode(cursor);
    }
}
