package com.monopolyfun;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.risk.domain.RiskAccountStatus;
import com.monopolyfun.modules.risk.domain.RiskLevel;
import com.monopolyfun.modules.work.domain.WorkEventEntity;
import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.domain.WorkReceiptEntity;
import com.monopolyfun.modules.work.domain.WorkReviewEntity;
import com.monopolyfun.modules.work.domain.WorkRunEntity;
import com.monopolyfun.modules.work.infra.WorkRepository;
import com.monopolyfun.modules.work.service.WorkQueryService;
import com.monopolyfun.modules.workbench.domain.WorkbenchDismissalEntity;
import com.monopolyfun.modules.workbench.infra.WorkbenchDismissalRepository;
import com.monopolyfun.modules.workbench.service.query.WorkbenchQueryService;
import com.monopolyfun.modules.workbench.service.view.WorkbenchItemView;
import com.monopolyfun.shared.security.CurrentAccount;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchQueryServiceTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void projectsCurrentAccountActionItemsFromPersistedWorkItems() {
        WorkbenchQueryService service = createService(new InMemoryWorkbenchDismissalRepository());

        List<WorkbenchItemView> items = service.listCurrentAccountItems();

        assertEquals(4, items.size());
        assertTrue(items.stream().anyMatch(item -> item.id().contains("delivery-result") && !item.canDismiss()));
        assertTrue(items.stream().anyMatch(item -> item.id().contains("money-payment") && !item.canDismiss()));
        assertTrue(items.stream().anyMatch(item -> item.id().contains("lead-review") && !item.canDismiss()));
        assertTrue(items.stream().noneMatch(item -> item.id().contains("lead-offer")));
        assertTrue(items.stream().noneMatch(item -> item.id().contains("lead-request")));
        assertTrue(items.stream().noneMatch(item -> item.id().contains("lead-project")));
        // 中文注释：页面级 agent 已删除，工作台只暴露业务详情页跳转和业务 action。
        assertTrue(items.stream().allMatch(item -> item.targetHref() != null));
        assertTrue(items.stream()
                .filter(item -> !item.canDismiss())
                .allMatch(item -> item.actions().stream().map(action -> action.id()).toList().containsAll(List.of("open", "claim_work_item"))));
        assertTrue(items.stream()
                .filter(WorkbenchItemView::canDismiss)
                .allMatch(item -> item.actions().stream().map(action -> action.id()).toList().equals(List.of("open", "dismiss"))));
    }

    @Test
    void postOperationsRemindersAreNotWorkbenchItems() {
        WorkbenchQueryService service = createService(new InMemoryWorkbenchDismissalRepository());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.dismissCurrentAccountItem("wb-lead-offer-offer-1"));

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void lockedItemsCannotBeDismissed() {
        WorkbenchQueryService service = createService(new InMemoryWorkbenchDismissalRepository());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.dismissCurrentAccountItem("wb-delivery-result-MF260505ORD000001X"));

        assertEquals(409, exception.getStatusCode().value());
    }

    private WorkbenchQueryService createService(InMemoryWorkbenchDismissalRepository dismissalRepository) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-worker", "@worker", "Worker"),
                null,
                List.of()));
        InMemoryWorkRepository workRepository = new InMemoryWorkRepository();
        seedWorkbenchItems(workRepository);
        WorkQueryService workQueryService = new WorkQueryService(
                new CurrentAccountAccess(),
                new InMemoryAccountRepository(),
                workRepository,
                Mockito.mock(OrganizationAuthorityService.class),
                Mockito.mock(OrderRepository.class));
        return new WorkbenchQueryService(new CurrentAccountAccess(), new InMemoryAccountRepository(), dismissalRepository, workQueryService);
    }

    private void seedWorkbenchItems(InMemoryWorkRepository workRepository) {
        Instant now = Instant.now();
        workRepository.upsertItem(workItem("wb-delivery-result-MF260505ORD000001X", "order", "MF260505ORD000001X", "提交交付结果", "买方付款已确认，请提交交付内容供验收。", "fulfiller", "delivery_result_due", null, false, now));
        workRepository.upsertItem(workItem("wb-money-payment-MF260505ORD000002X", "order", "MF260505ORD000002X", "完成现金支付", "这笔订单正在等待线下付款确认；完成支付后卖方会开始交付。", "payer", "complete_money_payment", null, false, now));
        workRepository.upsertItem(workItem("wb-lead-review-MF260505ORD000003X", "order", "MF260505ORD000003X", "负责人验收或争议", "交付内容已提交，请验收、要求返工或发起争议。", "lead", "lead_accept_or_dispute", null, false, now));
        workRepository.upsertItem(workItem("wb-lead-offer-offer-1", "offer", "offer-1", "补充报价供给", "当前报价还没有成交，可以继续扩充供给或推广。", "lead", "expand_offer_supply", null, true, now));
        workRepository.upsertItem(workItem("wb-lead-request-request-1", "request", "request-1", "推进需求履约", "当前需求还没有被接单，可以继续推进公开招募。", "lead", "promote_request", null, true, now));
        workRepository.upsertItem(workItem("wb-lead-project-project-1", "project", "project-1", "推进项目招募", "当前项目还没有消耗库存，可以继续对外招募或推进参与。", ProjectRoleCode.SYSTEM_CEO.code(), "promote_project", ProjectCapability.MARKET_GROWTH_MANAGE.code(), true, now));
        workRepository.upsertItem(workItem("wb-share-release-request-1", "share_release_request", "request-1", "审批 Shares 发放", "等待审批", ProjectRoleCode.SYSTEM_CFO.code(), "share_release_approval", ProjectCapability.SETTLEMENT_MANAGE.code(), false, now));
    }

    private WorkItemEntity workItem(
            String itemNo,
            String sourceType,
            String sourceId,
            String title,
            String goal,
            String role,
            String action,
            String capability,
            boolean canDismiss,
            Instant now) {
        return new WorkItemEntity(
                "wi-acct-worker-" + itemNo,
                itemNo,
                sourceType,
                sourceId,
                "acct-worker",
                title,
                goal,
                List.of(),
                List.of(sourceType + ":" + sourceId),
                Map.of("action", action, "canDismissSeed", canDismiss),
                role,
                capability,
                "attention",
                "ready",
                null,
                now,
                now,
                now);
    }

    private static final class InMemoryWorkbenchDismissalRepository implements WorkbenchDismissalRepository {
        private final Set<String> keys = new HashSet<>();

        @Override
        public Set<String> findItemKeysByAccountId(String accountId) {
            return Set.copyOf(keys);
        }

        @Override
        public WorkbenchDismissalEntity save(WorkbenchDismissalEntity dismissal) {
            keys.add(dismissal.itemKey());
            return dismissal;
        }
    }

    private static final class InMemoryWorkRepository implements WorkRepository {
        private final Map<String, WorkItemEntity> itemsById = new LinkedHashMap<>();
        private final Map<String, WorkRunEntity> runsById = new LinkedHashMap<>();
        private final Map<String, WorkReceiptEntity> receiptsById = new LinkedHashMap<>();
        private final Map<String, WorkReviewEntity> reviewsById = new LinkedHashMap<>();

        @Override
        public void upsertItem(WorkItemEntity item) {
            itemsById.values().removeIf(existing -> existing.accountId().equals(item.accountId())
                    && existing.itemNo().equals(item.itemNo()));
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
            return itemsById.values().stream()
                    .filter(item -> item.id().equals(itemNoOrId) || item.itemNo().equals(itemNoOrId))
                    .findFirst();
        }

        @Override
        public Optional<WorkRunEntity> findRunByItemAndActor(String workItemId, String actorAccountId) {
            return runsById.values().stream()
                    .filter(run -> run.workItemId().equals(workItemId) && run.actorAccountId().equals(actorAccountId))
                    .findFirst();
        }

        @Override
        public Optional<WorkRunEntity> findRunByItemId(String workItemId) {
            return runsById.values().stream().filter(run -> run.workItemId().equals(workItemId)).findFirst();
        }

        @Override
        public Optional<WorkRunEntity> findRunByNoOrId(String runNoOrId) {
            return runsById.values().stream()
                    .filter(run -> run.id().equals(runNoOrId) || run.runNo().equals(runNoOrId))
                    .findFirst();
        }

        @Override
        public WorkRunEntity saveRun(WorkRunEntity run) {
            runsById.values().removeIf(existing -> existing.workItemId().equals(run.workItemId())
                    && existing.actorAccountId().equals(run.actorAccountId()));
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
            return receiptsById.values().stream().filter(receipt -> receipt.workRunId().equals(workRunId)).findFirst();
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
        }

        private WorkItemEntity closedItem(WorkItemEntity item) {
            return new WorkItemEntity(
                    item.id(), item.itemNo(), item.sourceType(), item.sourceId(), item.accountId(), item.title(), item.goal(),
                    item.acceptanceCriteria(), item.inputRefs(), item.outputSchema(), item.requiredRole(), item.requiredCapability(),
                    item.urgency(), "closed", null, item.readyAt(), item.createdAt(), Instant.now());
        }
    }

    private static final class InMemoryAccountRepository implements AccountRepository {
        @Override
        public List<AccountEntity> findAll() {
            return List.of(findById("acct-worker").orElseThrow());
        }

        @Override
        public com.monopolyfun.shared.pagination.PageResult<AccountEntity> findPublic(com.monopolyfun.shared.pagination.PageQuery pageQuery) {
            return new com.monopolyfun.shared.pagination.PageResult<>(
                    findAll().stream().limit(pageQuery.limit()).toList(),
                    new com.monopolyfun.shared.pagination.PageInfo(pageQuery.limit(), null, false));
        }

        @Override
        public com.monopolyfun.shared.pagination.PageResult<AccountEntity> findRiskAccounts(
                String status,
                String riskLevel,
                String q,
                com.monopolyfun.shared.pagination.PageQuery pageQuery) {
            return findPublic(pageQuery);
        }

        @Override
        public List<AccountEntity> findByIds(java.util.Collection<String> ids) {
            return ids.stream().map(this::findById).flatMap(Optional::stream).toList();
        }

        @Override
        public Optional<AccountEntity> findById(String id) {
            return Optional.of(new AccountEntity(id, "worker", "Worker", null, RiskAccountStatus.ACTIVE, RiskLevel.NORMAL, null, null, null, Map.of(), Instant.now(), Instant.now()));
        }

        @Override
        public Optional<AccountEntity> findByHandle(String handle) {
            return Optional.empty();
        }

        @Override
        public AccountEntity save(AccountEntity account) {
            return account;
        }
    }

}
