package com.monopolyfun.modules.project.service;

import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.domain.ProjectSharePoolEntity;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.infra.ProjectSharePoolRepository;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.TaskView;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolService;
import com.monopolyfun.modules.project.service.view.ProjectCommercializationView;
import com.monopolyfun.modules.settlement.domain.SettlementEventEntity;
import com.monopolyfun.modules.settlement.infra.SettlementEventRepository;
import com.monopolyfun.modules.share.service.view.ProjectSharesView;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ProjectCommercializationService {
    private static final String DEFAULT_CURRENCY = "USD";

    private final ProjectRepository projectRepository;
    private final ProjectValidationProtocolService validationProtocolService;
    private final ProjectSharePoolRepository projectSharePoolRepository;
    private final SettlementEventRepository settlementEventRepository;
    private final DSLContext dsl;

    public ProjectCommercializationService(
            ProjectRepository projectRepository,
            ProjectValidationProtocolService validationProtocolService,
            ProjectSharePoolRepository projectSharePoolRepository,
            SettlementEventRepository settlementEventRepository,
            DSLContext dsl) {
        this.projectRepository = projectRepository;
        this.validationProtocolService = validationProtocolService;
        this.projectSharePoolRepository = projectSharePoolRepository;
        this.settlementEventRepository = settlementEventRepository;
        this.dsl = dsl;
    }

    public ProjectCommercializationView getCommercialization(String projectNo) {
        ProjectEntity project = requireProject(projectNo);
        List<TaskView> tasks = validationProtocolService.listProjectTasks(project.projectNo());
        ProjectSharesView shares = sharePool(project.id());
        ProjectCommercializationView.RevenuePoolView revenue = revenuePool(settlementEventRepository.findByProjectId(project.id()));
        List<ProjectCommercializationView.ContributionLedgerEntryView> contributionLedger = contributionLedger(project.id());
        List<ProjectCommercializationView.ContributionMemberView> contributors = contributionMembers(contributionLedger);
        List<ProjectCommercializationView.DirectionCardView> directions = directions(project, tasks);
        List<ProjectCommercializationView.DirectionCardView> validated = directions.stream()
                .filter(direction -> "validated".equals(direction.status()))
                .toList();
        // 中文注释：商业化页输出协议事实与统一贡献账本，用户侧治理视图使用同一份来源数据。
        return new ProjectCommercializationView(
                project.projectNo(),
                project.id(),
                directions,
                directions.isEmpty() ? null : directions.get(0),
                validated,
                proofStats(tasks),
                shares,
                revenue,
                currentDistribution(revenue, tasks, contributionLedger),
                contributionLedger,
                contributors);
    }

    private ProjectEntity requireProject(String projectNo) {
        if (projectNo == null || projectNo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "project business number is required");
        }
        return projectRepository.findByProjectNo(projectNo.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private ProjectSharesView sharePool(String projectId) {
        // 中文注释：旧测试数据和轻量项目可能先产生贡献账本，份额池缺口由治理页作为空池显示。
        return projectSharePoolRepository.findByProjectId(projectId)
                .map(this::sharesView)
                .orElse(null);
    }

    private ProjectSharesView sharesView(ProjectSharePoolEntity pool) {
        return new ProjectSharesView(
                pool.marketId(),
                pool.shareTotal(),
                pool.shareMinted(),
                pool.shareReserved(),
                pool.shareRemaining(),
                pool.taskBudget(),
                pool.taskMinted(),
                pool.taskReserved(),
                pool.taskRemaining(),
                pool.reserveBudget(),
                pool.nextCurveSlot(),
                pool.currentBaseReward());
    }

    private List<ProjectCommercializationView.DirectionCardView> directions(ProjectEntity project, List<TaskView> tasks) {
        Map<String, DirectionAccumulator> byKey = new LinkedHashMap<>();
        for (TaskView task : tasks) {
            String key = directionKey(project, task);
            byKey.computeIfAbsent(key, ignored -> new DirectionAccumulator(key, directionStatement(project, task), task.intent(), "", ""))
                    .add(task);
        }
        return byKey.values().stream()
                .map(DirectionAccumulator::view)
                .sorted(Comparator
                        .comparingInt(ProjectCommercializationView.DirectionCardView::score)
                        .reversed()
                        .thenComparing(ProjectCommercializationView.DirectionCardView::statement))
                .toList();
    }

    private String directionKey(ProjectEntity project, TaskView task) {
        String value = firstText(task.intent(), task.deliverable(), project.oneSentence(), project.title());
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String directionStatement(ProjectEntity project, TaskView task) {
        return firstText(task.title(), task.deliverable(), project.oneSentence(), project.title());
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "Project direction";
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private ProjectCommercializationView.ProofStatsView proofStats(List<TaskView> tasks) {
        return new ProjectCommercializationView.ProofStatsView(
                tasks.size(),
                (int) tasks.stream().filter(this::claimed).count(),
                (int) tasks.stream().filter(this::hasProof).count(),
                (int) tasks.stream().filter(this::accepted).count(),
                (int) tasks.stream().filter(task -> proofType(task, "deployment")).count(),
                (int) tasks.stream().filter(task -> proofType(task, "release")).count(),
                (int) tasks.stream().filter(this::opsIncidentProof).count());
    }

    private ProjectCommercializationView.RevenuePoolView revenuePool(List<SettlementEventEntity> events) {
        List<SettlementEventEntity> payable = events.stream()
                .filter(event -> event.amountMinor() != null && event.amountMinor() > 0)
                .toList();
        String currency = payable.stream()
                .map(SettlementEventEntity::currency)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(DEFAULT_CURRENCY);
        int total = payable.stream()
                .filter(event -> currency.equalsIgnoreCase(event.currency()))
                .mapToInt(SettlementEventEntity::amountMinor)
                .sum();
        return new ProjectCommercializationView.RevenuePoolView(currency.toUpperCase(Locale.ROOT), payable.size(), total);
    }

    private ProjectCommercializationView.DistributionEpochView currentDistribution(
            ProjectCommercializationView.RevenuePoolView revenue,
            List<TaskView> tasks,
            List<ProjectCommercializationView.ContributionLedgerEntryView> contributionLedger) {
        int acceptedCount = (int) tasks.stream().filter(this::accepted).count();
        int eligibleShares = contributionLedger.stream()
                .filter(entry -> "settled".equals(entry.status()))
                .mapToInt(ProjectCommercializationView.ContributionLedgerEntryView::shares)
                .sum();
        String status = revenue.totalMinor() > 0 && eligibleShares > 0 ? "ready" : "collecting";
        return new ProjectCommercializationView.DistributionEpochView(
                "current",
                status,
                revenue.currency(),
                revenue.totalMinor(),
                eligibleShares,
                acceptedCount);
    }

    private List<ProjectCommercializationView.ContributionLedgerEntryView> contributionLedger(String projectId) {
        // 中文注释：治理页直接读取统一贡献账本，WorkThread、验证奖励和订单验收共享同一展示来源。
        return dsl.resultQuery("""
                        select id, project_id, source_type, source_id, contribution_role, account_id,
                               task_value, shares, bounty_amount_minor, bounty_token, status,
                               contribution_weight, metadata, created_at
                        from contribution_ledger
                        where project_id = ?
                        order by created_at desc, id desc
                        """, projectId)
                .fetch(this::mapContributionLedgerEntry);
    }

    private ProjectCommercializationView.ContributionLedgerEntryView mapContributionLedgerEntry(Record record) {
        return new ProjectCommercializationView.ContributionLedgerEntryView(
                record.get("id", String.class),
                record.get("project_id", String.class),
                record.get("source_type", String.class),
                record.get("source_id", String.class),
                record.get("contribution_role", String.class),
                record.get("account_id", String.class),
                value(record.get("task_value", Integer.class)),
                value(record.get("shares", Integer.class)),
                value(record.get("bounty_amount_minor", Integer.class)),
                firstText(record.get("bounty_token", String.class), DEFAULT_CURRENCY),
                record.get("status", String.class),
                decimal(record.get("contribution_weight", BigDecimal.class)),
                PostgresJson.map(record.get("metadata", JSONB.class)),
                PostgresJson.instant(record.get("created_at", OffsetDateTime.class)));
    }

    private List<ProjectCommercializationView.ContributionMemberView> contributionMembers(List<ProjectCommercializationView.ContributionLedgerEntryView> entries) {
        Map<String, ContributorAccumulator> byAccount = new LinkedHashMap<>();
        for (ProjectCommercializationView.ContributionLedgerEntryView entry : entries) {
            if (!"settled".equals(entry.status())) {
                continue;
            }
            byAccount.computeIfAbsent(entry.accountId(), ContributorAccumulator::new).add(entry);
        }
        return byAccount.values().stream()
                .map(ContributorAccumulator::view)
                .sorted(Comparator
                        .comparingInt(ProjectCommercializationView.ContributionMemberView::totalShares)
                        .reversed()
                        .thenComparing(ProjectCommercializationView.ContributionMemberView::accountId))
                .toList();
    }

    private boolean claimed(TaskView task) {
        return task.claimedByAccountId() != null && !task.claimedByAccountId().isBlank();
    }

    private boolean hasProof(TaskView task) {
        return List.of("proof_submitted", "accepted", "settled", "changes_requested").contains(task.status());
    }

    private boolean accepted(TaskView task) {
        return List.of("accepted", "settled").contains(task.status());
    }

    private boolean proofType(TaskView task, String type) {
        return task.suggestedEvidence().stream()
                .map(item -> String.valueOf(item.getOrDefault("kind", "")))
                .anyMatch(kind -> kind.equalsIgnoreCase(type));
    }

    private boolean opsIncidentProof(TaskView task) {
        return proofType(task, "ops_incident") || proofType(task, "incident_fix");
    }

    private static final class DirectionAccumulator {
        private final String directionId;
        private final String statement;
        private final String hypothesis;
        private final String audience;
        private final String successMetric;
        private final List<String> taskIds = new ArrayList<>();
        private int claimedCount;
        private int proofCount;
        private int acceptedCount;
        private int score;

        private DirectionAccumulator(String directionId, String statement, String hypothesis, String audience, String successMetric) {
            this.directionId = directionId;
            this.statement = statement;
            this.hypothesis = hypothesis;
            this.audience = audience;
            this.successMetric = successMetric;
        }

        private void add(TaskView task) {
            taskIds.add(task.id());
            score += score(task);
            if (task.claimedByAccountId() != null && !task.claimedByAccountId().isBlank()) {
                claimedCount++;
            }
            if (List.of("proof_submitted", "accepted", "settled", "changes_requested").contains(task.status())) {
                proofCount++;
            }
            if (List.of("accepted", "settled").contains(task.status())) {
                acceptedCount++;
            }
        }

        private ProjectCommercializationView.DirectionCardView view() {
            return new ProjectCommercializationView.DirectionCardView(
                    directionId,
                    statement,
                    hypothesis,
                    audience,
                    successMetric,
                    score,
                    status(),
                    taskIds,
                    taskIds.size(),
                    claimedCount,
                    proofCount,
                    acceptedCount);
        }

        private String status() {
            if (acceptedCount > 0) {
                return "validated";
            }
            if (proofCount > 0) {
                return "proving";
            }
            if (claimedCount > 0) {
                return "active";
            }
            return "planned";
        }

        private int score(TaskView task) {
            int base = switch (task.status()) {
                case "settled" -> 21;
                case "accepted" -> 13;
                case "proof_submitted" -> 8;
                case "claimed", "working" -> 5;
                case "changes_requested", "held" -> 2;
                default -> 1;
            };
            boolean valuableEvidence = task.suggestedEvidence().stream()
                    .map(item -> String.valueOf(item.getOrDefault("kind", "")))
                    .anyMatch(kind -> Set.of("deployment", "release", "metric_snapshot", "experiment_report").contains(kind));
            int proofBonus = valuableEvidence ? 3 : 0;
            return base + proofBonus;
        }
    }

    private static final class ContributorAccumulator {
        private final String accountId;
        private final Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        private int totalShares;
        private int totalTaskValue;
        private int settledCount;
        private int bountyAmountMinor;
        private String bountyToken = DEFAULT_CURRENCY;
        private BigDecimal totalContributionWeight = BigDecimal.ZERO;

        private ContributorAccumulator(String accountId) {
            this.accountId = accountId;
        }

        private void add(ProjectCommercializationView.ContributionLedgerEntryView entry) {
            totalShares += entry.shares();
            totalTaskValue += entry.taskValue();
            settledCount++;
            bountyAmountMinor += entry.bountyAmountMinor();
            bountyToken = firstNonBlank(entry.bountyToken(), bountyToken);
            totalContributionWeight = totalContributionWeight.add(entry.contributionWeight());
            sourceCounts.merge(entry.sourceType(), 1, Integer::sum);
        }

        private ProjectCommercializationView.ContributionMemberView view() {
            return new ProjectCommercializationView.ContributionMemberView(
                    accountId,
                    totalShares,
                    totalTaskValue,
                    settledCount,
                    bountyAmountMinor,
                    bountyToken,
                    totalContributionWeight,
                    Map.copyOf(sourceCounts));
        }

        private static String firstNonBlank(String preferred, String fallback) {
            return preferred == null || preferred.isBlank() ? fallback : preferred;
        }
    }
}
