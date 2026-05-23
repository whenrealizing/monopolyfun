package com.monopolyfun.modules.identity.service.query;

import com.monopolyfun.modules.identity.domain.IdentityBadgeEntity;
import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.project.domain.ProjectRoleEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class IdentityRoleBadgeFactory {
    private static final Map<ProjectRoleCode, RoleBadgeSpec> ROLE_BADGES = new EnumMap<>(ProjectRoleCode.class);

    static {
        ROLE_BADGES.put(ProjectRoleCode.SYSTEM_CEO, new RoleBadgeSpec("协议维护", "crown", 95));
        ROLE_BADGES.put(ProjectRoleCode.SYSTEM_CTO, new RoleBadgeSpec("技术维护", "crown", 90));
        ROLE_BADGES.put(ProjectRoleCode.SYSTEM_CFO, new RoleBadgeSpec("结算维护", "crown", 85));
    }

    private IdentityRoleBadgeFactory() {
    }

    public static List<IdentityBadgeEntity> build(String accountId, List<ProjectRoleEntity> roles, String systemProjectId) {
        Map<ProjectRoleCode, ProjectRoleEntity> primaryRoles = new EnumMap<>(ProjectRoleCode.class);
        for (ProjectRoleEntity role : roles) {
            // 中文注释：身份页角色徽章只表达系统级身份，子项目职位留在项目详情和权限链内展示。
            if (!role.projectId().equals(systemProjectId)) {
                continue;
            }
            if (!ROLE_BADGES.containsKey(role.roleCode())) {
                continue;
            }
            // 中文注释：同一账号可在多个项目担任同一角色，身份页只展示角色能力本身，避免重复刷屏。
            primaryRoles.merge(role.roleCode(), role, IdentityRoleBadgeFactory::earliestAssignedRole);
        }

        List<IdentityBadgeEntity> badges = new ArrayList<>();
        for (ProjectRoleEntity role : primaryRoles.values()) {
            RoleBadgeSpec spec = ROLE_BADGES.get(role.roleCode());
            Instant issuedAt = role.assignedAt() == null ? role.createdAt() : role.assignedAt();
            badges.add(new IdentityBadgeEntity(
                    "ibadge:%s:role:%s:project_role".formatted(accountId, role.roleCode().code()),
                    accountId,
                    "role",
                    role.roleCode().code(),
                    spec.label(),
                    spec.icon(),
                    null,
                    null,
                    spec.weight(),
                    issuedAt,
                    role.updatedAt()));
        }
        return badges;
    }

    private static ProjectRoleEntity earliestAssignedRole(ProjectRoleEntity left, ProjectRoleEntity right) {
        Instant leftAssignedAt = left.assignedAt() == null ? left.createdAt() : left.assignedAt();
        Instant rightAssignedAt = right.assignedAt() == null ? right.createdAt() : right.assignedAt();
        return leftAssignedAt.isAfter(rightAssignedAt) ? right : left;
    }

    private record RoleBadgeSpec(String label, String icon, int weight) {
    }
}
