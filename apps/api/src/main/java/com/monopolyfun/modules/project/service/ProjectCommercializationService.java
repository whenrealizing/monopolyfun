package com.monopolyfun.modules.project.service;

import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.TaskView;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolService;
import com.monopolyfun.modules.project.service.view.ProjectCommercializationView;
import com.monopolyfun.modules.settlement.domain.SettlementEventEntity;
import com.monopolyfun.modules.settlement.infra.SettlementEventRepository;
import com.monopolyfun.modules.share.service.ProjectSharePoolService;
import com.monopolyfun.modules.share.service.view.ProjectSharesView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    private final ProjectSharePoolService projectSharePoolService;
    private final SettlementEventRepository settlementEventRepository;

    public ProjectCommercializationService(
            ProjectRepository projectRepository,
            ProjectValidationProtocolService validationProtocolService,
            ProjectSharePoolService projectSharePoolService,
            SettlementEventRepository settlementEventRepository) {
        this.projectRepository = projectRepository;
        this.validationProtocolService = validationProtocolService;
        this.projectSharePoolService = projectSharePoolService;
        this.settlementEventRepository = settlementEventRepository;
    }

    public ProjectCommercializationView getCommercialization(String projectNo) {
        ProjectEntity project = requireProject(projectNo);
        List<TaskView> tasks = validationProtocolService.listProjectTasks(project.projectNo());
        ProjectSharesView shares = projectSharePoolService.viewByProjectId(project.id());
        ProjectCommercializationView.RevenuePoolView revenue = revenuePool(settlementEventRepository.findByProjectId(project.id()));
        List<ProjectCommercializationView.DirectionCardView> directions = directions(project, tasks);
        List<ProjectCommercializationView.DirectionCardView> validated = directions.stream()
                .filter(direction -> "validated".equals(direction.status()))
                .toList();
        // 中文注释：商业化页只输出由 Validation Task、settlement 和 share pool 推导出的事实。
        return new ProjectCommercializationView(
                project.projectNo(),
                project.id(),
                directions,
                directions.isEmpty() ? null : directions.get(0),
                validated,
                proofStats(tasks),
                shares,
                revenue,
                currentDistribution(revenue, shares, tasks));
    }

    private ProjectEntity requireProject(String projectNo) {
        if (projectNo == null || projectNo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "project business number is required");
        }
        return projectRepository.findByProjectNo(projectNo.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
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
            ProjectSharesView shares,
            List<TaskView> tasks) {
        int acceptedCount = (int) tasks.stream().filter(this::accepted).count();
        String status = revenue.totalMinor() > 0 && shares.shareMinted() > 0 ? "ready" : "collecting";
        return new ProjectCommercializationView.DistributionEpochView(
                "current",
                status,
                revenue.currency(),
                revenue.totalMinor(),
                shares.shareMinted(),
                acceptedCount);
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
}
