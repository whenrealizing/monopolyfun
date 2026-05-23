package com.monopolyfun.modules.workbench.service.query;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.work.service.WorkQueryService;
import com.monopolyfun.modules.workbench.domain.WorkbenchDismissalEntity;
import com.monopolyfun.modules.workbench.infra.WorkbenchDismissalRepository;
import com.monopolyfun.modules.workbench.service.view.WorkbenchItemView;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class WorkbenchQueryService {
    private final CurrentAccountAccess currentAccountAccess;
    private final AccountRepository accountRepository;
    private final WorkbenchDismissalRepository dismissalRepository;
    private final WorkQueryService workQueryService;

    public WorkbenchQueryService(
            CurrentAccountAccess currentAccountAccess,
            AccountRepository accountRepository,
            WorkbenchDismissalRepository dismissalRepository,
            WorkQueryService workQueryService) {
        this.currentAccountAccess = currentAccountAccess;
        this.accountRepository = accountRepository;
        this.dismissalRepository = dismissalRepository;
        this.workQueryService = workQueryService;
    }

    public List<WorkbenchItemView> listCurrentAccountItems() {
        AccountEntity account = requireCurrentAccount();
        Set<String> dismissedKeys = dismissalRepository.findItemKeysByAccountId(account.id());
        // 中文注释：Workbench 现在只读 Work 执行内核，dismissal 作为用户偏好叠加到投影层。
        return workQueryService.listCurrentAccountWorkbenchItems().stream()
                .filter(item -> !item.canDismiss() || !dismissedKeys.contains(itemKey(item)))
                .sorted(Comparator
                        .comparingInt((WorkbenchItemView item) -> urgencyRank(item.urgency()))
                        .thenComparing((WorkbenchItemView item) -> updatedAt(item.updatedAt()), Comparator.reverseOrder())
                        .thenComparing(WorkbenchItemView::title))
                .toList();
    }

    public WorkbenchItemView requireCurrentAccountItem(String itemId) {
        return listCurrentAccountItems().stream()
                .filter(item -> item.id().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workbench item not found"));
    }

    public WorkbenchItemView dismissCurrentAccountItem(String itemId) {
        AccountEntity account = requireCurrentAccount();
        WorkbenchItemView item = workQueryService.requireCurrentAccountWorkbenchItem(itemId);
        if (!item.canDismiss()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workbench item cannot be dismissed");
        }
        dismissalRepository.save(new WorkbenchDismissalEntity(
                account.id(),
                itemKey(item),
                item.reason(),
                item.target().type(),
                item.target().id(),
                Instant.now()));
        return item;
    }

    private AccountEntity requireCurrentAccount() {
        String accountId = currentAccountAccess.requireAccountId();
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
    }

    private String itemKey(WorkbenchItemView item) {
        return WorkbenchItemKeys.itemKey(item.reason(), item.target().type(), item.target().id());
    }

    private int urgencyRank(String urgency) {
        if ("urgent".equals(urgency)) {
            return 0;
        }
        if ("attention".equals(urgency)) {
            return 1;
        }
        return 2;
    }

    private Instant updatedAt(String value) {
        try {
            return value == null || value.isBlank() ? Instant.EPOCH : Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }
}
