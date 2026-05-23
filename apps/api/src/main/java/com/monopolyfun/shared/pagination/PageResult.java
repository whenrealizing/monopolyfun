package com.monopolyfun.shared.pagination;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(
        List<T> items,
        PageInfo pageInfo
) {
    public static <T> PageResult<T> fromFetched(List<T> fetched, int limit, Function<T, String> cursorFactory) {
        boolean hasMore = fetched.size() > limit;
        List<T> items = hasMore ? fetched.subList(0, limit) : fetched;
        String nextCursor = hasMore && !items.isEmpty() ? cursorFactory.apply(items.getLast()) : null;
        return new PageResult<>(List.copyOf(items), new PageInfo(limit, nextCursor, hasMore));
    }
}
