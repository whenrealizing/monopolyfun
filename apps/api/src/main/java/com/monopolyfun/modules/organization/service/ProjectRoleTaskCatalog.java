package com.monopolyfun.modules.organization.service;

import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.project.domain.ProjectRoleCode;

import java.util.List;

final class ProjectRoleTaskCatalog {
    private ProjectRoleTaskCatalog() {
    }

    static RoleTaskSpec spec(ProjectRoleCode roleCode) {
        return switch (roleCode) {
            case SYSTEM_CEO -> new RoleTaskSpec(
                    "制定 %s 的本周治理路线",
                    "梳理项目目标、角色空缺、关键任务和审批风险，给出下一轮推进顺序。",
                    "governance_plan",
                    ProjectCapability.ROLE_ASSIGN,
                    List.of("列出本周最重要的 3 个项目目标", "确认角色空缺和审批责任", "给出下一步任务分配建议"));
            case SYSTEM_CTO -> new RoleTaskSpec(
                    "建立 %s 的技术交付检查",
                    "检查仓库 PR、CI、workflow、release 和 runtime 风险，输出技术推进清单。",
                    "technical_delivery",
                    ProjectCapability.PROJECT_TECH_MANAGE,
                    List.of("检查 repo / PR / workflow 状态", "指出阻塞技术交付的风险", "给出可验收的技术任务建议"));
            case SYSTEM_CFO -> new RoleTaskSpec(
                    "检查 %s 的 shares 和结算风险",
                    "检查 shares 发放、结算和预算状态，输出财务治理建议。",
                    "finance_control",
                    ProjectCapability.COMPENSATION_APPROVE,
                    List.of("读取 shares 和结算状态", "指出待审批或预算风险", "给出下一次发放或冻结建议"));
        };
    }

    record RoleTaskSpec(
            String title,
            String goal,
            String taskKind,
            ProjectCapability primaryCapability,
            List<String> acceptanceCriteria
    ) {
    }
}
