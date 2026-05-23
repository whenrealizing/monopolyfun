import type {ProjectRole} from "@/lib/api";

export const PROJECT_ROLE_CODES = [
    "system_ceo",
    "system_cto",
    "system_cfo",
] as const;

const ROLE_PRIORITY: Record<string, number> = {
    system_ceo: 10,
    system_cto: 20,
    system_cfo: 30,
};

export function projectRoleLabel(roleCode: string, locale = "zh-CN") {
    switch (roleCode) {
        case "system_ceo":
            return locale === "en" ? "Protocol maintainer" : "协议维护";
        case "system_cto":
            return locale === "en" ? "Technical maintainer" : "技术维护";
        case "system_cfo":
            return locale === "en" ? "Settlement maintainer" : "结算维护";
        default:
            return roleCode;
    }
}

export function projectRoleCapabilityLabel(roleCode: string, locale = "zh-CN") {
    switch (roleCode) {
        case "system_ceo":
            return locale === "en" ? "Rules, permissions, and final coordination" : "规则、权限和最终协调";
        case "system_cto":
            return locale === "en" ? "Repository, runtime, PR, and CI maintenance" : "仓库、运行时、PR 和 CI 维护";
        case "system_cfo":
            return locale === "en" ? "Payment and share settlement" : "支付和股份结算";
        default:
            return locale === "en" ? "System maintenance" : "系统维护";
    }
}

export function sortProjectRoles(roles: ProjectRole[]) {
    // 中文注释：Root Project 维护席位按维护责任排序，普通项目页面直接展示贡献事实。
    return [...roles].sort((left, right) => {
        const leftRank = ROLE_PRIORITY[left.roleCode] ?? 999;
        const rightRank = ROLE_PRIORITY[right.roleCode] ?? 999;
        if (leftRank !== rightRank) return leftRank - rightRank;
        return (left.accountId ?? "").localeCompare(right.accountId ?? "");
    });
}

export function projectRoleRoster(roles: ProjectRole[]) {
    const sortedRoles = sortProjectRoles(roles);
    const rolesByCode = new Map<string, ProjectRole[]>();
    for (const role of sortedRoles) {
        const existing = rolesByCode.get(role.roleCode) ?? [];
        rolesByCode.set(role.roleCode, [...existing, role]);
    }

    // 中文注释：Root Project 固定展示维护席位，空席也保留系统维护责任口径。
    return PROJECT_ROLE_CODES.flatMap((roleCode) => {
        const assigned = rolesByCode.get(roleCode);
        return assigned && assigned.length > 0 ? assigned : [{roleCode} as ProjectRole];
    });
}
