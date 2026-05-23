package com.monopolyfun.modules.project.infra.postgres;

import com.monopolyfun.generated.jooq.tables.records.ProjectsRecord;
import com.monopolyfun.modules.post.domain.InventoryPolicy;
import com.monopolyfun.modules.post.infra.MarketItemReadModelRepository;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.domain.ProjectLevel;
import com.monopolyfun.modules.project.domain.ProjectStatus;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.projection.ProjectTimelineProjectionWriter;
import com.monopolyfun.shared.pagination.CursorCodec;
import com.monopolyfun.shared.pagination.CursorKey;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectFieldOrAsterisk;
import org.jooq.SortField;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.monopolyfun.generated.jooq.Tables.PROJECTS;
import static com.monopolyfun.generated.jooq.Tables.PROJECT_ROLES;

@Repository
public class PostgresProjectRepository implements ProjectRepository {
    private final DSLContext dsl;
    private final MarketItemReadModelRepository marketItemReadModelRepository;
    private final ProjectTimelineProjectionWriter projectTimelineProjectionWriter;

    public PostgresProjectRepository(
            DSLContext dsl,
            MarketItemReadModelRepository marketItemReadModelRepository,
            ProjectTimelineProjectionWriter projectTimelineProjectionWriter) {
        this.dsl = dsl;
        this.marketItemReadModelRepository = marketItemReadModelRepository;
        this.projectTimelineProjectionWriter = projectTimelineProjectionWriter;
    }

    @Override
    public List<ProjectEntity> findAll() {
        return dsl.select(projectFields())
                .from(PROJECTS)
                .orderBy(PROJECTS.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    @Override
    public PageResult<ProjectEntity> findPublicChildren(String status, String q, String sort, PageQuery pageQuery) {
        List<ProjectEntity> fetched = dsl.select(projectFields())
                .from(PROJECTS)
                .where(PROJECTS.PROJECT_LEVEL.eq(ProjectLevel.CHILD.code()))
                .and(publicCondition())
                .and(statusCondition(status))
                .and(searchCondition(q))
                .and(cursorCondition(sort, pageQuery.cursorKey()))
                .orderBy(sortField(sort), idSortField(sort))
                .limit(pageQuery.fetchLimit())
                .fetch(this::mapRecord);
        return PageResult.fromFetched(fetched, pageQuery.limit(), project -> cursorFor(project, sort));
    }

    @Override
    public List<ProjectEntity> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return dsl.select(projectFields())
                .from(PROJECTS)
                .where(PROJECTS.ID.in(ids))
                .orderBy(PROJECTS.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    @Override
    public List<ProjectEntity> findPublicByOwnerAccountId(String ownerAccountId, int limit) {
        // 中文注释：公开主页按 owner 聚合公开项目，项目自己的私有治理状态继续留在工作台和详情读面。
        return dsl.select(projectFields())
                .from(PROJECTS)
                .where(PROJECTS.OWNER_ACCOUNT_ID.eq(ownerAccountId))
                .and(publicCondition())
                .orderBy(PROJECTS.CREATED_AT.desc(), PROJECTS.ID.desc())
                .limit(Math.max(1, limit))
                .fetch(this::mapRecord);
    }

    @Override
    public List<ProjectEntity> findByOwnerAccountId(String ownerAccountId, int limit) {
        return dsl.select(projectFields())
                .from(PROJECTS)
                .where(PROJECTS.OWNER_ACCOUNT_ID.eq(ownerAccountId))
                .orderBy(PROJECTS.CREATED_AT.desc())
                .limit(Math.max(1, limit))
                .fetch(this::mapRecord);
    }

    @Override
    public List<ProjectEntity> findWorkbenchCandidates(String accountId, int limit) {
        return dsl.selectDistinct(projectFields())
                .from(PROJECTS)
                .join(PROJECT_ROLES).on(PROJECT_ROLES.PROJECT_ID.eq(PROJECTS.ID))
                .where(PROJECT_ROLES.ACCOUNT_ID.eq(accountId))
                .and(PROJECTS.STATUS.eq(ProjectStatus.ACTIVE.name().toLowerCase()))
                .and(PROJECTS.STOCK_SOLD.eq(0))
                .orderBy(PROJECTS.UPDATED_AT.desc())
                .limit(Math.max(1, limit))
                .fetch(this::mapRecord);
    }

    @Override
    public List<ProjectEntity> findOwnerHandoffCandidates(Instant inactiveBefore) {
        OffsetDateTime cutoff = OffsetDateTime.ofInstant(inactiveBefore, ZoneOffset.UTC);
        // 中文注释：定时接力先在数据库筛出真正过期的 child project，再交给服务层做最终业务判定。
        return dsl.select(projectFields())
                .from(PROJECTS)
                .where(PROJECTS.PROJECT_LEVEL.eq(ProjectLevel.CHILD.code()))
                .and(PROJECTS.STATUS.ne(ProjectStatus.ARCHIVED.name().toLowerCase()))
                .and(ownerActivityAt().le(cutoff))
                .orderBy(ownerActivityAt().asc(), PROJECTS.ID.asc())
                .fetch(this::mapRecord);
    }

    @Override
    public Optional<ProjectEntity> findById(String id) {
        return dsl.select(projectFields())
                .from(PROJECTS)
                .where(PROJECTS.ID.eq(id))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<ProjectEntity> findByProjectNo(String projectNo) {
        // 中文注释：公开路由入口依赖 project_no，查询固定绑定物理列防止代码生成差异影响运行。
        return dsl.select(projectFields())
                .from(PROJECTS)
                .where("project_no = ?", projectNo)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<ProjectEntity> findRootProject() {
        return dsl.select(projectFields())
                .from(PROJECTS)
                .where(PROJECTS.PROJECT_LEVEL.eq("root"))
                .orderBy(PROJECTS.CREATED_AT.asc())
                .limit(1)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public ProjectEntity save(ProjectEntity project) {
        dsl.insertInto(PROJECTS)
                .set(PROJECTS.ID, project.id())
                .set(PROJECTS.PROJECT_NO, project.projectNo())
                .set(PROJECTS.OWNER_ACCOUNT_ID, project.ownerAccountId())
                .set(PROJECTS.PROJECT_LEVEL, project.projectLevel().code())
                .set(PROJECTS.PARENT_PROJECT_ID, project.parentProjectId())
                .set(PROJECTS.TITLE, project.title())
                .set(PROJECTS.SUMMARY, project.summary())
                .set(PROJECTS.ONE_SENTENCE, project.oneSentence())
                .set(PROJECTS.INVENTORY_POLICY, project.inventoryPolicy().name().toLowerCase())
                .set(PROJECTS.STOCK_TOTAL, project.stockTotal())
                .set(PROJECTS.STOCK_SOLD, project.stockSold())
                .set(PROJECTS.STATUS, project.status().name().toLowerCase())
                .set(PROJECTS.METADATA, PostgresJson.jsonb(project.metadata()))
                .set(PROJECTS.CREATED_AT, PostgresJson.offsetDateTime(project.createdAt()))
                .set(PROJECTS.UPDATED_AT, PostgresJson.offsetDateTime(project.updatedAt()))
                .onConflict(PROJECTS.ID)
                .doUpdate()
                // owner 是项目推进权的唯一来源，更新项目时必须同步接力后的 owner。
                .set(PROJECTS.OWNER_ACCOUNT_ID, project.ownerAccountId())
                .set(PROJECTS.PROJECT_LEVEL, project.projectLevel().code())
                .set(PROJECTS.PARENT_PROJECT_ID, project.parentProjectId())
                .set(PROJECTS.TITLE, project.title())
                .set(PROJECTS.SUMMARY, project.summary())
                .set(PROJECTS.ONE_SENTENCE, project.oneSentence())
                .set(PROJECTS.INVENTORY_POLICY, project.inventoryPolicy().name().toLowerCase())
                .set(PROJECTS.STOCK_TOTAL, project.stockTotal())
                .set(PROJECTS.STOCK_SOLD, project.stockSold())
                .set(PROJECTS.STATUS, project.status().name().toLowerCase())
                .set(PROJECTS.METADATA, PostgresJson.jsonb(project.metadata()))
                .set(PROJECTS.UPDATED_AT, PostgresJson.offsetDateTime(project.updatedAt()))
                .execute();
        marketItemReadModelRepository.upsertProject(project);
        projectTimelineProjectionWriter.syncProject(project);
        return project;
    }

    private List<? extends SelectFieldOrAsterisk> projectFields() {
        // 固定读取业务实体使用的列，避免本机 codegen schema 带入运行库尚未提供的扩展列。
        return List.<TableField<ProjectsRecord, ?>>of(
                PROJECTS.ID,
                PROJECTS.PROJECT_NO,
                PROJECTS.OWNER_ACCOUNT_ID,
                PROJECTS.PROJECT_LEVEL,
                PROJECTS.PARENT_PROJECT_ID,
                PROJECTS.TITLE,
                PROJECTS.SUMMARY,
                PROJECTS.ONE_SENTENCE,
                PROJECTS.INVENTORY_POLICY,
                PROJECTS.STOCK_TOTAL,
                PROJECTS.STOCK_SOLD,
                PROJECTS.STATUS,
                PROJECTS.METADATA,
                PROJECTS.CREATED_AT,
                PROJECTS.UPDATED_AT);
    }

    private Condition publicCondition() {
        // 中文注释：Project 列表排除 Root Project 和参与方私有条目，Root Project 由固定公开入口读取。
        return DSL.coalesce(DSL.field("projects.metadata ->> 'visibility'", String.class), "market_public").eq("market_public");
    }

    private Condition statusCondition(String status) {
        String normalized = normalize(status);
        return normalized == null ? DSL.trueCondition() : PROJECTS.STATUS.eq(normalized);
    }

    private Condition searchCondition(String q) {
        String normalized = normalize(q);
        if (normalized == null) {
            return DSL.trueCondition();
        }
        return DSL.condition("""
                to_tsvector(
                  'simple',
                  coalesce(project_no, '') || ' ' ||
                  coalesce(title, '') || ' ' ||
                  coalesce(summary, '') || ' ' ||
                  coalesce(one_sentence, '') || ' ' ||
                  coalesce(metadata->>'description', '') || ' ' ||
                  coalesce(metadata->>'goal', '') || ' ' ||
                  coalesce(metadata->>'deliverables', '')
                ) @@ plainto_tsquery('simple', ?)
                """, normalized);
    }

    private org.jooq.Field<OffsetDateTime> ownerActivityAt() {
        // 中文注释：历史 metadata 可能缺少 ownerLastActionAt，回退到项目更新时间保持旧接力语义。
        return DSL.field("""
                coalesce(
                  case
                    when jsonb_exists(projects.metadata, 'ownerLastActionAt')
                      and projects.metadata ->> 'ownerLastActionAt' ~ '^\\d{4}-\\d{2}-\\d{2}T'
                    then (projects.metadata ->> 'ownerLastActionAt')::timestamptz
                    else null
                  end,
                  projects.updated_at,
                  projects.created_at
                )
                """, OffsetDateTime.class);
    }

    private SortField<?> sortField(String sort) {
        return switch (normalize(sort) == null ? "recent" : normalize(sort)) {
            case "oldest" -> PROJECTS.CREATED_AT.asc();
            case "title" -> PROJECTS.TITLE.asc();
            default -> PROJECTS.CREATED_AT.desc();
        };
    }

    private SortField<?> idSortField(String sort) {
        return "oldest".equals(normalize(sort)) || "title".equals(normalize(sort)) ? PROJECTS.ID.asc() : PROJECTS.ID.desc();
    }

    private Condition cursorCondition(String sort, CursorKey cursor) {
        if (cursor == null) {
            return DSL.trueCondition();
        }
        String normalized = normalize(sort) == null ? "recent" : normalize(sort);
        // 中文注释：Project 列表与 offer/request 共用 cursor 规则，市场页三类条目保持同一翻页语义。
        return switch (normalized) {
            case "oldest" -> createdAtCursor(cursor, true);
            case "title" ->
                    PROJECTS.TITLE.gt(cursor.value()).or(PROJECTS.TITLE.eq(cursor.value()).and(PROJECTS.ID.gt(cursor.id())));
            default -> createdAtCursor(cursor, false);
        };
    }

    private Condition createdAtCursor(CursorKey cursor, boolean asc) {
        OffsetDateTime value = OffsetDateTime.ofInstant(CursorCodec.instantValue(cursor), ZoneOffset.UTC);
        return asc
                ? PROJECTS.CREATED_AT.gt(value).or(PROJECTS.CREATED_AT.eq(value).and(PROJECTS.ID.gt(cursor.id())))
                : PROJECTS.CREATED_AT.lt(value).or(PROJECTS.CREATED_AT.eq(value).and(PROJECTS.ID.lt(cursor.id())));
    }

    private String cursorFor(ProjectEntity project, String sort) {
        return "title".equals(normalize(sort))
                ? CursorCodec.encode(project.title(), project.id())
                : CursorCodec.encode(project.createdAt().toString(), project.id());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private ProjectEntity mapRecord(Record record) {
        return new ProjectEntity(
                record.get(PROJECTS.ID),
                record.get(PROJECTS.PROJECT_NO),
                record.get(PROJECTS.OWNER_ACCOUNT_ID),
                ProjectLevel.fromCode(record.get(PROJECTS.PROJECT_LEVEL)),
                record.get(PROJECTS.PARENT_PROJECT_ID),
                record.get(PROJECTS.TITLE),
                record.get(PROJECTS.SUMMARY),
                record.get(PROJECTS.ONE_SENTENCE),
                PostgresJson.modelEnum(InventoryPolicy.class, record.get(PROJECTS.INVENTORY_POLICY)),
                record.get(PROJECTS.STOCK_TOTAL),
                record.get(PROJECTS.STOCK_SOLD),
                PostgresJson.modelEnum(ProjectStatus.class, record.get(PROJECTS.STATUS)),
                PostgresJson.map(record.get(PROJECTS.METADATA)),
                PostgresJson.instant(record.get(PROJECTS.CREATED_AT)),
                PostgresJson.instant(record.get(PROJECTS.UPDATED_AT)));
    }
}
