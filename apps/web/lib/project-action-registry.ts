import type {WorkbenchItem} from "@/lib/api";

export type ProjectActionTone = "delivery" | "review" | "approval" | "maintenance" | "feedback" | "default";

export type ProjectActionUi = {
    label: string;
    group: string;
    tone: ProjectActionTone;
    tabId: string;
};

const ACTION_BY_KEY: Record<string, ProjectActionUi> = {
    "claim-task": {label: "领取任务", group: "Project delivery", tone: "delivery", tabId: "tasks"},
    "submit-proof": {label: "提交成果", group: "Project delivery", tone: "delivery", tabId: "myTasks"},
    "review-proof": {label: "验证成果", group: "Project review", tone: "review", tabId: "myTasks"},
    "create-validation-launch": {label: "创建验证轮次", group: "Project validation", tone: "review", tabId: "myTasks"},
    "publish-validation-launch": {label: "发布验证轮次", group: "Project validation", tone: "review", tabId: "myTasks"},
    "create-validation-task": {label: "创建验证任务", group: "Project validation", tone: "review", tabId: "myTasks"},
    "claim-validation-task": {label: "领取验证任务", group: "Project validation", tone: "delivery", tabId: "myTasks"},
    "submit-validation-proof": {label: "提交验证证据", group: "Project validation", tone: "delivery", tabId: "myTasks"},
    "review-validation-proof": {label: "复核验证证据", group: "Project validation", tone: "review", tabId: "myTasks"},
    "create-feedback": {label: "提交反馈", group: "Project feedback", tone: "feedback", tabId: "myTasks"},
    "resolve-feedback": {label: "处理反馈", group: "Project feedback", tone: "feedback", tabId: "myTasks"},
    "settle-validation-launch": {
        label: "结算验证奖励",
        group: "Project rewards",
        tone: "approval",
        tabId: "governance"
    },
    "approve-share-release": {
        label: "审批虚拟股份发放",
        group: "Project rewards",
        tone: "approval",
        tabId: "governance"
    },
    "bind-channel": {label: "绑定协作来源", group: "Project maintenance", tone: "maintenance", tabId: "memory"},
    "archive-discussion": {label: "归档外部讨论", group: "Project maintenance", tone: "maintenance", tabId: "memory"},
};

const CARD_TYPE_UI: Record<string, ProjectActionUi> = {
    submit_pack: {label: "提交候选结果", group: "Agent Protocol", tone: "delivery", tabId: "agentProtocol"},
    revise_pack: {label: "补充结果包", group: "Agent Protocol", tone: "delivery", tabId: "agentProtocol"},
    score_review: {label: "发起评分", group: "Agent Protocol", tone: "review", tabId: "agentProtocol"},
    result_review: {label: "验证结果包", group: "Agent Protocol", tone: "review", tabId: "agentProtocol"},
    final_review: {label: "通过最终复核", group: "Agent Protocol", tone: "approval", tabId: "agentProtocol"},
    challenge_pack: {label: "要求补充材料", group: "Agent Protocol", tone: "feedback", tabId: "agentProtocol"},
    support_candidate: {label: "支持候选 PR", group: "Agent Protocol", tone: "review", tabId: "agentProtocol"},
    final_review_candidate: {
        label: "最终复核候选 PR",
        group: "Agent Protocol",
        tone: "approval",
        tabId: "agentProtocol"
    },
    skip_candidate: {label: "跳过候选项", group: "Agent Protocol", tone: "maintenance", tabId: "agentProtocol"},
};

export function projectActionUi(actionKey: string | undefined): ProjectActionUi {
    if (!actionKey) return fallbackActionUi();
    return ACTION_BY_KEY[actionKey] ?? CARD_TYPE_UI[actionKey] ?? fallbackActionUi(actionKey);
}

export function projectWorkbenchGroup(item: WorkbenchItem): ProjectActionUi {
    const reason = String(item.reason ?? "");
    const lane = String(item.lane ?? "");
    const actionId = item.actions?.find((action) => action.id !== "open" && action.id !== "dismiss")?.id;
    if (reason.includes("share_release") || lane === "settlement") {
        return ACTION_BY_KEY["approve-share-release"];
    }
    if (reason.includes("feedback")) {
        return ACTION_BY_KEY["resolve-feedback"];
    }
    if (reason.includes("proof") || reason.includes("delivery") || lane === "fulfiller" || lane === "worker") {
        return ACTION_BY_KEY["submit-proof"];
    }
    if (reason.includes("review") || reason.includes("accept") || lane === "reviewer" || lane === "review" || lane === "payer") {
        return ACTION_BY_KEY["review-proof"];
    }
    if (reason.includes("project") || lane === "ecosystem" || lane === "authority") {
        return ACTION_BY_KEY[actionId ?? "create-validation-task"] ?? ACTION_BY_KEY["create-validation-task"];
    }
    return fallbackActionUi();
}

function fallbackActionUi(label = "Project action"): ProjectActionUi {
    return {label, group: "General workbench", tone: "default", tabId: "tasks"};
}
