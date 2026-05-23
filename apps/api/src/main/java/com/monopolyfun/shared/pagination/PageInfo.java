package com.monopolyfun.shared.pagination;

public record PageInfo(
        int limit,
        String nextCursor,
        boolean hasMore
) {
}
