package com.monopolyfun.modules.identity.api;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.identity.service.PublicIdentityRefs;
import com.monopolyfun.modules.identity.service.display.AccountSummaryProjector;
import com.monopolyfun.modules.identity.service.view.AccountSummary;
import com.monopolyfun.modules.identity.service.view.PublicAccountSummary;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountRepository accountRepository;
    private final AccountSummaryProjector accountSummaryProjector;
    private final CurrentAccountAccess currentAccountAccess;

    public AccountController(
            AccountRepository accountRepository,
            AccountSummaryProjector accountSummaryProjector,
            CurrentAccountAccess currentAccountAccess) {
        this.accountRepository = accountRepository;
        this.accountSummaryProjector = accountSummaryProjector;
        this.currentAccountAccess = currentAccountAccess;
    }

    @GetMapping
    @Operation(operationId = "listPublicAccounts")
    public PageResult<PublicAccountSummary> listAccounts(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        // 中文注释：公共账号列表服务订单和市场展示，统一走 PageResult 防止账号表增长后全量读取。
        var page = accountRepository.findPublic(PageQuery.of(limit, cursor));
        return new PageResult<>(page.items().stream()
                .map(accountSummaryProjector::publicProject)
                .toList(), page.pageInfo());
    }

    @GetMapping("/lookup")
    @Operation(operationId = "lookupPublicAccounts")
    public List<PublicAccountSummary> lookupAccounts(@RequestParam(name = "ids") List<String> ids) {
        List<String> requestedIds = ids == null ? List.of() : ids.stream()
                .map(PublicIdentityRefs::accountId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (requestedIds.isEmpty()) {
            return List.of();
        }

        List<String> candidateHandles = requestedIds.stream()
                .flatMap(id -> java.util.stream.Stream.of(id, "@" + id))
                .distinct()
                .toList();
        var summariesByPublicId = new LinkedHashMap<String, PublicAccountSummary>();
        accountRepository.findByIds(requestedIds).forEach(account -> {
            // 中文注释：订单内部保存 account.id，公开 lookup 要能按内部 id 回填，否则订单参与方会显示未知账号。
            summariesByPublicId.putIfAbsent(account.id(), publicProject(account, account.id()));
        });
        accountRepository.findByHandles(candidateHandles).forEach(account -> {
            // 中文注释：公开账号摘要按 public id 去重回填，兼容历史 handle 是否带 @ 的存储差异。
            PublicAccountSummary summary = accountSummaryProjector.publicProject(account);
            summariesByPublicId.putIfAbsent(summary.id(), summary);
        });
        return requestedIds.stream()
                .map(summariesByPublicId::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private PublicAccountSummary publicProject(AccountEntity account, String id) {
        PublicAccountSummary summary = accountSummaryProjector.publicProject(account);
        return new PublicAccountSummary(id, summary.handle(), summary.displayName(), summary.agentSummary(), summary.displaySkin());
    }

    @GetMapping("/directory")
    @Operation(operationId = "listAccountDirectory")
    public PageResult<AccountSummary> listAccountDirectory(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String cursor) {
        currentAccountAccess.requireAccountId();
        var page = accountRepository.findPublic(PageQuery.of(limit, cursor));
        return new PageResult<>(page.items().stream()
                .map(accountSummaryProjector::project)
                .toList(), page.pageInfo());
    }
}
