package com.monopolyfun.modules.risk.service;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.risk.domain.RiskAccountStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class AccountRiskGuard {
    private final AccountRepository accountRepository;
    private final RiskCenterService riskCenterService;

    public AccountRiskGuard(AccountRepository accountRepository, RiskCenterService riskCenterService) {
        this.accountRepository = accountRepository;
        this.riskCenterService = riskCenterService;
    }

    public void requireLoginAllowed(String handle) {
        accountRepository.findByHandle(normalizeHandle(handle)).ifPresent(account -> {
            AccountEntity refreshed = riskCenterService.refreshFrozenState(account);
            requireAccountStateAllowed(refreshed);
        });
    }

    public void requireAllowed(String accountId, RiskAction action) {
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        AccountEntity refreshed = riskCenterService.refreshFrozenState(account);
        requireAccountStateAllowed(refreshed);
        RiskDecision decision = riskCenterService.evaluateAction(refreshed, action);
        if (decision == RiskDecision.FREEZE_ACCOUNT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account frozen by risk control");
        }
        if (decision == RiskDecision.BAN_ACCOUNT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account banned by risk control");
        }
    }

    private void requireAccountStateAllowed(AccountEntity account) {
        // 中文注释：所有交易入口共享账号处置状态，避免发帖、接帖、上传各自维护封禁分支。
        if (account.status() == RiskAccountStatus.BANNED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account banned by risk control");
        }
        if (account.status() == RiskAccountStatus.FROZEN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account frozen by risk control");
        }
    }

    private String normalizeHandle(String handle) {
        return handle == null ? "" : handle.trim().replaceFirst("^@+", "").toLowerCase(Locale.ROOT);
    }
}
