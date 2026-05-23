package com.monopolyfun.modules.work;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.order.api.request.BackofficeOverrideReviewRequest;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.order.service.command.OrderCommandService;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.risk.domain.RiskAccountStatus;
import com.monopolyfun.modules.risk.domain.RiskLevel;
import com.monopolyfun.modules.work.api.request.ClaimWorkItemRequest;
import com.monopolyfun.modules.work.api.request.RequestWorkHelpRequest;
import com.monopolyfun.modules.work.api.request.ReviewWorkReceiptRequest;
import com.monopolyfun.modules.work.api.request.SubmitWorkProgressRequest;
import com.monopolyfun.modules.work.api.request.SubmitWorkReceiptRequest;
import com.monopolyfun.modules.work.domain.WorkEventEntity;
import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.domain.WorkReceiptEntity;
import com.monopolyfun.modules.work.domain.WorkReviewEntity;
import com.monopolyfun.modules.work.domain.WorkRunEntity;
import com.monopolyfun.modules.work.infra.WorkCommerceTrustRepository;
import com.monopolyfun.modules.work.infra.WorkRepository;
import com.monopolyfun.modules.work.service.WorkCommandService;
import com.monopolyfun.modules.work.service.WorkQueryService;
import com.monopolyfun.shared.pagination.PageInfo;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import com.monopolyfun.shared.security.CurrentAccount;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkRunCommandServiceTest {
    private InMemoryWorkRepository workRepository;
    private WorkCommandService service;
    private OrderCommandService orderCommandService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-worker", "@worker", "Worker"),
                null,
                List.of()));
        workRepository = new InMemoryWorkRepository();
        var accountRepository = new InMemoryAccountRepository();
        var currentAccountAccess = new CurrentAccountAccess();
        var queryService = new WorkQueryService(
                currentAccountAccess,
                accountRepository,
                workRepository,
                Mockito.mock(OrganizationAuthorityService.class),
                Mockito.mock(OrderRepository.class));
        orderCommandService = Mockito.mock(OrderCommandService.class);
        service = new WorkCommandService(
                workRepository,
                queryService,
                currentAccountAccess,
                orderCommandService,
                workRepository.commerceTrustRepository,
                Mockito.mock(OrganizationAuthorityService.class));
        workRepository.upsertItem(workItem("wi-main", "ready"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void workRunLifecycleWritesReceiptReviewAndEvents() {
        service.claimWorkItem("wi-main", new ClaimWorkItemRequest("acct-worker", "agent"));
        service.submitProgress("wi-main", new SubmitWorkProgressRequest(
                "acct-worker", "实现主体", "主体逻辑已完成", Map.of("files", 2), List.of("commit:1"), List.of(), List.of(), "agent", "session-1", "runtime-1"));
        service.submitReceipt("wi-main", new SubmitWorkReceiptRequest(
                "acct-worker", "结果已交付", Map.of("summary", "done"), Map.of(), List.of("proof:1"), List.of("trace:1"), List.of("hash:1"), List.of(), List.of(), "runtime-1"));
        service.reviewReceipt("wi-main", new ReviewWorkReceiptRequest(
                "acct-worker", "accepted", "验收通过", List.of("review:1")));

        WorkRunEntity run = workRepository.findRunByItemId("wi-main").orElseThrow();
        WorkItemEntity item = workRepository.findItemByNoOrId("wi-main").orElseThrow();

        assertThat(run.status()).isEqualTo("accepted");
        assertThat(item.status()).isEqualTo("accepted");
        assertThat(workRepository.receiptsById).hasSize(2);
        assertThat(workRepository.findReviewByRunId(run.id())).isPresent();
        assertThat(workRepository.commerceTrustRepository.settlementRecords).hasSize(1);
        assertThat(workRepository.eventsById.values()).extracting(WorkEventEntity::actionId)
                .contains("claim_work_item", "submit_progress", "submit_receipt", "review_receipt");
    }

    @Test
    void claimLeaseLastsThreeHoursAndProgressRenewsIt() {
        Instant beforeClaim = Instant.now();
        var claim = service.claimWorkItem("wi-main", new ClaimWorkItemRequest("acct-worker", "agent"));
        WorkItemEntity claimed = workRepository.findItemByNoOrId("wi-main").orElseThrow();
        assertThat(claimed.claimExpiresAt()).isNotNull();
        assertThat(Duration.between(beforeClaim, claimed.claimExpiresAt()).toMinutes()).isBetween(179L, 180L);
        assertThat(claim.payload()).containsKey("claimExpiresAt");

        service.submitProgress("wi-main", new SubmitWorkProgressRequest(
                "acct-worker", "实现主体", "主体逻辑已完成", Map.of(), List.of(), List.of(), List.of(), "agent", "session-1", "runtime-1"));

        WorkItemEntity renewed = workRepository.findItemByNoOrId("wi-main").orElseThrow();
        assertThat(renewed.claimExpiresAt()).isAfter(claimed.claimExpiresAt());
        assertThat(Duration.between(renewed.updatedAt(), renewed.claimExpiresAt()).toMinutes()).isBetween(179L, 180L);
    }

    @Test
    void expiredClaimReturnsWorkItemToReady() {
        service.claimWorkItem("wi-main", new ClaimWorkItemRequest("acct-worker", "agent"));
        WorkItemEntity claimed = workRepository.findItemByNoOrId("wi-main").orElseThrow();
        workRepository.saveItem(new WorkItemEntity(
                claimed.id(),
                claimed.itemNo(),
                claimed.sourceType(),
                claimed.sourceId(),
                claimed.accountId(),
                claimed.title(),
                claimed.goal(),
                claimed.acceptanceCriteria(),
                claimed.inputRefs(),
                claimed.outputSchema(),
                claimed.requiredRole(),
                claimed.requiredCapability(),
                claimed.urgency(),
                claimed.status(),
                Instant.now().minusSeconds(1),
                claimed.readyAt(),
                claimed.createdAt(),
                claimed.updatedAt()));

        var accountRepository = new InMemoryAccountRepository();
        var currentAccountAccess = new CurrentAccountAccess();
        var queryService = new WorkQueryService(
                currentAccountAccess,
                accountRepository,
                workRepository,
                Mockito.mock(OrganizationAuthorityService.class),
                Mockito.mock(OrderRepository.class));

        WorkItemEntity released = queryService.listCurrentAccountWorkItems().stream()
                .filter(item -> item.itemNo().equals("wi-main"))
                .findFirst()
                .flatMap(item -> workRepository.findItemByNoOrId(item.id()))
                .orElseThrow();

        assertThat(released.status()).isEqualTo("ready");
        assertThat(released.claimExpiresAt()).isNull();
        assertThat(workRepository.findRunByItemAndActor("wi-main", "acct-worker")).isEmpty();
    }

    @Test
    void paymentAndDisputeTrustRecordsBindToWorkRun() {
        workRepository.upsertItem(workItem("wb-money-payment-order-1", "ready", "order", "order-1", Map.of("action", "complete_money_payment")));
        service.claimWorkItem("wb-money-payment-order-1", new ClaimWorkItemRequest("acct-worker", "agent"));
        assertThat(workRepository.commerceTrustRepository.paymentAuthorizations).hasSize(1);

        service.claimWorkItem("wi-main", new ClaimWorkItemRequest("acct-worker", "agent"));
        service.submitReceipt("wi-main", new SubmitWorkReceiptRequest(
                "acct-worker", "结果已交付", Map.of("summary", "done"), Map.of(), List.of("proof:1"), List.of("trace:1"), List.of("hash:1"), List.of(), List.of(), "runtime-1"));
        service.reviewReceipt("wi-main", new ReviewWorkReceiptRequest(
                "acct-worker", "disputed", "验收证据冲突，需要仲裁", List.of("review:1")));

        assertThat(workRepository.commerceTrustRepository.afterSaleCases).hasSize(1);
        assertThat(workRepository.commerceTrustRepository.arbitrationCases).hasSize(1);
    }

    @Test
    void disputeReviewRevisionDoesNotFinalizeSourceOrder() {
        workRepository.upsertItem(workItem("wb-review-open-order-1", "ready", "order", "order-1", Map.of("action", "resolve_disputed_order")));
        service.claimWorkItem("wb-review-open-order-1", new ClaimWorkItemRequest("acct-worker", "human"));
        service.submitReceipt("wb-review-open-order-1", new SubmitWorkReceiptRequest(
                "acct-worker", "需要补充争议材料", Map.of("decision", "revision_requested"), Map.of(), List.of("evidence:1"), List.of("trace:1"), List.of(), List.of(), List.of(), "manual-review"));

        service.reviewReceipt("wb-review-open-order-1", new ReviewWorkReceiptRequest(
                "acct-worker", "revision_requested", "证据不足，要求补充材料", List.of("review:1")));

        WorkItemEntity item = workRepository.findItemByNoOrId("wb-review-open-order-1").orElseThrow();
        WorkRunEntity run = workRepository.findRunByItemId(item.id()).orElseThrow();
        assertThat(item.status()).isEqualTo("revision_requested");
        assertThat(run.status()).isEqualTo("revision_requested");
        assertThat(workRepository.commerceTrustRepository.afterSaleCases).hasSize(1);
        Mockito.verify(orderCommandService, Mockito.never())
                .backofficeOverride(Mockito.eq("order-1"), Mockito.any(BackofficeOverrideReviewRequest.class));
    }

    @Test
    void requestHelpCreatesExecutableHelpItem() {
        service.claimWorkItem("wi-main", new ClaimWorkItemRequest("acct-worker", "agent"));
        service.requestHelp("wi-main", new RequestWorkHelpRequest(
                "acct-worker", "需要 reviewer 确认验收标准", "确认验收标准", List.of("blocker:1"), Map.of("blocked", true)));

        assertThat(workRepository.findItemsByAccountId("acct-worker")).anyMatch(item ->
                item.sourceType().equals("help_request") && item.status().equals("ready"));
        assertThat(workRepository.eventsById.values()).extracting(WorkEventEntity::actionId).contains("request_help");
    }

    @Test
    void requireWorkItemUsesCurrentAccountWhenItemNoIsShared() {
        workRepository.upsertItem(workItem("shared-work", "ready", "order", "order-1", Map.of("summary", "string")));
        workRepository.upsertItem(new WorkItemEntity(
                "shared-other",
                "shared-work",
                "order",
                "order-1",
                "acct-other",
                "其他账号待办",
                "其他账号目标",
                List.of(),
                List.of("order:1"),
                Map.of("summary", "string"),
                "worker",
                null,
                "attention",
                "ready",
                null,
                Instant.now(),
                Instant.now(),
                Instant.now()));

        service.claimWorkItem("shared-work", new ClaimWorkItemRequest("acct-worker", "agent"));

        assertThat(workRepository.findRunByItemAndActor("shared-work", "acct-worker")).isPresent();
        var otherItem = workRepository.findItemByNoOrId("shared-other").orElseThrow();
        assertThat(otherItem.status()).isEqualTo("ready");
    }

    @Test
    void reviewRequiresSubmittedRun() {
        service.claimWorkItem("wi-main", new ClaimWorkItemRequest("acct-worker", "agent"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                service.reviewReceipt("wi-main", new ReviewWorkReceiptRequest("acct-worker", "accepted", "提前验收", List.of())));

        assertThat(exception.getStatusCode().value()).isEqualTo(409);
    }

    private WorkItemEntity workItem(String itemNo, String status) {
        return workItem(itemNo, status, "initiative", "proposal-1", Map.of("summary", "string"));
    }

    private WorkItemEntity workItem(String itemNo, String status, String sourceType, String sourceId, Map<String, Object> outputSchema) {
        Instant now = Instant.now();
        return new WorkItemEntity(
                itemNo,
                itemNo,
                sourceType,
                sourceId,
                "acct-worker",
                "实现结果页",
                "交付可验收结果",
                List.of("能打开页面"),
                List.of("mandate:1"),
                outputSchema,
                "worker",
                null,
                "attention",
                status,
                null,
                now,
                now,
                now);
    }

    private static final class InMemoryWorkRepository implements WorkRepository {
        private final Map<String, WorkItemEntity> itemsById = new LinkedHashMap<>();
        private final Map<String, WorkRunEntity> runsById = new LinkedHashMap<>();
        private final Map<String, WorkReceiptEntity> receiptsById = new LinkedHashMap<>();
        private final Map<String, WorkReviewEntity> reviewsById = new LinkedHashMap<>();
        private final Map<String, WorkEventEntity> eventsById = new LinkedHashMap<>();
        private final InMemoryWorkCommerceTrustRepository commerceTrustRepository = new InMemoryWorkCommerceTrustRepository();

        @Override
        public void upsertItem(WorkItemEntity item) {
            itemsById.values().removeIf(existing -> existing.accountId().equals(item.accountId()) && existing.itemNo().equals(item.itemNo()));
            itemsById.put(item.id(), item);
        }

        @Override
        public List<WorkItemEntity> findItemsByAccountId(String accountId) {
            return itemsById.values().stream().filter(item -> item.accountId().equals(accountId)).toList();
        }

        @Override
        public List<WorkItemEntity> findSubmittedProjectRoleItems() {
            return itemsById.values().stream()
                    .filter(item -> "project_role_task".equals(item.sourceType()) && "submitted".equals(item.status()))
                    .toList();
        }

        @Override
        public int releaseExpiredClaims(Instant now) {
            int released = 0;
            for (WorkItemEntity item : List.copyOf(itemsById.values())) {
                if ("claimed".equals(item.status()) && item.claimExpiresAt() != null && !item.claimExpiresAt().isAfter(now)) {
                    saveItem(new WorkItemEntity(
                            item.id(), item.itemNo(), item.sourceType(), item.sourceId(), item.accountId(), item.title(), item.goal(),
                            item.acceptanceCriteria(), item.inputRefs(), item.outputSchema(), item.requiredRole(), item.requiredCapability(),
                            item.urgency(), "ready", null, item.readyAt(), item.createdAt(), now));
                    runsById.values().removeIf(run -> run.workItemId().equals(item.id()) && List.of("claimed", "running").contains(run.status()));
                    released++;
                }
            }
            return released;
        }

        @Override
        public int renewClaimLease(String accountId, String itemNo, Instant claimExpiresAt, Instant now) {
            for (WorkItemEntity item : List.copyOf(itemsById.values())) {
                if (item.accountId().equals(accountId) && item.itemNo().equals(itemNo) && "claimed".equals(item.status())) {
                    saveItem(new WorkItemEntity(
                            item.id(), item.itemNo(), item.sourceType(), item.sourceId(), item.accountId(), item.title(), item.goal(),
                            item.acceptanceCriteria(), item.inputRefs(), item.outputSchema(), item.requiredRole(), item.requiredCapability(),
                            item.urgency(), item.status(), claimExpiresAt, item.readyAt(), item.createdAt(), now));
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public int closeStaleSourceItems(String accountId, Set<String> sourceTypes, Set<String> activeItemNos, String reason) {
            int closed = 0;
            for (WorkItemEntity item : List.copyOf(itemsById.values())) {
                if (item.accountId().equals(accountId)
                        && sourceTypes.contains(item.sourceType())
                        && !activeItemNos.contains(item.itemNo())
                        && List.of("ready", "claimed", "submitted", "revision_requested", "disputed").contains(item.status())) {
                    saveItem(closedItem(item));
                    closed++;
                }
            }
            return closed;
        }

        @Override
        public int closeOpenItemsBySource(String sourceType, String sourceId, String reason) {
            int closed = 0;
            for (WorkItemEntity item : List.copyOf(itemsById.values())) {
                if (item.sourceType().equals(sourceType)
                        && item.sourceId().equals(sourceId)
                        && List.of("ready", "claimed", "submitted", "revision_requested", "disputed").contains(item.status())) {
                    saveItem(closedItem(item));
                    closed++;
                }
            }
            return closed;
        }

        @Override
        public List<WorkItemEntity> findItemsBySource(String sourceType, String sourceId) {
            return itemsById.values().stream().filter(item -> item.sourceType().equals(sourceType) && item.sourceId().equals(sourceId)).toList();
        }

        @Override
        public Optional<WorkItemEntity> findItemByNoOrId(String itemNoOrId) {
            return itemsById.values().stream().filter(item -> item.id().equals(itemNoOrId) || item.itemNo().equals(itemNoOrId)).findFirst();
        }

        @Override
        public Optional<WorkRunEntity> findRunByItemAndActor(String workItemId, String actorAccountId) {
            return runsById.values().stream().filter(run -> run.workItemId().equals(workItemId) && run.actorAccountId().equals(actorAccountId)).findFirst();
        }

        @Override
        public Optional<WorkRunEntity> findRunByItemId(String workItemId) {
            return runsById.values().stream().filter(run -> run.workItemId().equals(workItemId)).findFirst();
        }

        @Override
        public Optional<WorkRunEntity> findRunByNoOrId(String runNoOrId) {
            return runsById.values().stream().filter(run -> run.id().equals(runNoOrId) || run.runNo().equals(runNoOrId)).findFirst();
        }

        @Override
        public WorkRunEntity saveRun(WorkRunEntity run) {
            runsById.values().removeIf(existing -> existing.workItemId().equals(run.workItemId()) && existing.actorAccountId().equals(run.actorAccountId()));
            runsById.put(run.id(), run);
            return run;
        }

        @Override
        public WorkItemEntity saveItem(WorkItemEntity item) {
            upsertItem(item);
            return item;
        }

        @Override
        public WorkReceiptEntity saveReceipt(WorkReceiptEntity receipt) {
            receiptsById.put(receipt.id(), receipt);
            return receipt;
        }

        @Override
        public Optional<WorkReceiptEntity> findLatestReceiptByRunId(String workRunId) {
            return receiptsById.values().stream().filter(receipt -> receipt.workRunId().equals(workRunId)).reduce((first, second) -> second);
        }

        @Override
        public WorkReviewEntity saveReview(WorkReviewEntity review) {
            reviewsById.values().removeIf(existing -> existing.workRunId().equals(review.workRunId()));
            reviewsById.put(review.id(), review);
            return review;
        }

        @Override
        public Optional<WorkReviewEntity> findReviewByRunId(String workRunId) {
            return reviewsById.values().stream().filter(review -> review.workRunId().equals(workRunId)).findFirst();
        }

        @Override
        public void saveEvent(WorkEventEntity event) {
            eventsById.put(event.id(), event);
        }

        private WorkItemEntity closedItem(WorkItemEntity item) {
            return new WorkItemEntity(
                    item.id(), item.itemNo(), item.sourceType(), item.sourceId(), item.accountId(), item.title(), item.goal(),
                    item.acceptanceCriteria(), item.inputRefs(), item.outputSchema(), item.requiredRole(), item.requiredCapability(),
                    item.urgency(), "closed", null, item.readyAt(), item.createdAt(), Instant.now());
        }
    }

    private static final class InMemoryWorkCommerceTrustRepository implements WorkCommerceTrustRepository {
        private final List<Map<String, Object>> paymentAuthorizations = new java.util.ArrayList<>();
        private final List<Map<String, Object>> settlementRecords = new java.util.ArrayList<>();
        private final List<Map<String, Object>> afterSaleCases = new java.util.ArrayList<>();
        private final List<Map<String, Object>> arbitrationCases = new java.util.ArrayList<>();

        @Override
        public void savePaymentAuthorization(WorkRunEntity run, WorkItemEntity item, String actorAccountId, String status, Map<String, Object> input, Map<String, Object> output) {
            paymentAuthorizations.add(Map.of("runNo", run.runNo(), "itemNo", item.itemNo(), "status", status));
        }

        @Override
        public void saveSettlementRecord(WorkRunEntity run, WorkReviewEntity review, WorkItemEntity item, String actorAccountId, String status, Map<String, Object> input, Map<String, Object> output) {
            settlementRecords.add(Map.of("runNo", run.runNo(), "itemNo", item.itemNo(), "status", status));
        }

        @Override
        public void saveAfterSaleCase(WorkRunEntity run, WorkReviewEntity review, WorkItemEntity item, String actorAccountId, String status, String reason, Map<String, Object> input) {
            afterSaleCases.add(Map.of("runNo", run.runNo(), "itemNo", item.itemNo(), "status", status, "reason", reason));
        }

        @Override
        public void saveArbitrationCase(WorkRunEntity run, WorkReviewEntity review, WorkItemEntity item, String actorAccountId, String status, String reason, Map<String, Object> input) {
            arbitrationCases.add(Map.of("runNo", run.runNo(), "itemNo", item.itemNo(), "status", status, "reviewNo", review.reviewNo()));
        }
    }

    private static final class InMemoryAccountRepository implements AccountRepository {
        @Override
        public List<AccountEntity> findAll() {
            return List.of(account("acct-worker"));
        }

        @Override
        public PageResult<AccountEntity> findPublic(PageQuery pageQuery) {
            return new PageResult<>(findAll(), new PageInfo(pageQuery.limit(), null, false));
        }

        @Override
        public PageResult<AccountEntity> findRiskAccounts(String status, String riskLevel, String q, PageQuery pageQuery) {
            return findPublic(pageQuery);
        }

        @Override
        public List<AccountEntity> findByIds(Collection<String> ids) {
            return ids.stream().map(this::findById).flatMap(Optional::stream).toList();
        }

        @Override
        public Optional<AccountEntity> findById(String id) {
            return Optional.of(account(id));
        }

        @Override
        public Optional<AccountEntity> findByHandle(String handle) {
            return Optional.empty();
        }

        @Override
        public AccountEntity save(AccountEntity account) {
            return account;
        }

        private AccountEntity account(String id) {
            return new AccountEntity(id, id, id, null, RiskAccountStatus.ACTIVE, RiskLevel.NORMAL, null, null, null, Map.of(), Instant.now(), Instant.now());
        }
    }
}
