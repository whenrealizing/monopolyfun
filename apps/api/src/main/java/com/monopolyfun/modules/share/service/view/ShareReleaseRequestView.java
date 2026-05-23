package com.monopolyfun.modules.share.service.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.share.domain.ShareIssuerType;
import com.monopolyfun.modules.share.domain.ShareReleaseRequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ShareReleaseRequestView(
        String id,
        ShareIssuerType issuerType,
        String issuerId,
        String marketId,
        String projectId,
        String orderId,
        String proofId,
        String accountId,
        int amount,
        int curveSlot,
        ShareReleaseRequestStatus status,
        List<ProjectRoleCode> requiredRoleCodes,
        List<ProjectRoleCode> approvedRoleCodes,
        List<ProjectRoleCode> skippedRoleCodes,
        String requestedByAccountId,
        Instant resolvedAt,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String resourceKey,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<String> capabilities,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Map<String, Object>> blockedCapabilities
) {
    public ShareReleaseRequestView(
            String id,
            ShareIssuerType issuerType,
            String issuerId,
            String marketId,
            String projectId,
            String orderId,
            String proofId,
            String accountId,
            int amount,
            int curveSlot,
            ShareReleaseRequestStatus status,
            List<ProjectRoleCode> requiredRoleCodes,
            List<ProjectRoleCode> approvedRoleCodes,
            List<ProjectRoleCode> skippedRoleCodes,
            String requestedByAccountId,
            Instant resolvedAt,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                issuerType,
                issuerId,
                marketId,
                projectId,
                orderId,
                proofId,
                accountId,
                amount,
                curveSlot,
                status,
                requiredRoleCodes,
                approvedRoleCodes,
                skippedRoleCodes,
                requestedByAccountId,
                resolvedAt,
                metadata,
                createdAt,
                updatedAt,
                null,
                List.of(),
                List.of());
    }

    public ShareReleaseRequestView {
        requiredRoleCodes = requiredRoleCodes == null ? List.of() : List.copyOf(requiredRoleCodes);
        approvedRoleCodes = approvedRoleCodes == null ? List.of() : List.copyOf(approvedRoleCodes);
        skippedRoleCodes = skippedRoleCodes == null ? List.of() : List.copyOf(skippedRoleCodes);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        blockedCapabilities = blockedCapabilities == null ? List.of() : List.copyOf(blockedCapabilities);
    }

    public ShareReleaseRequestView withAgentState(
            String resourceKey,
            List<String> capabilities,
            List<Map<String, Object>> blockedCapabilities) {
        return new ShareReleaseRequestView(
                id,
                issuerType,
                issuerId,
                marketId,
                projectId,
                orderId,
                proofId,
                accountId,
                amount,
                curveSlot,
                status,
                requiredRoleCodes,
                approvedRoleCodes,
                skippedRoleCodes,
                requestedByAccountId,
                resolvedAt,
                metadata,
                createdAt,
                updatedAt,
                resourceKey,
                capabilities,
                blockedCapabilities);
    }
}
