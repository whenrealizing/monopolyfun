import {Link} from "@/i18n/navigation";
import {getLocale, getTranslations} from "next-intl/server";

import {GlobalStatePage, MarketHomeButton} from "@/components/global-state-page";
import {buildSurfaceOwnerIdentity, formatSurfaceDateLabel} from "@/components/market-card-primitives";
import {BaseInfoGrid} from "@/components/market-detail-sections";
import {ProjectDetailTabs} from "@/components/project-detail-tabs";
import {PostOwnerControls} from "@/components/post-owner-controls";
import {PostItemWorkspacePanel} from "@/components/post-item-workspace-panel";
import {ProjectDevelopmentPanel} from "@/components/project-development-panel";
import {ProjectContributionLedgerPanel} from "@/components/project-commercialization-panel";
import {ProjectMemoryPanel} from "@/components/project-memory-panel";
import {ProjectAgentProtocolPanel} from "@/components/project-agent-protocol-panel";
import {ProjectValidationPanel} from "@/components/project-validation-panel";
import {ProjectWorkroomPanel} from "@/components/project-workroom-panel";
import {PostKindBadge, PostStatusBadge} from "@/components/status-badge";
import {EmptyState, PageContainer} from "@/components/ui/page-layout";
import {
    getProjectAgentInbox,
    getProjectDashboard,
    getProjectWorkroom,
    listProjectValidationLaunches,
    listProjectValidationProofs,
    listProjectValidationRewards,
    listProjectValidationTasks,
    lookupPublicAccounts,
    type ProjectRole,
    type ValidationLaunch,
    type ValidationProof,
    type ValidationReward,
    type ValidationTask,
} from "@/lib/api";
import {loadAccessiblePageData} from "@/lib/api/page-data";
import {serverRequestOptions} from "@/lib/api/server-request";
import {projectHref} from "@/lib/business-routes";
import {projectActionUi} from "@/lib/project-action-registry";
import {projectRoleCapabilityLabel, projectRoleLabel, projectRoleRoster} from "@/lib/project-roles";

export default async function ProjectDetailPage({params}: { params: Promise<{ projectNo: string }> }) {
    const {projectNo} = await params;
    const t = await getTranslations("ProjectDetail");
    const stateT = await getTranslations("State.forbidden");
    const actionsT = await getTranslations("State.actions");
    const locale = await getLocale();
    // 中文注释：详情页服务端读取 dashboard 时透传 HttpOnly cookie，已登录用户能直接看到治理区，匿名用户保留公开项目视图。
    const dashboardResult = await loadAccessiblePageData(getProjectDashboard(projectNo, await serverRequestOptions()), `/market/projects/${projectNo}`);

    if (dashboardResult.status === "forbidden") {
        return (
            <GlobalStatePage
                kind="forbidden"
                title={stateT("title")}
                description={stateT("description")}
                primaryAction={<MarketHomeButton label={actionsT("home")}/>}
            />
        );
    }

    const dashboard = dashboardResult.data;
    const workspace = dashboard.workspace;
    const {project, shares: sharePool, items} = workspace;
    const {repoBindings, prCiStatus, projectMemory, agentContext, commercialization} = dashboard;
    const isRootProject = project.projectNo === "monopolyfun" || project.id === "project-root";
    const validationLaunches = await listProjectValidationLaunches(project.projectNo).catch(() => []);
    const [validationTaskGroups, validationProofGroups, validationRewards] = await Promise.all([
        Promise.all(validationLaunches.map((launch) => listProjectValidationTasks(project.projectNo, launch.id).catch(() => []))),
        Promise.all(validationLaunches.map((launch) => listProjectValidationProofs(project.projectNo, launch.id).catch(() => []))),
        listProjectValidationRewards(project.projectNo).catch(() => []),
    ]);
    const validationTasks = validationTaskGroups.flat();
    const validationProofs = validationProofGroups.flat();
    const agentInbox = await getProjectAgentInbox(project.projectNo, await serverRequestOptions()).catch(() => null);
    const workroomResult = await getProjectWorkroom(project.projectNo, await serverRequestOptions())
        .then((data) => ({data, failed: false}))
        .catch(() => ({data: null, failed: true}));
    const contributors = buildProjectContributors({
        ownerAccountId: project.ownerHandle,
        launches: validationLaunches,
        tasks: validationTasks,
        proofs: validationProofs,
        rewards: validationRewards,
    });
    // 中文注释：普通项目按协议事实补全参与者身份，Root Project 额外读取维护席位账号。
    const accountIds = uniqueStrings([
        project.ownerHandle,
        ...project.roles.map((role) => role.accountId),
        ...contributors.map((contributor) => contributor.accountId),
        ...(commercialization?.contributors ?? []).map((contributor) => contributor.accountId),
    ]);
    const accounts = await lookupPublicAccounts(accountIds).catch(() => []);
    const accountsById = Object.fromEntries(accounts.flatMap((account) => [[account.id, account], [account.handle.replace(/^@+/, "").toLowerCase(), account]]));
    const owner = buildSurfaceOwnerIdentity(project.ownerHandle, accountsById);
    const contributionAccountsById = Object.fromEntries((commercialization?.contributors ?? []).map((contributor) => {
        const identity = buildSurfaceOwnerIdentity(contributor.accountId, accountsById);
        return [contributor.accountId, {displayName: identity.displayName, handle: identity.handle}];
    }));
    // 中文注释：项目顶部展示第一版任务闭环事实，帮助用户直接判断任务、成果、验证和奖励状态。
    const activeValidationTaskCount = validationTasks.filter((task) => ["open", "claimed", "working", "proof_submitted", "changes_requested"].includes(task.status)).length;
    const submittedValidationProofCount = validationProofs.filter((proof) => proof.status === "submitted").length;
    const acceptedValidationProofCount = validationProofs.filter((proof) => proof.status === "accepted").length;
    const settledValidationRewardCount = validationRewards.filter((reward) => reward.status === "settled").length;
    const publishedLabel = formatSurfaceDateLabel(project.createdAt, locale);
    const roles: ProjectRole[] = isRootProject ? projectRoleRoster(project.roles) : [];
    const projectSummary = firstDistinctText(project.title, project.description, project.summary, project.oneSentence);
    const projectInfoBlocks = [
        {label: t("info.projectBrief"), value: project.goal ?? project.oneSentence},
        {label: t("info.deliverables"), value: project.deliverables},
        {label: t("info.joinGuide"), value: project.joinGuide},
        {label: t("info.ownerIntro"), value: project.ownerIntro},
    ].filter((item) => Boolean(item.value));
    const taskTab = (
        <div id="project-tasks" className="scroll-mt-20">
            <PostItemWorkspacePanel
                postKind="project"
                postId={project.projectNo}
                postStatus={project.status}
                ownerHandle={owner.handle}
                returnTo={projectHref(project)}
                initialItems={items}
            />
        </div>
    );
    const platformGrantsTab = (
        <div className="space-y-3">
            <div className="rounded-[12px] bg-[var(--surface-2)] px-4 py-3">
                <div className="text-sm font-semibold text-[var(--foreground)]">{t("team.title")}</div>
                <p className="mt-1 text-xs leading-5 text-[var(--muted-foreground)]">{t("team.description")}</p>
            </div>
            <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-3">
                {roles.map((role, index) => {
                    const assignee = role.accountId ? accountsById[role.accountId] : undefined;
                    const member = role.accountId ? buildSurfaceOwnerIdentity(role.accountId, accountsById) : null;
                    const roleLabel = projectRoleLabel(role.roleCode, locale);
                    // 中文注释：Root Project 授权只展示平台级系统维护席位，普通项目走开放贡献事实。
                    const memberContent = (
                        <>
                            <div className="flex min-w-0 items-center gap-3">
                <span
                    className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full text-xs font-medium text-white ring-1 ring-[rgba(255,255,255,0.1)]"
                    style={{background: `linear-gradient(135deg, hsl(${member?.hue ?? 220} 76% 48%), hsl(${((member?.hue ?? 220) + 36) % 360} 72% 36%))`}}
                >
                  {member?.avatarUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={member.avatarUrl} alt="" className="h-full w-full object-cover"/>
                  ) : roleLabel.slice(0, 2).toUpperCase()}
                </span>
                                <span className="min-w-0">
                  <span
                      className="block truncate text-sm text-[var(--foreground)]">{assignee?.displayName ?? projectRoleCapabilityLabel(role.roleCode, locale)}</span>
                  <span
                      className="mt-0.5 block truncate text-xs text-[var(--muted-foreground)]">{assignee?.handle ? `@${assignee.handle.replace(/^@+/, "")}` : t("team.unassigned")}</span>
                </span>
                            </div>
                            <span
                                className="shrink-0 rounded-full bg-[var(--surface-control)] px-2.5 py-1 text-[11px] text-[var(--muted-foreground)]">
                {roleLabel}
              </span>
                        </>
                    );
                    return (
                        member?.profileHref ? (
                            <Link
                                key={`${role.roleCode}-${role.accountId ?? index}`}
                                href={member.profileHref}
                                className="flex min-h-16 items-center justify-between gap-3 rounded-[12px] bg-[var(--background)] px-3 py-3 transition hover:bg-[var(--surface-control)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]"
                            >
                                {memberContent}
                            </Link>
                        ) : (
                            <div key={`${role.roleCode}-${role.accountId ?? index}`}
                                 className="flex min-h-16 items-center justify-between gap-3 rounded-[12px] bg-[var(--background)] px-3 py-3">
                                {memberContent}
                            </div>
                        )
                    );
                })}
            </div>
        </div>
    );
    const contributorsTab = (
        <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-3">
            {contributors.map((contributor) => {
                const identity = buildSurfaceOwnerIdentity(contributor.accountId, accountsById);
                const contributionCount = contributor.launchCount + contributor.taskCount + contributor.proofCount + contributor.rewardCount;
                const content = (
                    <>
                        <div className="flex min-w-0 items-center gap-3">
              <span
                  className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full text-xs font-medium text-white ring-1 ring-[rgba(255,255,255,0.1)]"
                  style={{background: `linear-gradient(135deg, hsl(${identity.hue} 76% 48%), hsl(${(identity.hue + 36) % 360} 72% 36%))`}}
              >
                {identity.avatarUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={identity.avatarUrl} alt="" className="h-full w-full object-cover"/>
                ) : identity.initials}
              </span>
                            <span className="min-w-0">
                <span className="block truncate text-sm text-[var(--foreground)]">{identity.displayName}</span>
                <span className="mt-0.5 block truncate text-xs text-[var(--muted-foreground)]">{identity.handle}</span>
              </span>
                        </div>
                        <div className="grid grid-cols-4 gap-1 text-center text-[11px] text-[var(--muted-foreground)]">
                            <ContributorMetric label={t("contributorMetrics.tasks")} value={contributor.taskCount}/>
                            <ContributorMetric label={t("contributorMetrics.results")} value={contributor.proofCount}/>
                            <ContributorMetric label={t("contributorMetrics.rewards")} value={contributor.rewardCount}/>
                            <ContributorMetric label={t("contributorMetrics.score")}
                                               value={Math.round(contributor.score)}/>
                        </div>
                        <div className="flex flex-wrap gap-1.5">
                            {contributor.tags.map((tag) => (
                                <span key={tag}
                                      className="rounded-full bg-[var(--surface-control)] px-2 py-0.5 text-[11px] text-[var(--muted-foreground)]">
                  {tag}
                </span>
                            ))}
                            {contributionCount === 0 && !contributor.tags.includes("发起人") ? (
                                <span
                                    className="rounded-full bg-[var(--surface-control)] px-2 py-0.5 text-[11px] text-[var(--muted-foreground)]">发起人</span>
                            ) : null}
                        </div>
                    </>
                );
                return identity.profileHref ? (
                    <Link
                        key={contributor.accountId}
                        href={identity.profileHref}
                        className="grid min-h-28 gap-3 rounded-[12px] bg-[var(--background)] p-3 transition hover:bg-[var(--surface-control)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]"
                    >
                        {content}
                    </Link>
                ) : (
                    <div key={contributor.accountId}
                         className="grid min-h-28 gap-3 rounded-[12px] bg-[var(--background)] p-3">
                        {content}
                    </div>
                );
            })}
        </div>
    );
    const peopleTab = isRootProject ? platformGrantsTab : contributorsTab;
    const myTasksTab = (
        <div className="space-y-4">
            <ProjectValidationPanel projectNo={project.projectNo}/>
        </div>
    );
    const workroomTab = (
        <ProjectWorkroomPanel projectNo={project.projectNo} initialOverview={workroomResult.data} initialLoadFailed={workroomResult.failed}/>
    );
    const virtualSharesTab = (
        <div className="space-y-4">
            <div className="rounded-[12px] bg-[var(--surface-2)] px-4 py-3">
                <div className="text-sm font-semibold text-[var(--foreground)]">{t("governance.title")}</div>
                <p className="mt-1 text-xs leading-5 text-[var(--muted-foreground)]">{t("governance.description")}</p>
            </div>
            <ProjectContributionLedgerPanel
                commercialization={commercialization ?? null}
                accountsById={contributionAccountsById}
                labels={{
                    title: t("commercialization.title"),
                    description: t("commercialization.description"),
                    ledgerTitle: t("commercialization.ledgerTitle"),
                    contributorsTitle: t("commercialization.contributorsTitle"),
                    empty: t("commercialization.empty"),
                    status: {
                        planned: t("commercialization.status.planned"),
                        active: t("commercialization.status.active"),
                        proving: t("commercialization.status.proving"),
                        validated: t("commercialization.status.validated"),
                        collecting: t("commercialization.status.collecting"),
                        ready: t("commercialization.status.ready"),
                    },
                    metrics: {
                        tasks: t("commercialization.metrics.tasks"),
                        results: t("commercialization.metrics.results"),
                        accepted: t("commercialization.metrics.accepted"),
                        virtualSharePool: t("commercialization.metrics.virtualSharePool"),
                        claimed: t("commercialization.metrics.claimed"),
                        virtualShares: t("commercialization.metrics.virtualShares"),
                        contributors: t("commercialization.metrics.contributors"),
                        ledgerEntries: t("commercialization.metrics.ledgerEntries"),
                        weight: t("commercialization.metrics.weight"),
                    },
                    sourceTypes: {
                        order: t("commercialization.sourceTypes.order"),
                        work_thread: t("commercialization.sourceTypes.workThread"),
                        validation_reward: t("commercialization.sourceTypes.validationReward"),
                    },
                    roles: {
                        assignee: t("commercialization.roles.assignee"),
                        proof_submitter: t("commercialization.roles.proofSubmitter"),
                        proof_validator: t("commercialization.roles.proofValidator"),
                        order_assignee: t("commercialization.roles.orderAssignee"),
                        work_order: t("commercialization.roles.workOrder"),
                        review_order: t("commercialization.roles.reviewOrder"),
                    },
                }}
            />
            {sharePool ? (
                <BaseInfoGrid
                    items={[
                        {label: t("baseInfo.totalShares"), value: (sharePool.shareTotal ?? 0).toLocaleString()},
                        {label: t("baseInfo.releasedShares"), value: (sharePool.shareMinted ?? 0).toLocaleString()},
                        {
                            label: t("baseInfo.taskPoolRemaining"),
                            value: t("sharesAmount", {count: (sharePool.taskRemaining ?? 0).toLocaleString()})
                        },
                        {label: t("baseInfo.reservePool"), value: (sharePool.reserveBudget ?? 0).toLocaleString()},
                    ]}
                />
            ) : (
                <EmptyState compact title={t("sharesUnavailable")} description={t("sharesUnavailableDescription")}/>
            )}
            {isRootProject ? null : (
                <div className="space-y-3">
                    <div className="rounded-[12px] bg-[var(--surface-2)] px-4 py-3">
                        <div
                            className="text-sm font-semibold text-[var(--foreground)]">{t("contributionRecords.title")}</div>
                        <p className="mt-1 text-xs leading-5 text-[var(--muted-foreground)]">{t("contributionRecords.description")}</p>
                    </div>
                    {contributorsTab}
                </div>
            )}
        </div>
    );
    const memoryTab = (
        <ProjectMemoryPanel projectNo={project.projectNo} overview={projectMemory ?? null}
                            agentContext={agentContext ?? null}/>
    );
    const agentProtocolTab = (
        <ProjectAgentProtocolPanel projectNo={project.projectNo} initialInbox={agentInbox}/>
    );
    const infoTab = (
        <div className="space-y-4">
            {projectInfoBlocks.length > 0 ? (
                <div className="grid gap-3 lg:grid-cols-2">
                    {projectInfoBlocks.map((item) => (
                        <ProjectInfoBlock key={item.label} label={item.label} value={item.value ?? ""}/>
                    ))}
                </div>
            ) : (
                <EmptyState compact title={t("info.empty")}/>
            )}
            <BaseInfoGrid
                items={[
                    {label: t("baseInfo.publishedAt"), value: publishedLabel},
                ]}
            />
            {project.referenceLinks?.length ? (
                <ProjectLinksBlock title={t("info.referenceLinks")} links={project.referenceLinks}/>
            ) : null}
            <ProjectDevelopmentPanel repoBindings={repoBindings ?? []} prCiStatus={prCiStatus ?? null}/>
        </div>
    );
    const tabs = [
        {id: "workroom", label: "Workroom", content: workroomTab},
        {id: "tasks", label: t("tabs.tasks"), content: taskTab},
        {id: "myTasks", label: t("tabs.myTasks"), content: myTasksTab},
        {id: "governance", label: t("tabs.governance"), content: virtualSharesTab},
        {id: "agentProtocol", label: "Agent Protocol", content: agentProtocolTab},
        {id: "memory", label: t("tabs.memory"), content: memoryTab},
        ...(isRootProject ? [{id: "members", label: t("tabs.platformGrants"), content: peopleTab}] : []),
        {id: "info", label: t("tabs.info"), content: infoTab},
    ];

    return (
        <PageContainer width="full" className="space-y-4 pb-16">
            <section className="space-y-5 bg-[var(--background)] px-1 py-3 sm:px-0">
                <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
                    <div className="min-w-0 space-y-4">
                        <div className="flex flex-wrap gap-2">
                            <PostKindBadge kind="project"/>
                            <PostStatusBadge status={project.status}/>
                        </div>
                        <div className="space-y-3">
                            <h1 className="max-w-4xl text-[32px] font-normal leading-tight text-[var(--foreground)]">
                                {project.title}
                            </h1>
                            {projectSummary ? (
                                <p className="max-w-3xl whitespace-pre-line text-sm leading-7 text-[var(--muted-foreground)]">
                                    {projectSummary}
                                </p>
                            ) : null}
                        </div>
                    </div>

                    <ProjectOwnerPanel
                        title={t("publisher.title")}
                        name={owner.displayName}
                        handle={owner.handle}
                        initials={owner.initials}
                        hue={owner.hue}
                        avatarUrl={owner.avatarUrl}
                        profileHref={owner.profileHref}
                    />
                </div>

                <div className="grid gap-2 sm:grid-cols-4">
                    <ProjectMetric label={t("validationMetrics.activeTasks")}
                                   value={String(activeValidationTaskCount)}/>
                    <ProjectMetric label={t("validationMetrics.pendingReviews")}
                                   value={String(submittedValidationProofCount)}/>
                    <ProjectMetric label={t("validationMetrics.acceptedProofs")}
                                   value={String(acceptedValidationProofCount)}/>
                    <ProjectMetric label={t("validationMetrics.settledRewards")}
                                   value={String(settledValidationRewardCount)}/>
                </div>

                <ProjectNextActionStrip action={resolveProjectNextAction({
                    items,
                    agentInboxCardCount: agentInbox?.cards.length ?? 0,
                    activeValidationTaskCount,
                    submittedValidationProofCount,
                    acceptedValidationProofCount,
                })}/>

                <div className="flex justify-end">
                    <PostOwnerControls post={{...project, kind: "project"}}/>
                </div>
            </section>

            <ProjectDetailTabs tabs={tabs}/>
        </PageContainer>
    );
}

type ProjectNextAction = {
    actionKey: string;
    title: string;
    description: string;
    tabLabel: string;
};

function resolveProjectNextAction(input: {
    items: Array<{ status?: string | null; activeOrderDisplayPhase?: string | null; activeOrderNo?: string | null }>;
    agentInboxCardCount: number;
    activeValidationTaskCount: number;
    submittedValidationProofCount: number;
    acceptedValidationProofCount: number;
}): ProjectNextAction {
    const activeItem = input.items.find((item) => item.activeOrderNo) ?? input.items[0];
    const status = String(activeItem?.status ?? "");
    const phase = String(activeItem?.activeOrderDisplayPhase ?? "");
    if (status === "open") {
        return {
            actionKey: "claim-task",
            title: "当前可领取公开任务",
            description: "Project 任务池还有开放 item，交付方可以直接领取并开始提交结果。",
            tabLabel: "任务"
        };
    }
    if (["in_progress", "claimed"].includes(status) || phase === "delivery_result_due") {
        return {
            actionKey: "submit-proof",
            title: "等待交付结果",
            description: "当前 active order 已生成，交付方需要提交 proof、链接或阶段说明。",
            tabLabel: "我的任务"
        };
    }
    if (["in_review", "submitted"].includes(status) || ["waiting_lead_acceptance", "review_due"].includes(phase)) {
        return {
            actionKey: "review-proof",
            title: "等待 owner 验收",
            description: "proof 已提交，项目 owner 需要确认、退回补充或进入风险复核。",
            tabLabel: "我的任务"
        };
    }
    if (["accepted", "accepted_window_open", "closed"].includes(status) || phase === "accepted_window_open") {
        return {
            actionKey: "approve-share-release",
            title: "查看虚拟股份释放",
            description: "任务已验收，关注 shares release、争议窗口和最终发放状态。",
            tabLabel: "虚拟股份"
        };
    }
    if (input.submittedValidationProofCount > input.acceptedValidationProofCount) {
        return {
            actionKey: "review-validation-proof",
            title: "有验证成果待确认",
            description: "Validation proof 队列里存在待验证结果。",
            tabLabel: "我的任务"
        };
    }
    if (input.activeValidationTaskCount > 0) {
        return {
            actionKey: "claim-validation-task",
            title: "推进验证任务",
            description: "当前项目存在进行中的验证任务，优先领取、提交证据和处理反馈。",
            tabLabel: "我的任务"
        };
    }
    if (input.agentInboxCardCount > 0) {
        return {
            actionKey: "create-feedback",
            title: "处理 Agent action",
            description: "Agent Protocol 有候选动作可处理，进入业务 action 卡继续推进。",
            tabLabel: "Agent Protocol"
        };
    }
    return {
        actionKey: "create-validation-launch",
        title: "创建下一轮验证",
        description: "当前没有紧急待办，可以创建新的验证轮次或补充任务。",
        tabLabel: "我的任务"
    };
}

function ProjectNextActionStrip({action}: { action: ProjectNextAction }) {
    const ui = projectActionUi(action.actionKey);
    return (
        <div
            className="grid gap-3 border-y border-[var(--border)] py-3 md:grid-cols-[minmax(0,1fr)_220px] md:items-center">
            <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                    <span
                        className="rounded-full bg-[var(--surface-control)] px-2.5 py-1 text-[11px] font-semibold text-[var(--muted-foreground)]">{ui.group}</span>
                    <span
                        className="rounded-full bg-[rgba(72,108,230,0.12)] px-2.5 py-1 text-[11px] font-semibold text-[var(--accent-blue)]">{ui.label}</span>
                </div>
                <div className="mt-2 text-base font-semibold text-[var(--foreground)]">{action.title}</div>
                <div className="mt-1 text-sm leading-6 text-[var(--muted-foreground)]">{action.description}</div>
            </div>
            <div className="rounded-[8px] bg-[var(--surface-2)] px-3 py-2 text-sm text-[var(--foreground)]">
                <span className="block text-[11px] font-semibold text-[var(--muted-foreground)]">对应入口</span>
                <span className="mt-1 block font-semibold">{action.tabLabel}</span>
            </div>
        </div>
    );
}

type ProjectContributor = {
    accountId: string;
    launchCount: number;
    taskCount: number;
    proofCount: number;
    rewardCount: number;
    settledRewardCount: number;
    score: number;
    lastAt?: string | null;
    tags: string[];
};

function buildProjectContributors({
                                      ownerAccountId,
                                      launches,
                                      tasks,
                                      proofs,
                                      rewards,
                                  }: {
    ownerAccountId?: string | null;
    launches: ValidationLaunch[];
    tasks: ValidationTask[];
    proofs: ValidationProof[];
    rewards: ValidationReward[];
}) {
    const contributors = new Map<string, ProjectContributor>();
    const touch = (accountId: string | null | undefined, patch: Partial<ProjectContributor>, tag: string, lastAt?: string | null) => {
        if (!accountId) return;
        const current = contributors.get(accountId) ?? {
            accountId,
            launchCount: 0,
            taskCount: 0,
            proofCount: 0,
            rewardCount: 0,
            settledRewardCount: 0,
            score: 0,
            lastAt: null,
            tags: [],
        };
        const nextTags = current.tags.includes(tag) ? current.tags : [...current.tags, tag];
        const nextLastAt = latestIso(current.lastAt, lastAt);
        contributors.set(accountId, {...current, ...patch, tags: nextTags, lastAt: nextLastAt});
    };

    touch(ownerAccountId, {score: 1}, "发起人");
    for (const launch of launches) {
        touch(launch.createdByAccountId, {
            launchCount: (contributors.get(launch.createdByAccountId)?.launchCount ?? 0) + 1,
            score: (contributors.get(launch.createdByAccountId)?.score ?? 0) + 3
        }, "创建任务列表", launch.createdAt);
        touch(launch.publishedByAccountId, {score: (contributors.get(launch.publishedByAccountId ?? "")?.score ?? 0) + 1}, "发布任务列表", launch.publishedAt);
        touch(launch.settledByAccountId, {score: (contributors.get(launch.settledByAccountId ?? "")?.score ?? 0) + 1}, "结算奖励", launch.settledAt);
    }
    for (const task of tasks) {
        touch(task.createdByAccountId, {
            taskCount: (contributors.get(task.createdByAccountId)?.taskCount ?? 0) + 1,
            score: (contributors.get(task.createdByAccountId)?.score ?? 0) + 2
        }, "创建任务", task.createdAt);
        touch(task.claimedByAccountId, {
            taskCount: (contributors.get(task.claimedByAccountId ?? "")?.taskCount ?? 0) + 1,
            score: (contributors.get(task.claimedByAccountId ?? "")?.score ?? 0) + 2
        }, "接任务", task.claimedAt);
    }
    for (const proof of proofs) {
        touch(proof.submittedByAccountId, {
            proofCount: (contributors.get(proof.submittedByAccountId)?.proofCount ?? 0) + 1,
            score: (contributors.get(proof.submittedByAccountId)?.score ?? 0) + 4
        }, "提交成果", proof.createdAt);
    }
    for (const reward of rewards) {
        touch(reward.recipientAccountId, {
            rewardCount: (contributors.get(reward.recipientAccountId ?? "")?.rewardCount ?? 0) + 1,
            settledRewardCount: (contributors.get(reward.recipientAccountId ?? "")?.settledRewardCount ?? 0) + (reward.status === "settled" ? 1 : 0),
            score: (contributors.get(reward.recipientAccountId ?? "")?.score ?? 0) + Number(reward.contributionWeight ?? 0),
        }, reward.status === "settled" ? "已结算" : "待结算", reward.updatedAt ?? reward.createdAt);
    }

    // 中文注释：贡献者列表从协议事实推导，排序优先看参与强度，再看最近一次贡献时间。
    return [...contributors.values()].sort((left, right) => {
        if (right.score !== left.score) return right.score - left.score;
        return Date.parse(right.lastAt ?? "0") - Date.parse(left.lastAt ?? "0");
    });
}

function latestIso(left?: string | null, right?: string | null) {
    if (!left) return right ?? null;
    if (!right) return left;
    return Date.parse(right) > Date.parse(left) ? right : left;
}

function uniqueStrings(values: Array<string | null | undefined>) {
    return [...new Set(values.filter((value): value is string => Boolean(value)))];
}

function ContributorMetric({label, value}: { label: string; value: number }) {
    return (
        <span className="rounded-[8px] bg-[var(--surface-control)] px-2 py-1">
      <span className="block text-[10px]">{label}</span>
      <span className="mt-0.5 block text-xs font-semibold text-[var(--foreground)]">{value}</span>
    </span>
    );
}

function ProjectMetric({label, value}: { label: string; value: string }) {
    return (
        <div className="bg-[var(--background)] px-3 py-2.5">
            <div className="text-[11px] font-medium text-[var(--muted-foreground)]">{label}</div>
            <div className="mt-2 truncate text-lg font-medium text-[var(--foreground)]">{value}</div>
        </div>
    );
}

function ProjectInfoBlock({label, value}: { label: string; value: string }) {
    return (
        <div className="rounded-[12px] bg-[var(--background)] p-4">
            <div className="text-[11px] font-medium text-[var(--muted-foreground)]">{label}</div>
            <p className="mt-2 whitespace-pre-line text-sm leading-7 text-[var(--foreground)]/88">{value}</p>
        </div>
    );
}

function ProjectLinksBlock({title, links}: { title: string; links: string[] }) {
    return (
        <div className="rounded-[12px] bg-[var(--background)] p-4">
            <div className="text-[11px] font-medium text-[var(--muted-foreground)]">{title}</div>
            <div className="mt-3 grid gap-2">
                {links.map((link) => (
                    <a
                        key={link}
                        href={link}
                        target="_blank"
                        rel="noreferrer"
                        className="break-all text-sm leading-6 text-[var(--accent-blue)] transition hover:text-[var(--foreground)] hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]"
                    >
                        {link}
                    </a>
                ))}
            </div>
        </div>
    );
}

function ProjectOwnerPanel(props: {
    title: string;
    name: string;
    handle: string;
    initials: string;
    hue: number;
    avatarUrl?: string | null;
    profileHref?: string | null;
}) {
    return (
        <aside className="space-y-4 rounded-[12px] bg-[var(--background)] p-4">
            <ProjectOwnerCard {...props} />
        </aside>
    );
}

function firstDistinctText(...values: Array<string | null | undefined>) {
    const [base, ...candidates] = values;
    const normalizedBase = normalizeText(base);
    return candidates.find((value) => {
        const normalizedValue = normalizeText(value);
        return normalizedValue && normalizedValue !== normalizedBase;
    });
}

function normalizeText(value: string | null | undefined) {
    return value?.replace(/\s+/g, "").trim();
}

function ProjectOwnerCard({
                              title,
                              name,
                              handle,
                              initials,
                              hue,
                              avatarUrl,
                              profileHref,
                          }: {
    title: string;
    name: string;
    handle: string;
    initials: string;
    hue: number;
    avatarUrl?: string | null;
    profileHref?: string | null;
}) {
    const content = (
        <>
            <div className="text-[11px] font-medium text-[var(--muted-foreground)]">{title}</div>
            <div className="mt-2 flex items-center gap-3">
        <span
            className="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-full text-sm font-medium text-white ring-1 ring-[rgba(255,255,255,0.12)]"
            style={{background: `linear-gradient(135deg, hsl(${hue} 76% 48%), hsl(${(hue + 36) % 360} 72% 36%))`}}
        >
          {avatarUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={avatarUrl} alt="" className="h-full w-full object-cover"/>
          ) : initials}
        </span>
                <span className="min-w-0 flex-1">
          <span className="block truncate text-sm text-[var(--foreground)]">{name}</span>
          <span className="mt-1 block truncate text-xs text-[var(--muted-foreground)]">{handle}</span>
        </span>
            </div>
        </>
    );

    if (profileHref) {
        return (
            <Link
                href={profileHref}
                className="block transition hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]"
            >
                {content}
            </Link>
        );
    }

    return (
        <div>
            {content}
        </div>
    );
}
