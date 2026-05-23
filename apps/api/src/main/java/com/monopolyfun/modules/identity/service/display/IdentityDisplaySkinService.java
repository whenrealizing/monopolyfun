package com.monopolyfun.modules.identity.service.display;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.domain.IdentityFactEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.identity.infra.IdentityFactRepository;
import com.monopolyfun.modules.identity.service.query.IdentityQueryService;
import com.monopolyfun.modules.identity.service.view.IdentityPageView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class IdentityDisplaySkinService {
    private final AccountRepository accountRepository;
    private final IdentityFactRepository identityFactRepository;
    private final IdentityDisplaySkinProjector projector;
    private final IdentityQueryService identityQueryService;

    public IdentityDisplaySkinService(
            AccountRepository accountRepository,
            IdentityFactRepository identityFactRepository,
            IdentityDisplaySkinProjector projector,
            IdentityQueryService identityQueryService) {
        this.accountRepository = accountRepository;
        this.identityFactRepository = identityFactRepository;
        this.projector = projector;
        this.identityQueryService = identityQueryService;
    }

    public IdentityPageView update(String accountId, String source, String certifierId) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        IdentityDisplaySkinPreference preference = resolvePreference(account, source, certifierId);
        AccountEntity updated = new AccountEntity(
                account.id(),
                account.handle(),
                account.displayName(),
                account.passwordHash(),
                account.status(),
                account.riskLevel(),
                account.frozenUntil(),
                account.riskReason(),
                account.riskUpdatedAt(),
                projector.writePreference(account.metadata(), preference),
                account.createdAt(),
                Instant.now());
        accountRepository.save(updated);
        return identityQueryService.getCurrentIdentity();
    }

    public void normalizePreference(AccountEntity account, List<IdentityFactEntity> facts) {
        IdentityDisplaySkinPreference preference = projector.readPreference(account.metadata());
        if (!"verified_identity".equals(preference.source()) || preference.certifierId() == null) {
            return;
        }
        if (projector.hasSelectableCertifier(account, facts, preference.certifierId())) {
            return;
        }
        // 中文注释：认证事实失效时立即回退默认皮肤，避免 UI 继续展示已撤销外部资料。
        accountRepository.save(new AccountEntity(
                account.id(),
                account.handle(),
                account.displayName(),
                account.passwordHash(),
                account.status(),
                account.riskLevel(),
                account.frozenUntil(),
                account.riskReason(),
                account.riskUpdatedAt(),
                projector.writePreference(account.metadata(), IdentityDisplaySkinPreference.nativeSkin()),
                account.createdAt(),
                Instant.now()));
    }

    private IdentityDisplaySkinPreference resolvePreference(AccountEntity account, String source, String certifierId) {
        String normalizedSource = source == null ? "" : source.trim();
        if ("native".equals(normalizedSource)) {
            return IdentityDisplaySkinPreference.nativeSkin();
        }
        if (!"verified_identity".equals(normalizedSource)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display skin source is invalid");
        }
        String normalizedCertifierId = certifierId == null ? "" : certifierId.trim();
        if (normalizedCertifierId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Display skin certifier is required");
        }
        List<IdentityFactEntity> facts = identityFactRepository.findByAccountId(account.id());
        if (!projector.hasSelectableCertifier(account, facts, normalizedCertifierId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Display skin certifier is not verified");
        }
        return new IdentityDisplaySkinPreference("verified_identity", normalizedCertifierId);
    }
}
