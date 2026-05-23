package com.monopolyfun.modules.identity.service.verification;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.identity.infra.IdentityFactRepository;
import com.monopolyfun.modules.identity.service.display.IdentityDisplaySkinService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentityProjectionRepairService {
    private final AccountRepository accountRepository;
    private final IdentityFactRepository identityFactRepository;
    private final IdentityBadgeProjector identityBadgeProjector;
    private final IdentityDisplaySkinService identityDisplaySkinService;

    public IdentityProjectionRepairService(
            AccountRepository accountRepository,
            IdentityFactRepository identityFactRepository,
            IdentityBadgeProjector identityBadgeProjector,
            IdentityDisplaySkinService identityDisplaySkinService) {
        this.accountRepository = accountRepository;
        this.identityFactRepository = identityFactRepository;
        this.identityBadgeProjector = identityBadgeProjector;
        this.identityDisplaySkinService = identityDisplaySkinService;
    }

    public void rebuildAccountIdentity(String accountId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        var facts = identityFactRepository.findByAccountId(accountId);
        identityBadgeProjector.project(account, facts);
        identityDisplaySkinService.normalizePreference(account, facts);
    }

    public void rebuildAll() {
        // 中文注释：repair 入口用于 seed 和迁移后全量刷新派生 badge 与展示身份偏好。
        accountRepository.findAll()
                .forEach(account -> rebuildAccountIdentity(account.id()));
    }
}
