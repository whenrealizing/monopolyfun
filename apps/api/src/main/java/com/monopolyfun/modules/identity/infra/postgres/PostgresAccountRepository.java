package com.monopolyfun.modules.identity.infra.postgres;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.risk.domain.RiskAccountStatus;
import com.monopolyfun.modules.risk.domain.RiskLevel;
import com.monopolyfun.shared.pagination.CursorCodec;
import com.monopolyfun.shared.pagination.CursorKey;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.monopolyfun.generated.jooq.Tables.ACCOUNTS;

@Repository
public class PostgresAccountRepository implements AccountRepository {
    // 中文注释：迁移列由运行库提供，动态字段让本地 jOOQ 生成节奏跟上账号风控上线。
    private static final Field<String> ACCOUNT_STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<String> ACCOUNT_RISK_LEVEL = DSL.field(DSL.name("risk_level"), String.class);
    private static final Field<java.time.OffsetDateTime> ACCOUNT_FROZEN_UNTIL = DSL.field(DSL.name("frozen_until"), java.time.OffsetDateTime.class);
    private static final Field<String> ACCOUNT_RISK_REASON = DSL.field(DSL.name("risk_reason"), String.class);
    private static final Field<java.time.OffsetDateTime> ACCOUNT_RISK_UPDATED_AT = DSL.field(DSL.name("risk_updated_at"), java.time.OffsetDateTime.class);

    private final DSLContext dsl;

    public PostgresAccountRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<AccountEntity> findAll() {
        return dsl.select(accountFields())
                .from(ACCOUNTS)
                .orderBy(ACCOUNTS.CREATED_AT.asc())
                .fetch(this::mapRecord);
    }

    @Override
    public PageResult<AccountEntity> findPublic(PageQuery pageQuery) {
        List<AccountEntity> fetched = dsl.select(accountFields())
                .from(ACCOUNTS)
                .where(createdCursor(pageQuery.cursorKey(), true))
                .orderBy(ACCOUNTS.CREATED_AT.asc(), ACCOUNTS.ID.asc())
                .limit(pageQuery.fetchLimit())
                .fetch(this::mapRecord);
        return PageResult.fromFetched(fetched, pageQuery.limit(), account -> CursorCodec.encode(account.createdAt().toString(), account.id()));
    }

    @Override
    public PageResult<AccountEntity> findRiskAccounts(String status, String riskLevel, String q, PageQuery pageQuery) {
        String normalizedStatus = normalize(status);
        String normalizedLevel = normalize(riskLevel);
        String normalizedQuery = normalize(q);
        Field<OffsetDateTime> riskSortAt = DSL.coalesce(ACCOUNT_RISK_UPDATED_AT, ACCOUNTS.CREATED_AT);
        List<AccountEntity> fetched = dsl.select(accountFields())
                .from(ACCOUNTS)
                .where(filterCondition(ACCOUNT_STATUS, normalizedStatus))
                .and(filterCondition(ACCOUNT_RISK_LEVEL, normalizedLevel))
                .and(searchCondition(normalizedQuery))
                .and(riskCursor(riskSortAt, pageQuery.cursorKey()))
                .orderBy(riskSortAt.desc(), ACCOUNTS.ID.desc())
                .limit(pageQuery.fetchLimit())
                .fetch(this::mapRecord);
        return PageResult.fromFetched(fetched, pageQuery.limit(), account -> {
            Instant riskSortValue = account.riskUpdatedAt() == null ? account.createdAt() : account.riskUpdatedAt();
            return CursorCodec.encode(riskSortValue.toString(), account.id());
        });
    }

    @Override
    public List<AccountEntity> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return dsl.select(accountFields())
                .from(ACCOUNTS)
                .where(ACCOUNTS.ID.in(ids))
                .orderBy(ACCOUNTS.CREATED_AT.asc())
                .fetch(this::mapRecord);
    }

    @Override
    public List<AccountEntity> findByHandles(Collection<String> handles) {
        if (handles == null || handles.isEmpty()) {
            return List.of();
        }
        List<String> normalizedHandles = handles.stream()
                .filter(handle -> handle != null && !handle.isBlank())
                .distinct()
                .toList();
        if (normalizedHandles.isEmpty()) {
            return List.of();
        }
        return dsl.select(accountFields())
                .from(ACCOUNTS)
                .where(ACCOUNTS.HANDLE.in(normalizedHandles))
                .orderBy(ACCOUNTS.CREATED_AT.asc())
                .fetch(this::mapRecord);
    }

    @Override
    public Optional<AccountEntity> findById(String id) {
        return dsl.select(accountFields())
                .from(ACCOUNTS)
                .where(ACCOUNTS.ID.eq(id))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<AccountEntity> findByHandle(String handle) {
        return dsl.select(accountFields())
                .from(ACCOUNTS)
                .where(ACCOUNTS.HANDLE.eq(handle))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public AccountEntity save(AccountEntity account) {
        dsl.insertInto(ACCOUNTS)
                .set(ACCOUNTS.ID, account.id())
                .set(ACCOUNTS.HANDLE, account.handle())
                .set(ACCOUNTS.DISPLAY_NAME, account.displayName())
                .set(ACCOUNTS.PASSWORD_HASH, account.passwordHash())
                .set(ACCOUNT_STATUS, account.status().code())
                .set(ACCOUNT_RISK_LEVEL, account.riskLevel().code())
                .set(ACCOUNT_FROZEN_UNTIL, PostgresJson.offsetDateTime(account.frozenUntil()))
                .set(ACCOUNT_RISK_REASON, account.riskReason())
                .set(ACCOUNT_RISK_UPDATED_AT, PostgresJson.offsetDateTime(account.riskUpdatedAt()))
                .set(ACCOUNTS.METADATA, PostgresJson.jsonb(account.metadata()))
                .set(ACCOUNTS.CREATED_AT, PostgresJson.offsetDateTime(account.createdAt()))
                .set(ACCOUNTS.UPDATED_AT, PostgresJson.offsetDateTime(account.updatedAt()))
                .onConflict(ACCOUNTS.ID)
                .doUpdate()
                .set(ACCOUNTS.HANDLE, account.handle())
                .set(ACCOUNTS.DISPLAY_NAME, account.displayName())
                .set(ACCOUNTS.PASSWORD_HASH, account.passwordHash())
                .set(ACCOUNT_STATUS, account.status().code())
                .set(ACCOUNT_RISK_LEVEL, account.riskLevel().code())
                .set(ACCOUNT_FROZEN_UNTIL, PostgresJson.offsetDateTime(account.frozenUntil()))
                .set(ACCOUNT_RISK_REASON, account.riskReason())
                .set(ACCOUNT_RISK_UPDATED_AT, PostgresJson.offsetDateTime(account.riskUpdatedAt()))
                .set(ACCOUNTS.METADATA, PostgresJson.jsonb(account.metadata()))
                .set(ACCOUNTS.UPDATED_AT, PostgresJson.offsetDateTime(account.updatedAt()))
                .execute();
        return account;
    }

    private java.util.List<org.jooq.SelectField<?>> accountFields() {
        return java.util.List.of(
                ACCOUNTS.ID,
                ACCOUNTS.HANDLE,
                ACCOUNTS.DISPLAY_NAME,
                ACCOUNTS.PASSWORD_HASH,
                ACCOUNT_STATUS,
                ACCOUNT_RISK_LEVEL,
                ACCOUNT_FROZEN_UNTIL,
                ACCOUNT_RISK_REASON,
                ACCOUNT_RISK_UPDATED_AT,
                ACCOUNTS.METADATA,
                ACCOUNTS.CREATED_AT,
                ACCOUNTS.UPDATED_AT);
    }

    private Condition filterCondition(Field<String> field, String value) {
        return value == null ? DSL.trueCondition() : field.eq(value);
    }

    private Condition searchCondition(String q) {
        if (q == null) {
            return DSL.trueCondition();
        }
        // 中文注释：风控账号筛选下推数据库，账号量增长时仍保持稳定分页成本。
        return DSL.lower(ACCOUNTS.HANDLE).contains(q)
                .or(DSL.lower(ACCOUNTS.DISPLAY_NAME).contains(q));
    }

    private Condition createdCursor(CursorKey cursor, boolean asc) {
        if (cursor == null) {
            return DSL.trueCondition();
        }
        OffsetDateTime value = OffsetDateTime.ofInstant(CursorCodec.instantValue(cursor), ZoneOffset.UTC);
        return asc
                ? ACCOUNTS.CREATED_AT.gt(value).or(ACCOUNTS.CREATED_AT.eq(value).and(ACCOUNTS.ID.gt(cursor.id())))
                : ACCOUNTS.CREATED_AT.lt(value).or(ACCOUNTS.CREATED_AT.eq(value).and(ACCOUNTS.ID.lt(cursor.id())));
    }

    private Condition riskCursor(Field<OffsetDateTime> riskSortAt, CursorKey cursor) {
        if (cursor == null) {
            return DSL.trueCondition();
        }
        OffsetDateTime value = OffsetDateTime.ofInstant(CursorCodec.instantValue(cursor), ZoneOffset.UTC);
        return riskSortAt.lt(value).or(riskSortAt.eq(value).and(ACCOUNTS.ID.lt(cursor.id())));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private AccountEntity mapRecord(Record record) {
        return new AccountEntity(
                record.get(ACCOUNTS.ID),
                record.get(ACCOUNTS.HANDLE),
                record.get(ACCOUNTS.DISPLAY_NAME),
                record.get(ACCOUNTS.PASSWORD_HASH),
                RiskAccountStatus.fromCode(record.get(ACCOUNT_STATUS)),
                RiskLevel.fromCode(record.get(ACCOUNT_RISK_LEVEL)),
                PostgresJson.instant(record.get(ACCOUNT_FROZEN_UNTIL)),
                record.get(ACCOUNT_RISK_REASON),
                PostgresJson.instant(record.get(ACCOUNT_RISK_UPDATED_AT)),
                PostgresJson.map(record.get(ACCOUNTS.METADATA)),
                PostgresJson.instant(record.get(ACCOUNTS.CREATED_AT)),
                PostgresJson.instant(record.get(ACCOUNTS.UPDATED_AT)));
    }
}
