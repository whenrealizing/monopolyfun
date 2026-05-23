package com.monopolyfun.modules.organization.service;

import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.project.domain.ProjectRoleCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

final class ProjectRoleCapabilityPolicy {
    private static final Map<ProjectRoleCode, Set<ProjectCapability>> CAPABILITIES_BY_ROLE =
            new EnumMap<>(ProjectRoleCode.class);

    static {
        CAPABILITIES_BY_ROLE.put(ProjectRoleCode.SYSTEM_CTO, EnumSet.of(
                ProjectCapability.PROJECT_PARTICIPATE,
                ProjectCapability.ORDER_REVIEW,
                ProjectCapability.ORDER_DISPUTE_RESOLVE,
                ProjectCapability.PROJECT_TECH_MANAGE,
                ProjectCapability.RUNTIME_MANAGE,
                ProjectCapability.PROOF_TECH_REVIEW,
                ProjectCapability.UPLOAD_REVIEW));
        CAPABILITIES_BY_ROLE.put(ProjectRoleCode.SYSTEM_CFO, EnumSet.of(
                ProjectCapability.PROJECT_PARTICIPATE,
                ProjectCapability.ORDER_REVIEW,
                ProjectCapability.PAYMENT_REVIEW,
                ProjectCapability.PAYMENT_REFUND,
                ProjectCapability.SETTLEMENT_MANAGE,
                ProjectCapability.COMPENSATION_APPROVE,
                ProjectCapability.SECURITY_RISK_VIEW,
                ProjectCapability.BACKOFFICE_VIEW));
        CAPABILITIES_BY_ROLE.put(ProjectRoleCode.SYSTEM_CEO, EnumSet.allOf(ProjectCapability.class));
    }

    private ProjectRoleCapabilityPolicy() {
    }

    static Map<ProjectRoleCode, Set<ProjectCapability>> capabilitiesByRole() {
        return CAPABILITIES_BY_ROLE;
    }
}
