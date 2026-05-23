package com.monopolyfun.shared.id.infra.postgres;

import com.monopolyfun.shared.id.BusinessIdSequenceRepository;
import com.monopolyfun.shared.id.BusinessIdType;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
public class PostgresBusinessIdSequenceRepository implements BusinessIdSequenceRepository {
    private final DSLContext dsl;

    public PostgresBusinessIdSequenceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public long nextValue(BusinessIdType type, LocalDate bizDate) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // 中文注释：取号与递增在同一条数据库语句内完成，保证并发创建订单时业务编号仍然唯一递增。
        Long value = dsl.resultQuery("""
                                insert into business_id_sequences (id_type, biz_date, next_value, updated_at)
                                values (?, ?, 1, ?::timestamptz)
                                on conflict (id_type, biz_date) do update
                                set next_value = business_id_sequences.next_value + 1,
                                    updated_at = excluded.updated_at
                                returning next_value
                                """,
                        type.name(),
                        bizDate,
                        now)
                .fetchOne(0, Long.class);
        if (value == null) {
            throw new IllegalStateException("Business id sequence did not return a value");
        }
        return value;
    }
}
