package com.monopolyfun;

import com.monopolyfun.modules.project.domain.ProjectSharePoolEntity;
import com.monopolyfun.modules.project.infra.ProjectSharePoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ProjectSharePoolConcurrencyTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProjectSharePoolRepository projectSharePoolRepository;

    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("truncate table project_share_pools, projects, markets, accounts cascade");
        jdbcTemplate.update("""
                insert into accounts (id, handle, display_name, metadata)
                values ('acct-founder', '@founder', 'Founder', '{}'::jsonb)
                """);
        jdbcTemplate.update("""
                insert into projects (
                  id, project_no, owner_account_id, project_level, parent_project_id, title, summary, one_sentence,
                  inventory_policy, stock_total, stock_sold, status, metadata, created_at, updated_at
                ) values (
                  'project-root-concurrent', 'monopolyfun', 'acct-founder', 'root', null,
                  'Root project', 'Root project', 'Root project',
                  'unlimited', null, 0, 'active', '{}'::jsonb, now(), now()
                ), (
                  'project-concurrent', 'MF260512PROJ000001X', 'acct-founder', 'child', 'project-root-concurrent',
                  'Concurrent project', 'Concurrent project', 'Concurrent project',
                  'unlimited', null, 0, 'active', '{}'::jsonb, now(), now()
                )
                """);
        jdbcTemplate.update("""
                insert into markets (
                  id, name, summary, listing_goal, lead_account_id, source_ref, surface_url,
                  settlement_type, next_curve_slot, status, lead_last_active_at, lead_seat_status, metadata, created_at, updated_at
                ) values (
                  'mkt-concurrent', 'Concurrent project', 'Concurrent project', 'Concurrent project',
                  'acct-founder', 'project://project-concurrent', 'http://localhost:3000/market/projects/MF260512PROJ000001X',
                  'shares'::settlement_type, 0, 'active'::market_status, now(), 'occupied',
                  '{}'::jsonb, now(), now()
                )
                """);
        jdbcTemplate.update("""
                insert into project_share_pools (
                  project_id, market_id, share_total, share_minted, share_reserved,
                  task_budget, task_minted, task_reserved,
                  reserve_budget, next_curve_slot, initial_base_reward, decay, min_base_reward,
                  created_at, updated_at
                ) values (
                  'project-concurrent', 'mkt-concurrent', 1000, 0, 0,
                  100, 0, 0,
                  800, 0, 70, 0.99, 10,
                  now(), now()
                )
                """);
    }

    @Test
    void concurrentTaskReserveCannotOverspendTaskBudget() throws Exception {
        List<Boolean> results = runConcurrently(() -> reserveTask(70), () -> reserveTask(70));

        ProjectSharePoolEntity pool = projectSharePoolRepository.findByProjectId("project-concurrent").orElseThrow();
        // 中文注释：两个大额 claim 同时进入时，数据库原子条件保证只有一个成功占用任务预算。
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(pool.taskReserved()).isEqualTo(70);
        assertThat(pool.shareReserved()).isEqualTo(70);
    }

    private boolean reserveTask(int amount) {
        try {
            projectSharePoolRepository.reserveTask("project-concurrent", amount);
            return true;
        } catch (ResponseStatusException exception) {
            return false;
        }
    }

    @SafeVarargs
    private final List<Boolean> runConcurrently(Callable<Boolean>... tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(tasks.length);
        try {
            List<Callable<Boolean>> wrapped = new ArrayList<>();
            for (Callable<Boolean> task : tasks) {
                wrapped.add(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                });
            }
            var futures = wrapped.stream().map(executor::submit).toList();
            ready.await();
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (var future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
