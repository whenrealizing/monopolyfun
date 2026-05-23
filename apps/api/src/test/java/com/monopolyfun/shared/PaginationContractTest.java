package com.monopolyfun;

import com.monopolyfun.shared.pagination.CursorCodec;
import com.monopolyfun.shared.pagination.CursorKey;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationContractTest {
    @Test
    void clampsLimitAndNormalizesCursor() {
        String cursor = CursorCodec.encode("2026-05-06T00:00:00Z", "row-1");

        PageQuery query = PageQuery.of(999, "  " + cursor + "  ");

        // 中文注释：入口层只依赖 PageQuery，所有列表共享同一 page size 上限。
        assertThat(query.limit()).isEqualTo(100);
        assertThat(query.fetchLimit()).isEqualTo(101);
        assertThat(query.cursor()).isEqualTo(cursor);
    }

    @Test
    void decodesCursorKey() {
        String cursor = CursorCodec.encode("title-a", "row-1");

        CursorKey key = CursorCodec.decode(cursor);

        // 中文注释：cursor 同时携带排序值和 id，解决同一排序值下的稳定翻页。
        assertThat(key.value()).isEqualTo("title-a");
        assertThat(key.id()).isEqualTo("row-1");
    }

    @Test
    void buildsPageResultFromFetchLimit() {
        List<String> fetched = List.of("a", "b", "c");

        PageResult<String> page = PageResult.fromFetched(fetched, 2, item -> "cursor-" + item);

        // 中文注释：仓储层多取一条只用于判断下一页，响应 items 保持请求 limit。
        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.pageInfo().limit()).isEqualTo(2);
        assertThat(page.pageInfo().nextCursor()).isEqualTo("cursor-b");
        assertThat(page.pageInfo().hasMore()).isTrue();
    }
}
