package com.monopolyfun.modules.identity.domain;

import com.monopolyfun.modules.risk.domain.RiskAccountStatus;
import com.monopolyfun.modules.risk.domain.RiskLevel;

import java.time.Instant;
import java.util.Map;

public record AccountEntity(
        String id,
        String handle,
        String displayName,
        String passwordHash,
        RiskAccountStatus status,
        RiskLevel riskLevel,
        Instant frozenUntil,
        String riskReason,
        Instant riskUpdatedAt,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public AccountEntity {
        // 中文注释：账号风险状态在实体层补默认值，历史账号读出后也会进入统一处置模型。
        status = status == null ? RiskAccountStatus.ACTIVE : status;
        riskLevel = riskLevel == null ? RiskLevel.NORMAL : riskLevel;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public AccountEntity withPasswordHash(String nextPasswordHash, Instant updatedAt) {
        return new AccountEntity(
                id,
                handle,
                displayName,
                nextPasswordHash,
                status,
                riskLevel,
                frozenUntil,
                riskReason,
                riskUpdatedAt,
                metadata,
                createdAt,
                updatedAt);
    }

    public AccountEntity withRiskState(
            RiskAccountStatus nextStatus,
            RiskLevel nextRiskLevel,
            Instant nextFrozenUntil,
            String nextRiskReason,
            Instant now) {
        return new AccountEntity(
                id,
                handle,
                displayName,
                passwordHash,
                nextStatus,
                nextRiskLevel,
                nextFrozenUntil,
                nextRiskReason,
                now,
                metadata,
                createdAt,
                now);
    }
}
