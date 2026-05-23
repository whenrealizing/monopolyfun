package com.monopolyfun.modules.project.infra.postgres;

import com.monopolyfun.modules.project.domain.ProjectSharePoolEntity;
import com.monopolyfun.modules.project.infra.ProjectSharePoolRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public class PostgresProjectSharePoolRepository implements ProjectSharePoolRepository {
    private final DSLContext dsl;

    public PostgresProjectSharePoolRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<ProjectSharePoolEntity> findByProjectId(String projectId) {
        return dsl.resultQuery(selectSql() + " where project_id = ?", projectId)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<ProjectSharePoolEntity> findByMarketId(String marketId) {
        return dsl.resultQuery(selectSql() + " where market_id = ?", marketId)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public ProjectSharePoolEntity save(ProjectSharePoolEntity pool) {
        dsl.query("""
                                insert into project_share_pools (
                                  project_id, market_id, share_total, share_minted, share_reserved,
                                  task_budget, task_minted, task_reserved,
                                  reserve_budget, next_curve_slot, initial_base_reward, decay, min_base_reward,
                                  created_at, updated_at
                                )
                                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz)
                                on conflict (project_id) do update
                                set market_id = excluded.market_id,
                                    share_total = excluded.share_total,
                                    share_minted = excluded.share_minted,
                                    share_reserved = excluded.share_reserved,
                                    task_budget = excluded.task_budget,
                                    task_minted = excluded.task_minted,
                                    task_reserved = excluded.task_reserved,
                                    reserve_budget = excluded.reserve_budget,
                                    next_curve_slot = excluded.next_curve_slot,
                                    initial_base_reward = excluded.initial_base_reward,
                                    decay = excluded.decay,
                                    min_base_reward = excluded.min_base_reward,
                                    updated_at = excluded.updated_at
                                """,
                        pool.projectId(),
                        pool.marketId(),
                        pool.shareTotal(),
                        pool.shareMinted(),
                        pool.shareReserved(),
                        pool.taskBudget(),
                        pool.taskMinted(),
                        pool.taskReserved(),
                        pool.reserveBudget(),
                        pool.nextCurveSlot(),
                        pool.initialBaseReward(),
                        BigDecimal.valueOf(pool.decay()),
                        pool.minBaseReward(),
                        PostgresJson.offsetDateTime(pool.createdAt()),
                        PostgresJson.offsetDateTime(pool.updatedAt()))
                .execute();
        return findByProjectId(pool.projectId()).orElseThrow();
    }

    @Override
    public ProjectSharePoolEntity reserveTask(String projectId, int amount) {
        return dsl.resultQuery("""
                        with updated as (
                          update project_share_pools
                          set share_reserved = share_reserved + ?,
                              task_reserved = task_reserved + ?,
                              updated_at = now()
                          where project_id = ?
                            and ? > 0
                            and task_minted + task_reserved + ? <= task_budget
                          returning *
                        )
                        %s from updated
                        """.formatted(selectColumnsSql()), amount, amount, projectId, amount, amount)
                .fetchOptional(this::mapRecord)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Project task share pool is exhausted"));
    }

    @Override
    public ProjectSharePoolEntity releaseTaskReservationByMarketId(String marketId, int amount) {
        return dsl.resultQuery("""
                        with updated as (
                          update project_share_pools
                          set share_reserved = greatest(0, share_reserved - ?),
                              task_reserved = greatest(0, task_reserved - ?),
                              updated_at = now()
                          where market_id = ?
                          returning *
                        )
                        %s from updated
                        """.formatted(selectColumnsSql()), amount, amount, marketId)
                .fetchOptional(this::mapRecord)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project share pool not found"));
    }

    @Override
    public ProjectSharePoolEntity mintTaskByMarketId(String marketId, int amount, int nextCurveSlot) {
        return dsl.resultQuery("""
                        with updated as (
                          update project_share_pools
                          set share_minted = share_minted + ?,
                              share_reserved = greatest(0, share_reserved - ?),
                              task_minted = task_minted + ?,
                              task_reserved = greatest(0, task_reserved - ?),
                              next_curve_slot = greatest(next_curve_slot, ?),
                              updated_at = now()
                          where market_id = ?
                            and ? > 0
                            and task_minted + ? <= task_budget
                          returning *
                        )
                        %s from updated
                        """.formatted(selectColumnsSql()), amount, amount, amount, amount, nextCurveSlot, marketId, amount, amount)
                .fetchOptional(this::mapRecord)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Project task share pool is exhausted"));
    }

    private String selectSql() {
        return selectColumnsSql() + """
                from project_share_pools
                """;
    }

    private String selectColumnsSql() {
        return """
                select project_id, market_id, share_total, share_minted, share_reserved,
                       task_budget, task_minted, task_reserved,
                       reserve_budget, next_curve_slot, initial_base_reward, decay, min_base_reward,
                       created_at, updated_at
                """;
    }

    private ProjectSharePoolEntity mapRecord(Record record) {
        return new ProjectSharePoolEntity(
                record.get("project_id", String.class),
                record.get("market_id", String.class),
                record.get("share_total", Integer.class),
                record.get("share_minted", Integer.class),
                record.get("share_reserved", Integer.class),
                record.get("task_budget", Integer.class),
                record.get("task_minted", Integer.class),
                record.get("task_reserved", Integer.class),
                record.get("reserve_budget", Integer.class),
                record.get("next_curve_slot", Integer.class),
                record.get("initial_base_reward", Integer.class),
                record.get("decay", BigDecimal.class).doubleValue(),
                record.get("min_base_reward", Integer.class),
                PostgresJson.instant(record.get("created_at", java.time.OffsetDateTime.class)),
                PostgresJson.instant(record.get("updated_at", java.time.OffsetDateTime.class)));
    }
}
