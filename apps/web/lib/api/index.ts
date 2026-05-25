import {monopolyfunFetch, requireClientSession, throwApiRequestError,} from "@/lib/api-runtime";
import {UiError} from "@/lib/error-messages";
import * as Api from "@/lib/generated/api/monopolyfun";
import type * as Gen from "@/lib/generated/api/model";
import {sha256} from "@noble/hashes/sha2";

export {ApiRequestError} from "@/lib/api-error";

type JsonRecord = Record<string, unknown>;
export type PageInfo = {
    limit: number;
    nextCursor?: string | null;
    hasMore: boolean;
};
export type PageResult<T> = {
    items: T[];
    pageInfo: PageInfo;
};
export type UploadProgress = {
    loaded: number;
    total: number;
    percent: number;
};
export type UploadOrderArtifactOptions = Parameters<typeof createUploadTicket>[2] & {
    signal?: AbortSignal;
    onProgress?: (progress: UploadProgress) => void;
};
type Contract<T> = T extends Array<infer Item>
    ? Contract<Item>[]
    : T extends object
        ? { [Key in keyof T]-?: Contract<NonNullable<T[Key]>> }
        : T;
type Override<T, U> = Omit<T, keyof U> & U;

function contract<T>(value: T): Contract<T> {
    return value as Contract<T>;
}

function contractList<T>(value: T[]): Contract<T>[] {
    return value as Contract<T>[];
}

function pageItems<T>(value: PageResult<T> | T[]): T[] {
    return Array.isArray(value) ? value : array(value.items);
}

function contractPage<T, U>(page: PageResult<T>, items: U[]): PageResult<U> {
    // 中文注释：列表 API 保留后端 pageInfo，UI 层基于同一 cursor 契约做翻页。
    return {
        items,
        pageInfo: page.pageInfo,
    };
}

function pageParams(options?: { limit?: number; cursor?: string }) {
    return {
        limit: options?.limit,
        cursor: options?.cursor,
    };
}

function lower(value: unknown) {
    return typeof value === "string" ? value.toLowerCase() : "";
}

function record(value: unknown): JsonRecord {
    return value && typeof value === "object" && !Array.isArray(value) ? value as JsonRecord : {};
}

function array<T>(value: T[] | undefined | null): T[] {
    return Array.isArray(value) ? value : [];
}

function stringArray(value: unknown): string[] {
    return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string" && item.length > 0) : [];
}

export type SettlementType = string;
export type OrderStatus = string;
export type ExecutionMode = string;
export type OrderAction =
    | "submit_progress"
    | "submit_proof"
    | "submit_delivery_result"
    | "complete_auto_delivery"
    | "complete_money_payment"
    | "abandon_payment"
    | "accept_order"
    | "open_dispute"
    | "cancel_dispute"
    | "assign_reviewer"
    | "override_accept_original"
    | "override_close_original"
    | "open_appeal"
    | "retry_instant_fulfillment"
    | "retry_instant_delivery";
export type InventoryPolicy = string;
export type DeliveryMode = string;
export type DeliverySource = string;
export type InventoryPayloadType = "code" | "link" | "file" | "text";
export type PaymentMethodCode = "shares" | "okx_direct_pay" | string;
export type PostKind = "offer" | "request" | "project" | "review";
export type PostItemFulfillmentMode = "reviewed_delivery" | "instant_fulfillment" | "stock_fulfillment";
export type ProjectRoleCode = NonNullable<Gen.ProjectRoleView["roleCode"]>;

export type IdentityDisplaySkin = Override<Contract<Gen.IdentityDisplaySkinView>, {
    source: "native" | "verified_identity" | "external_identity";
    certifierId: string | null;
    platformUserId: string | null;
    avatarUrl: string | null;
    profileUrl: string | null;
    verified: boolean;
}>;

export type Account = Override<Contract<Gen.AccountSummary>, {
    agentSummary?: string;
    displaySkin: IdentityDisplaySkin;
}>;

export type PublicAccount = {
    id: string;
    handle: string;
    displayName: string;
    agentSummary?: string;
    displaySkin: IdentityDisplaySkin;
};

export type PostItemSummary = {
    itemCount: number;
    openItemCount: number;
    minAmount: number | null;
    maxAmount: number | null;
    totalQuantity: number | null;
    remainingQuantity: number | null;
    currency: string | null;
};

export type Market = Override<Contract<Gen.MarketSummary>, {
    settlementType: SettlementType;
    status: string;
}>;

export type ProjectRole = Override<Contract<Gen.ProjectRoleView>, {
    roleCode: ProjectRoleCode;
    projectId?: string;
    accountId?: string;
    assignedByAccountId?: string;
    assignedAt?: string;
    updatedAt?: string;
}>;

export type OfferPost = Override<Contract<Gen.OfferView>, {
    id: string | null;
    offerNo: string;
    actorAccountId: string | null;
    actorHandle: string | null;
    itemSummary?: PostItemSummary | null;
    paymentMethod?: PaymentMethodCode;
    inventoryPolicy: InventoryPolicy;
    status: string;
    tradeStatus?: string;
    visibility?: string;
}>;

export type RequestPost = Override<Contract<Gen.RequestView>, {
    id: string | null;
    requestNo: string;
    actorAccountId: string | null;
    actorHandle: string | null;
    itemSummary?: PostItemSummary | null;
    paymentMethod?: PaymentMethodCode;
    inventoryPolicy: InventoryPolicy;
    status: string;
    tradeStatus?: string;
    visibility?: string;
}>;

export type ProjectPost = Override<Contract<Gen.ProjectView>, {
    id: string | null;
    ownerAccountId: string | null;
    ownerHandle: string | null;
    parentProjectId: string | null;
    projectNo: string;
    projectLevel: string;
    inventoryPolicy: InventoryPolicy;
    roles: ProjectRole[];
    maintenanceMode?: string;
    repoProvider?: string | null;
    repoOwner?: string | null;
    repoName?: string | null;
    defaultMaintenanceCommands?: string[];
    maintenancePlaybook?: JsonRecord;
    status: string;
    tradeStatus?: string;
    visibility?: string;
}>;

export type ProjectRepoProvision = {
    provisionSessionId: string;
    repoUrl: string;
    cloneUrl: string;
    provider: string;
    owner: string;
    name: string;
    defaultBranch: string;
    visibility: string;
};

export type ValidationProofRequest = {
    id: string;
    launchId: string;
    title: string;
    intent: string;
    evidenceRequirements: JsonRecord[];
    acceptanceSignals: JsonRecord[];
    riskLevel: string;
    version: number;
    parentVersionId?: string | null;
    status: string;
    metadata: JsonRecord;
    createdByAccountId: string;
    createdAt?: string | null;
    updatedAt?: string | null;
};

export type ValidationLaunch = {
    id: string;
    projectId: string;
    title: string;
    hypothesis: string;
    status: "draft" | "live" | "reviewing" | "settled" | string;
    version: number;
    parentLaunchId?: string | null;
    sourceRefs: JsonRecord[];
    metadata: JsonRecord;
    createdByAccountId: string;
    publishedByAccountId?: string | null;
    settledByAccountId?: string | null;
    createdAt?: string | null;
    publishedAt?: string | null;
    settledAt?: string | null;
    updatedAt?: string | null;
    proofRequests: ValidationProofRequest[];
};

export type ValidationTask = {
    id: string;
    projectId: string;
    launchId: string;
    title: string;
    intent: string;
    linkedProofRequestIds: string[];
    deliverable: string;
    acceptanceCriteria: string[];
    suggestedEvidence: JsonRecord[];
    rewardPreview: JsonRecord;
    templateRef?: string | null;
    status: string;
    subStatus?: string | null;
    tags: string[];
    metadata: JsonRecord;
    createdByAccountId: string;
    claimedByAccountId?: string | null;
    createdAt?: string | null;
    claimedAt?: string | null;
    updatedAt?: string | null;
};

export type ValidationProof = {
    id: string;
    projectId: string;
    launchId: string;
    taskId: string;
    summary: string;
    evidenceItems: JsonRecord[];
    linkedProofRequestIds: string[];
    notes?: string | null;
    status: string;
    validationStats?: {
        participantCount: number;
        minParticipantCount: number;
        ordinaryValidationCount: number;
        stakedValidationCount: number;
        stakedShares: number;
        effectiveValidationCount: number;
        minEffectiveValidationCount: number;
        sharesPerEffectiveValidator: number;
        finalized: boolean;
    } | null;
    metadata: JsonRecord;
    submittedByAccountId: string;
    createdAt?: string | null;
    updatedAt?: string | null;
};

export type ValidationReviewQueueItem = {
    proof: ValidationProof;
    launchTitle: string;
    launchStatus: string;
    taskTitle: string;
    taskStatus: string;
    submittedByAccountId: string;
    submittedAt?: string | null;
    reviewRewardPreview: JsonRecord;
};

export type ValidationFeedback = {
    id: string;
    projectId: string;
    launchId?: string | null;
    subjectType: string;
    subjectId: string;
    intent: string;
    reason: string;
    evidence: JsonRecord[];
    suggestedAction?: string | null;
    status: string;
    metadata: JsonRecord;
    createdByAccountId: string;
    resolvedByAccountId?: string | null;
    createdAt?: string | null;
    resolvedAt?: string | null;
    updatedAt?: string | null;
};

export type ValidationReward = {
    id: string;
    projectId: string;
    launchId: string;
    taskId?: string | null;
    proofId?: string | null;
    recipientAccountId?: string | null;
    status: string;
    contributionWeight: number;
    rewardSnapshot: JsonRecord;
    metadata: JsonRecord;
    createdAt?: string | null;
    updatedAt?: string | null;
};

export type PublicFeed = {
    accountsById: Record<string, PublicAccount>;
    offers: OfferPost[];
    requests: RequestPost[];
    projects: ProjectPost[];
    rootProject?: ProjectPost | null;
    counts: Record<string, number>;
};

export type PostItemStatus = string;

export type PostItem = Override<Contract<Gen.PostItemView>, {
    postKind: PostKind;
    fulfillmentMode: string;
    deliveryMode: DeliveryMode;
    deliverySource: DeliverySource;
    priority: string;
    settlementType: SettlementType;
    status: PostItemStatus;
    activeOrderNo?: string;
    activeOrderDisplayPhase?: string;
    activeOrderPaymentRequired?: boolean;
    activeOrderPaymentStatus?: string;
    lockExpiresAt?: string;
    nextProgressDueAt?: string;
    deliveryProvider?: string;
    deliveryInputSchema?: JsonRecord;
    deliverySlaLabel?: string;
    deliveryFailurePolicy?: string;
}>;

export type PublishPostItemInput = {
    name: string;
    description?: string;
    deliveryStandard: string;
    acceptanceCriteria?: string[];
    amount?: number;
    difficultyScore?: number;
    quantity: number;
    agentInstruction?: string;
    itemType?: string;
    mode?: PostItemFulfillmentMode;
};

export type DigitalInventorySummary = {
    itemId: string;
    available: number;
    reserved: number;
    delivered: number;
    voided: number;
    total: number;
};

export type DigitalInventoryUploadResult = {
    itemId: string;
    uploaded: number;
    inventoryItemIds: string[];
    summary: DigitalInventorySummary;
};

export type DigitalDeliveryReveal = {
    orderNo: string;
    inventoryItemId: string;
    payload: string;
    payloadPreview: string;
    deliveredAt: string;
};

export type PublishProjectItemInput = {
    name: string;
    description?: string;
    deliveryStandard: string;
    acceptanceCriteria?: string[];
    difficultyScore?: number;
    itemType?: string;
    mode?: PostItemFulfillmentMode;
};

export type ProjectShares = Override<Contract<Gen.ProjectSharesView>, {
    taskBudget?: number;
    taskMinted?: number;
    taskReserved?: number;
    taskRemaining?: number;
    reserveBudget?: number;
}>;

export type ProjectRepoBinding = {
    id: string;
    projectId: string;
    provider?: string | null;
    repoUrl: string;
    repoOwner: string;
    repoName: string;
    defaultBranch?: string | null;
    installationId?: string | null;
};

export type ProjectPrLink = JsonRecord & {
    id?: string;
    projectId?: string;
    validationTaskId?: string | null;
    repoUrl?: string | null;
    prNumber?: number | null;
    prUrl?: string | null;
    headSha?: string | null;
    baseBranch?: string | null;
    branchName?: string | null;
    state?: string | null;
    updatedAt?: string | null;
};

export type ProjectCiCheck = JsonRecord & {
    id?: string;
    projectId?: string;
    validationTaskId?: string | null;
    repoUrl?: string | null;
    prNumber?: number | null;
    headSha?: string | null;
    checkName?: string | null;
    status?: string | null;
    conclusion?: string | null;
    detailsUrl?: string | null;
    updatedAt?: string | null;
};

export type ProjectPrCiStatus = {
    pullRequests: ProjectPrLink[];
    checks: ProjectCiCheck[];
};

export type ProjectDirectionCard = {
    directionId: string;
    statement: string;
    hypothesis?: string | null;
    audience?: string | null;
    successMetric?: string | null;
    score: number;
    status: "planned" | "active" | "proving" | "validated" | string;
    taskIds: string[];
    taskCount: number;
    claimedCount: number;
    proofCount: number;
    acceptedCount: number;
};

export type ProjectCommercialization = {
    projectNo: string;
    projectId: string;
    directions: ProjectDirectionCard[];
    leadingDirection?: ProjectDirectionCard | null;
    validatedDirections: ProjectDirectionCard[];
    proofStats: {
        totalTasks: number;
        claimedTasks: number;
        submittedProofs: number;
        acceptedProofs: number;
        deploymentProofs: number;
        releaseProofs: number;
        opsIncidentProofs: number;
    };
    sharePool?: ProjectShares | null;
    revenuePool: {
        currency: string;
        eventCount: number;
        totalMinor: number;
    };
    currentDistribution: {
        epochId: string;
        status: "collecting" | "ready" | string;
        currency: string;
        totalRevenueMinor: number;
        eligibleShareMinted: number;
        acceptedTaskCount: number;
    };
};

export type ProjectMemoryRoot = {
    id: string;
    provider: string;
    repoOwner: string;
    repoName: string;
    branch: string;
    commitSha: string;
    rootHash: string;
    syncStatus: string;
    errorCode?: string | null;
    errorMessage?: string | null;
    rawRoot: JsonRecord;
    syncedAt?: string | null;
};

export type ProjectMemorySource = {
    id: string;
    sourceId: string;
    kind: string;
    path: string;
    sha256: string;
    visibility: string;
    provider?: string | null;
    externalUrl?: string | null;
    syncStatus: string;
    metadata: JsonRecord;
    createdAt?: string | null;
};

export type ProjectMemoryEntry = {
    id: string;
    memoryId: string;
    kind: string;
    content: string;
    sourceRefs: string[];
    confidence: number;
    visibility: string;
    riskLevel: string;
    retrievalTags: string[];
    supersedes: string[];
    status: "proposed" | "active" | "superseded" | "rejected" | string;
    createdByAccountId?: string | null;
    approvedByAccountId?: string | null;
    approvedAt?: string | null;
    updatedAt?: string | null;
};

export type ProjectMemorySyncEvent = {
    id: string;
    eventType: string;
    status: string;
    message?: string | null;
    payload: JsonRecord;
    createdAt?: string | null;
};

export type ProjectMemoryOverview = {
    latestRoot?: ProjectMemoryRoot | null;
    sources: ProjectMemorySource[];
    entries: ProjectMemoryEntry[];
    events: ProjectMemorySyncEvent[];
};

export type ProjectAgentContext = {
    project: JsonRecord;
    memory: Record<string, ProjectMemoryEntry[]>;
    validation: JsonRecord;
    workbench: JsonRecord;
    toolContracts: JsonRecord;
    memorySource: JsonRecord;
};

export type ProjectAgentActionCard = {
    cardId: string;
    type: "submit_pack" | "revise_pack" | "score_review" | "challenge_pack" | "result_review" | "final_review" | string;
    title: string;
    packId?: string | null;
    context: JsonRecord;
    requiredFields: string[];
};

export type ProjectAgentInbox = {
    project: JsonRecord;
    agentState: JsonRecord;
    cards: ProjectAgentActionCard[];
};

export type ProjectAgentActionResult = {
    accepted: boolean;
    actionType: string;
    packId?: string | null;
    status: string;
    result: JsonRecord;
    inbox: ProjectAgentInbox;
};

export type ProjectSourceContract = JsonRecord & {
    version: string;
    projectNo: string;
    contractHash: string;
    readbackUrl: string;
};

type WorkspaceBase = Override<Contract<Omit<Gen.PostWorkspaceView, "post" | "postKind">>, {
    market: Market;
    shares?: ProjectShares;
    items: PostItem[];
    itemCounts: Record<string, number>;
}>;

export type ProjectWorkspace = WorkspaceBase & {
    postKind: "project";
    post: ProjectPost;
    project: ProjectPost;
};

export type ProjectDashboard = {
    workspace: ProjectWorkspace;
    repoBindings: ProjectRepoBinding[];
    prCiStatus?: ProjectPrCiStatus | null;
    projectMemory?: ProjectMemoryOverview | null;
    agentContext?: ProjectAgentContext | null;
    commercialization?: ProjectCommercialization | null;
    sections: {
        repoBindings: DashboardSection<ProjectRepoBinding[]>;
        prCiStatus: DashboardSection<ProjectPrCiStatus | null>;
        projectMemory: DashboardSection<ProjectMemoryOverview | null>;
        agentContext: DashboardSection<ProjectAgentContext | null>;
        commercialization: DashboardSection<ProjectCommercialization | null>;
    };
};

export type DashboardSection<T> = {
    status: "visible" | "forbidden" | "missing" | "unavailable";
    data: T;
    errorCode?: string | null;
};

type ProjectDashboardResponse = {
    workspace: Gen.PostWorkspaceView;
    repoBindings?: DashboardSection<ProjectRepoBinding[]> | ProjectRepoBinding[] | null;
    prCiStatus?: DashboardSection<ProjectPrCiStatus | null> | ProjectPrCiStatus | null;
    projectMemory?: DashboardSection<ProjectMemoryOverview | null> | ProjectMemoryOverview | null;
    agentContext?: DashboardSection<ProjectAgentContext | null> | ProjectAgentContext | null;
    commercialization?: DashboardSection<ProjectCommercialization | null> | ProjectCommercialization | null;
};

export type OfferWorkspace = WorkspaceBase & {
    postKind: "offer";
    post: OfferPost;
    offer: OfferPost;
};

export type RequestWorkspace = WorkspaceBase & {
    postKind: "request";
    post: RequestPost;
    request: RequestPost;
};

export type Proof = Override<Contract<Gen.ProofSummary>, {
    kind: string;
    proofPayload: JsonRecord;
    executionMode: ExecutionMode;
    decision?: string;
}>;

export type Order = Override<Contract<Gen.OrderSummary>, {
    orderName: string;
    postKind?: PostKind;
    status: OrderStatus;
    buyerAccountId?: string;
    sellerAccountId?: string;
    fulfillerAccountId?: string;
    acceptorAccountId?: string;
    roleModelVersion?: string;
    currentAccountRole?: string;
    settlementType: SettlementType;
    deliveryMode?: DeliveryMode;
    deliverySource?: DeliverySource;
    paymentMethod?: PaymentMethodCode;
    deliveryPayload?: JsonRecord;
    deliveryReceipt?: JsonRecord;
    deliveryProvider?: string;
    deliveryInputSchema?: JsonRecord;
    deliverySlaLabel?: string;
    deliveryFailurePolicy?: string;
    fulfillmentMode?: string;
    acceptanceCriteriaSnapshot: string[];
    settlementFrozen: boolean;
    reviewStatus?: string;
    disputeWindowStatus?: string;
    disputeWindowExpiresAt?: string | null;
    finalizedAt?: string | null;
    paymentIntentStatus?: string | null;
    paymentDueAt?: string | null;
}>;

export type ProgressUpdate = Override<Contract<Gen.ProgressUpdateView>, {
    executionMode: ExecutionMode;
}>;

export type SharesLedgerEntry = Override<Contract<Gen.SharesLedgerEntryEntity>, {
    settlementTypeSnapshot: SettlementType;
    reason: string;
}>;

export type ShareSettlementHold = {
    id: string;
    orderId: string;
    orderNo: string;
    orderStatus?: string | null;
    marketId: string;
    projectId?: string | null;
    itemId?: string | null;
    accountId: string;
    amount: number;
    curveSlot: number;
    reason: string;
    status: string;
    lockReason?: string | null;
    releaseReason?: string | null;
    disputeWindowExpiresAt?: string | null;
    releasedAt?: string | null;
    cancelledAt?: string | null;
    createdAt: string;
    updatedAt: string;
};

export type ShareReleaseRequest = Override<Contract<Gen.ShareReleaseRequestView>, {
    id: string;
    issuerType: string;
    marketId: string;
    projectId: string;
    orderId: string;
    proofId?: string;
    accountId: string;
    amount: number;
    curveSlot: number;
    status: string;
    requiredRoleCodes: ProjectRoleCode[];
    approvedRoleCodes: ProjectRoleCode[];
    skippedRoleCodes: ProjectRoleCode[];
    metadata: JsonRecord;
}>;

export type OrderEvent = Override<Contract<Gen.OrderEventView>, {
    orderId: string;
    label: string;
}>;

export type BackofficeAuditEvent = Override<Contract<Gen.AuditEventView>, {
    payload: JsonRecord;
}>;

export type BackofficeRiskEvent = Override<Contract<Gen.RiskEventView>, {
    payload: JsonRecord;
}>;

export type RiskAccount = {
    accountId: string;
    handle: string;
    displayName: string;
    status: string;
    riskLevel: string;
    frozenUntil?: string | null;
    riskReason?: string | null;
    riskUpdatedAt?: string | null;
    recentEvents: BackofficeRiskEvent[];
};

export type BackofficeProofAsset = Override<Contract<Gen.ProofAssetView>, {
    metadata: JsonRecord;
    status: string;
}>;

export type WorkResult = {
    id: string;
    resultNo: string;
    workThreadId: string;
    actorAccountId: string;
    summary: string;
    prUrl: string;
    testSummary: string;
    changedFiles: string[];
    evidenceRefs: string[];
    runtime: string;
    status: string;
    createdAt: string;
};

export type WorkThread = {
    id: string;
    threadNo: string;
    projectId: string;
    createdByAccountId: string;
    assigneeAccountId: string | null;
    reviewerAccountId: string | null;
    issueUrl: string | null;
    repoRef: string | null;
    title: string;
    goal: string;
    deliverables: string[];
    acceptanceCriteria: string[];
    taskValue: number;
    bountyAmountMinor: number;
    bountyToken: string;
    status: string;
    createdAt: string;
    updatedAt: string;
    submittedAt: string | null;
    settledAt: string | null;
    latestResult: WorkResult | null;
};

export type ProjectRevenueAddress = {
    id: string;
    projectId: string;
    chainId: string;
    contractAddress: string;
    tokenAddress: string;
    status: string;
};

export type ContributionReward = {
    totalShares: number;
    bountyAmountMinor: number;
    bountyToken: string;
    claimableAmountMinor: number;
    claimableToken: string;
};

export type ContributionLedgerEntry = {
    id: string;
    projectId: string;
    workThreadId: string;
    resultId: string;
    accountId: string;
    taskValue: number;
    shares: number;
    bountyAmountMinor: number;
    bountyToken: string;
    status: string;
    createdAt: string;
};

export type ContributionMember = {
    accountId: string;
    totalShares: number;
    totalTaskValue: number;
    settledCount: number;
    bountyAmountMinor: number;
    bountyToken: string;
};

export type DistributionBatch = {
    id: string;
    projectId: string;
    period: string;
    totalRevenueMinor: number;
    totalSnapshotShares: number;
    merkleRoot: string;
    myClaimableAmountMinor: number;
    token: string;
    status: string;
    createdAt: string;
    updatedAt: string;
};

export type DistributionClaim = {
    batchId: string;
    projectId: string;
    period: string;
    accountId: string;
    walletAddress: string;
    amountMinor: number;
    token: string;
    proof: string[];
    txHash: string | null;
    status: string;
};

export type WorkThreadOverview = {
    projectId: string;
    projectNo: string;
    owner: boolean;
    revenueAddress: ProjectRevenueAddress | null;
    myRewards: ContributionReward;
    workThreads: WorkThread[];
    ledger: ContributionLedgerEntry[];
    contributors: ContributionMember[];
    distributions: DistributionBatch[];
};

export type WorkbenchItem = Override<Contract<Gen.WorkbenchItemView>, {
    actions: Array<{
        id: "open" | "dismiss" | string;
        label: string;
        mode?: "navigate" | "direct" | "form" | string;
        requiredInputs?: string[];
        targetHref?: string;
        destructive?: boolean;
    }>;
    category?: string;
    filterTags?: string[];
    roleBucket?: string;
    domain?: string;
    actionKind?: string;
    targetHref?: string;
    summaryFacts?: Array<{ key: string; value: string }>;
}>;

export type WorkbenchProjection = {
    items: WorkbenchItem[];
    selectedItem: WorkbenchItem | null;
};

export type BackofficeDashboard = Override<Contract<Gen.BackofficeDashboardView>, {
    recentPaymentIntents: PaymentIntent[];
    recentProofAssets: BackofficeProofAsset[];
    recentAuditEvents: BackofficeAuditEvent[];
    recentRiskEvents: BackofficeRiskEvent[];
}>;

export type IdentityBadge = Contract<Gen.IdentityBadgeView>;
export type IdentityLinkedAccount = Contract<Gen.IdentityLinkedAccountView>;
export type IdentityCertifier = Contract<Gen.IdentityCertifierView>;
export type IdentityVerificationChallenge = Override<Contract<Gen.IdentityVerificationChallengeView>, {
    status: string;
}>;

export type IdentityProfile = Override<Contract<Gen.IdentityProfileView>, {
    account: Account;
    badges: IdentityBadge[];
    linkedAccounts: IdentityLinkedAccount[];
    displaySkin: IdentityDisplaySkin;
    displaySkinOptions: IdentityDisplaySkin[];
}>;

export type IdentityActivity = Override<Contract<Gen.IdentityActivityView>, {
    myOffers: OfferPost[];
    myRequests: RequestPost[];
    myProjects: ProjectPost[];
    myOrders: Order[];
    sharesLedger: SharesLedgerEntry[];
    shareSettlementHolds: ShareSettlementHold[];
    agentCapabilitySummary: JsonRecord;
}>;

export type IdentityPage = Override<Contract<Gen.IdentityPageView>, {
    profile: IdentityProfile;
    activity: IdentityActivity;
    certifiers: IdentityCertifier[];
    challenges: IdentityVerificationChallenge[];
}>;

export type IdentityDashboard = IdentityActivity & {
    account: Account;
};

export type PublicProfileIdentity = {
    account: PublicAccount;
    verified: boolean;
    verifiedFactCount: number;
    badges: IdentityBadge[];
    linkedAccounts: IdentityLinkedAccount[];
    displaySkin: IdentityDisplaySkin;
};

export type PublicProfileActivity = {
    offers: OfferPost[];
    requests: RequestPost[];
    projects: ProjectPost[];
};

export type PublicProfile = {
    profile: PublicProfileIdentity;
    activity: PublicProfileActivity;
};

export type IdentityVerificationStartResult = Override<Contract<Gen.IdentityVerificationStartResponse>, {
    challenge: IdentityVerificationChallenge;
}>;

export type ActionView = Override<Contract<Gen.ActionView>, {
    id: OrderAction;
    disabledReason?: string;
    reasonCode?: string;
    role?: string;
    requiresPayment?: boolean;
    requiresProof?: boolean;
    dangerLevel?: string;
}>;
export type SettlementPreview = Override<Contract<Gen.SettlementPreview>, {
    settlementType: SettlementType;
}>;
export type ReviewContext = Override<Contract<Gen.ReviewContext>, {
    disputeOpenedByAccountId?: string;
    disputeOpenedFromStatus?: string;
    disputeOpenedFromWindowStatus?: string;
    disputeOpenedFromWindowExpiresAt?: string | null;
    disputeOpenedAt?: string | null;
    disputeCancelledByAccountId?: string;
    disputeCancelledAt?: string | null;
    disputeCancelReason?: string;
    disputeEvidenceRefs: string[];
}>;
export type OrderPost = Override<Contract<Gen.OrderPostView>, {
    kind: PostKind;
}>;
export type OrderDetail = Override<Contract<Gen.OrderDetailView>, {
    order: Order;
    post: OrderPost;
    item?: PostItem;
    proof?: Proof;
    progressTimeline: ProgressUpdate[];
    paymentIntent?: PaymentIntent;
    availableActions: ActionView[];
    settlementPreview: SettlementPreview;
    shareSettlementHold?: ShareSettlementHold;
    shareReleaseRequest?: ShareReleaseRequest;
    reviewContext?: ReviewContext;
    eventTimeline: OrderEvent[];
}>;

export type PaymentIntent = Override<Contract<Gen.PaymentIntentView>, {
    status: string;
    metadata: JsonRecord;
}>;
export type PaymentIntentResponse = Override<Contract<Gen.PaymentIntentResponse>, {
    paymentIntent: PaymentIntent;
}>;

export type CommandReceipt = Override<Contract<Gen.CommandReceipt>, {
    payload: JsonRecord;
    status: string;
}>;

export type AuthSession = Override<Contract<Gen.AuthSessionResponse>, {
    account: Account;
}>;

export type UploadTicket = Override<Contract<Gen.UploadPresignResponse>, {
    uploadHeaders: Record<string, string>;
}>;
export type UploadCompletion = Override<Contract<Gen.UploadCompletionResponse>, {
    status: string;
}>;
export type UploadDownload = {
    assetId: string;
    artifactRef: string;
    filename: string;
    contentType: string;
    contentLengthBytes: number;
    checksumSha256: string;
    status: string;
    downloadMethod: string;
    downloadUrl: string;
    downloadHeaders: Record<string, string>;
    expiresAt: string;
};
export type PasswordResetRequestResult = Contract<Gen.PasswordResetRequestResponse>;

export function backofficeAuditContract(event: Gen.AuditEventView): BackofficeAuditEvent {
    return contract(event) as BackofficeAuditEvent;
}

export function backofficeRiskContract(event: Gen.RiskEventView): BackofficeRiskEvent {
    return contract(event) as BackofficeRiskEvent;
}

export function backofficeProofAssetContract(asset: Gen.ProofAssetView): BackofficeProofAsset {
    return contract(asset) as BackofficeProofAsset;
}

export function backofficeDashboardContract(view: Gen.BackofficeDashboardView): BackofficeDashboard {
    return contract(view) as BackofficeDashboard;
}

export function paymentIntentContract(paymentIntent?: Gen.PaymentIntentView | null): PaymentIntent | undefined {
    return paymentIntent ? contract(paymentIntent) as PaymentIntent : undefined;
}

export function readWorkbenchProjection(value: unknown): WorkbenchProjection | null {
    // 中文注释：agent projection 是动态 JSON，只在这里做最低限度形状收口，避免页面复制解析规则。
    const projection = record(value);
    if (!Object.keys(projection).length) return null;
    const current = record(projection.current);
    const selectedItem = current.id ? readWorkbenchItem(current) : null;
    return {
        items: selectedItem ? [selectedItem] : [],
        selectedItem,
    };
}

export function readWorkbenchItems(value: unknown): WorkbenchItem[] {
    return Array.isArray(value) ? value.map(readWorkbenchItem).filter((item) => item.id) : [];
}

export function readWorkbenchItem(value: unknown): WorkbenchItem {
    const item = record(value);
    const target = record(item.target);
    return contract({
        ...item,
        id: typeof item.id === "string" ? item.id : "",
        title: typeof item.title === "string" ? item.title : "",
        description: typeof item.description === "string" ? item.description : "",
        lane: typeof item.lane === "string" ? item.lane : "fulfiller",
        urgency: typeof item.urgency === "string" ? item.urgency : "attention",
        reason: typeof item.reason === "string" ? item.reason : "",
        category: typeof item.category === "string" ? item.category : undefined,
        filterTags: stringArray(item.filterTags),
        roleBucket: typeof item.roleBucket === "string" ? item.roleBucket : undefined,
        domain: typeof item.domain === "string" ? item.domain : undefined,
        actionKind: typeof item.actionKind === "string" ? item.actionKind : undefined,
        targetHref: typeof item.targetHref === "string" ? item.targetHref : undefined,
        summaryFacts: readWorkbenchSummaryFacts(item.summaryFacts),
        canDismiss: item.canDismiss === true,
        target: {
            type: typeof target.type === "string" ? target.type : "",
            id: typeof target.id === "string" ? target.id : "",
        },
        actions: Array.isArray(item.actions)
            ? item.actions.map((action) => {
                const actionRecord = record(action);
                return {
                    id: typeof actionRecord.id === "string" ? actionRecord.id : "",
                    label: typeof actionRecord.label === "string" ? actionRecord.label : "",
                    mode: typeof actionRecord.mode === "string" ? actionRecord.mode : undefined,
                    requiredInputs: stringArray(actionRecord.requiredInputs),
                    targetHref: typeof actionRecord.targetHref === "string" ? actionRecord.targetHref : undefined,
                    destructive: actionRecord.destructive === true,
                };
            }).filter((action) => action.id)
            : [],
    }) as WorkbenchItem;
}

function readWorkbenchSummaryFacts(value: unknown): Array<{ key: string; value: string }> {
    if (!Array.isArray(value)) {
        return [];
    }
    return value.map((fact) => {
        const factRecord = record(fact);
        return {
            key: typeof factRecord.key === "string" ? factRecord.key : "",
            value: typeof factRecord.value === "string" ? factRecord.value : "",
        };
    }).filter((fact) => fact.key && fact.value);
}

function workspace<TPost, TKey extends "project" | "offer" | "request">(
    view: Gen.PostWorkspaceView,
    key: TKey,
): WorkspaceBase & Record<TKey, TPost> & { postKind: TKey; post: TPost } {
    const post = view.post as TPost;
    return contract({
        ...view,
        postKind: key,
        post,
        [key]: post,
        market: view.market,
        shares: view.shares,
        items: array(view.items),
        itemCounts: view.itemCounts ?? {},
    }) as unknown as WorkspaceBase & Record<TKey, TPost> & { postKind: TKey; post: TPost };
}

export async function listAccounts(): Promise<PublicAccount[]> {
    // 中文注释：市场公开面只读取公开账号目录，公开 profile id 与 handle 保持一致。
    const page = await Api.listPublicAccounts({limit: 100});
    return contractList(array(page.items ?? [])) as unknown as PublicAccount[];
}

export async function lookupPublicAccounts(ids: string[]): Promise<PublicAccount[]> {
    // 中文注释：公开账号批量查找走同一公开摘要协议，调用方可以按 public id 一次补齐多张卡片的人物信息。
    if (ids.length === 0) {
        return [];
    }
    return contractList(await monopolyfunFetch<PublicAccount[]>("/api/v1/accounts/lookup", {params: {ids}})) as unknown as PublicAccount[];
}

export async function getPublicAccount(id: string): Promise<PublicAccount | null> {
    const items = await lookupPublicAccounts([id]);
    return items[0] ?? null;
}

export async function getMyProjectAuthority(projectNo: string): Promise<Gen.ProjectAuthorityContextView> {
    requireClientSession();
    // 中文注释：项目工资和后台入口共用项目权限视图，避免前端复制角色到能力的推导规则。
    return contract(await Api.getMyProjectAuthority(projectNo)) as Gen.ProjectAuthorityContextView;
}

export async function listAccountDirectory(): Promise<Account[]> {
    const page = await monopolyfunFetch<PageResult<Gen.AccountSummary> | Gen.AccountSummary[]>("/api/v1/accounts/directory", {params: {limit: 100}});
    return contractList(pageItems(page)) as unknown as Account[];
}

export async function getHomeFeed(): Promise<PublicFeed> {
    return contract(await Api.getHomeFeed()) as unknown as PublicFeed;
}

export async function getMarketFeed(options?: {
    kind?: "all" | "offer" | "request" | "project";
    status?: string;
    q?: string;
    sort?: "recent" | "oldest" | "title";
    limit?: number;
    cursor?: string;
}): Promise<PublicFeed> {
    return contract(await monopolyfunFetch<Gen.PublicFeedView>("/api/v1/public/market-feed", {params: options})) as unknown as PublicFeed;
}

export async function listReviewerCandidates(orderId: string): Promise<Account[]> {
    return contractList(await monopolyfunFetch<Gen.AccountSummary[]>(`/api/v1/orders/${orderId}/reviewer-candidates`)) as unknown as Account[];
}

export async function listBackofficeAuditEvents(limit = 50): Promise<BackofficeAuditEvent[]> {
    return contractList(await Api.listBackofficeAuditEvents({limit})) as BackofficeAuditEvent[];
}

export async function getBackofficeDashboard(): Promise<BackofficeDashboard> {
    return contract(await Api.getBackofficeDashboard()) as BackofficeDashboard;
}

export async function listBackofficeRiskEvents(limit = 50): Promise<BackofficeRiskEvent[]> {
    return contractList(await Api.listBackofficeRiskEvents({limit})) as BackofficeRiskEvent[];
}

export async function listRiskAccounts(limit = 50): Promise<RiskAccount[]> {
    const page = await Api.listRiskAccounts({limit});
    return pageItems(page as unknown as PageResult<RiskAccount>).map((account) => ({
        ...account,
        recentEvents: (account.recentEvents ?? []).map(backofficeRiskContract),
    }));
}

export async function getRiskAccount(accountId: string): Promise<RiskAccount> {
    const account = contract(await Api.getRiskAccount(accountId)) as unknown as RiskAccount;
    return {
        ...account,
        recentEvents: (account.recentEvents ?? []).map(backofficeRiskContract),
    };
}

export async function listBackofficePaymentIntents(limit = 50): Promise<PaymentIntent[]> {
    return contractList(await Api.listBackofficePaymentIntents({limit})) as PaymentIntent[];
}

export async function listBackofficeProofAssets(limit = 50): Promise<BackofficeProofAsset[]> {
    return contractList(await Api.listBackofficeProofAssets({limit})) as BackofficeProofAsset[];
}

export async function getIdentityDashboard(): Promise<IdentityDashboard> {
    requireClientSession();
    const page = contract(await Api.getCurrentIdentity()) as unknown as IdentityPage;
    return {
        account: page.profile.account,
        ...page.activity,
    };
}

export async function getIdentityPage(): Promise<IdentityPage> {
    requireClientSession();
    return contract(await Api.getCurrentIdentity()) as unknown as IdentityPage;
}

export async function getPublicProfile(handle: string): Promise<PublicProfile> {
    const normalizedHandle = handle.trim();
    return contract(await monopolyfunFetch<PublicProfile>(`/api/v1/public/profiles/${encodeURIComponent(normalizedHandle)}`)) as PublicProfile;
}

export async function updateIdentityProfile(input: {
    displayName: string;
    agentSummary?: string;
    avatarUrl?: string;
}): Promise<IdentityPage> {
    requireClientSession();
    return contract(await monopolyfunFetch<Gen.IdentityPageView>("/api/v1/identity/profile", {
        method: "PATCH",
        body: JSON.stringify(input),
    })) as unknown as IdentityPage;
}

export async function beginIdentityVerification(certifierId: string, input: JsonRecord = {}): Promise<IdentityVerificationStartResult> {
    requireClientSession();
    return contract(await Api.startVerification({certifierId, input})) as IdentityVerificationStartResult;
}

export async function completeIdentityVerification(challengeId: string, input: JsonRecord = {}): Promise<void> {
    requireClientSession();
    await Api.completeVerification(challengeId, {input});
}

export async function updateIdentityDisplaySkin(input: {
    source: "native" | "verified_identity";
    certifierId?: string | null;
}): Promise<IdentityPage> {
    requireClientSession();
    return contract(await Api.updateDisplaySkin({
        source: input.source,
        ...(input.certifierId ? {certifierId: input.certifierId} : {}),
    })) as unknown as IdentityPage;
}

export async function listWorkbenchItems(): Promise<WorkbenchItem[]> {
    requireClientSession();
    return readWorkbenchItems(await Api.listWorkbenchItems());
}

export async function waitWorkbenchItems(timeoutSec = 30): Promise<WorkbenchItem[]> {
    requireClientSession();
    return readWorkbenchItems(await Api.waitWorkbenchItems({timeoutSec}));
}

export async function dismissWorkbenchItem(itemId: string): Promise<WorkbenchItem> {
    requireClientSession();
    return readWorkbenchItem(await monopolyfunFetch(`/api/v1/workbench/${encodeURIComponent(itemId)}/dismiss`, {
        method: "POST",
    }));
}

export async function acceptProjectInvite(itemId: string): Promise<CommandReceipt> {
    requireClientSession();
    return contract(await monopolyfunFetch(`/api/v1/workbench/${encodeURIComponent(itemId)}/project-invite/accept`, {
        method: "POST",
    })) as CommandReceipt;
}

export async function declineProjectInvite(itemId: string): Promise<CommandReceipt> {
    requireClientSession();
    return contract(await monopolyfunFetch(`/api/v1/workbench/${encodeURIComponent(itemId)}/project-invite/decline`, {
        method: "POST",
    })) as CommandReceipt;
}

export async function submitWorkProgress(
    itemId: string,
    input: {
        actorAccountId: string;
        stepTitle: string;
        summary: string;
        progressPayload?: JsonRecord;
        links?: Array<{ label: string; href: string }>;
        artifacts?: string[];
        executionMode?: string;
        agentSessionId?: string;
        agentRuntime?: string;
    },
): Promise<CommandReceipt> {
    requireClientSession();
    // 中文注释：执行页写动作统一落到 Work API，订单状态联动由后端 WorkCommandService 完成。
    return contract(await monopolyfunFetch(`/api/v1/work/items/${encodeURIComponent(itemId)}/progress`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as CommandReceipt;
}

export async function submitWorkReceipt(
    itemId: string,
    input: {
        actorAccountId: string;
        summary: string;
        output?: JsonRecord;
        sourceReceipt?: JsonRecord;
        evidenceRefs?: string[];
        traceRefs?: string[];
        contentHashes?: string[];
        links?: Array<{ label: string; href: string }>;
        artifacts?: string[];
        agentRuntime?: string;
    },
): Promise<CommandReceipt> {
    requireClientSession();
    // 中文注释：交付证明统一写入 Work Receipt，订单推进由后端 Work 内核绑定 source 后完成。
    return contract(await monopolyfunFetch(`/api/v1/work/items/${encodeURIComponent(itemId)}/receipt`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as CommandReceipt;
}

export async function completeWorkbenchItem(
    itemId: string,
    input: {
        actorAccountId: string;
        deliverySummary: string;
        deliveryPayload?: JsonRecord;
        deliveryReceipt?: JsonRecord;
        links?: Array<{ label: string; href: string }>;
        artifacts?: string[];
        agentRuntime?: string;
    },
): Promise<CommandReceipt> {
    // 中文注释：PR3 的完成动作仍落到 master 的 Work Receipt 契约，避免新增并行完成接口。
    return submitWorkReceipt(itemId, {
        actorAccountId: input.actorAccountId,
        summary: input.deliverySummary,
        output: input.deliveryPayload,
        sourceReceipt: input.deliveryReceipt,
        links: input.links,
        artifacts: input.artifacts,
        agentRuntime: input.agentRuntime,
    });
}

export async function reviewWorkReceipt(
    itemId: string,
    input: {
        reviewerAccountId: string;
        decision: "accepted" | "revision_requested" | "disputed";
        reason: string;
        evidenceRefs?: string[];
    },
): Promise<CommandReceipt> {
    requireClientSession();
    return contract(await monopolyfunFetch(`/api/v1/work/items/${encodeURIComponent(itemId)}/review`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as CommandReceipt;
}

export async function listOffers(options?: {
    status?: OfferPost["status"];
    q?: string;
    sort?: "recent" | "oldest" | "title";
    limit?: number;
    cursor?: string;
}): Promise<PageResult<OfferPost>> {
    const page = await monopolyfunFetch<PageResult<Gen.OfferView>>("/api/v1/offers", {params: options});
    return contractPage(page, contractList(pageItems(page)) as OfferPost[]);
}

export async function getOfferPost(offerId: string): Promise<OfferPost> {
    return contract(await Api.getOffer(offerId)) as OfferPost;
}

export async function listRequests(options?: {
    status?: RequestPost["status"];
    q?: string;
    sort?: "recent" | "oldest" | "title";
    limit?: number;
    cursor?: string;
}): Promise<PageResult<RequestPost>> {
    const page = await monopolyfunFetch<PageResult<Gen.RequestView>>("/api/v1/requests", {params: options});
    return contractPage(page, contractList(pageItems(page)) as RequestPost[]);
}

export async function getRequestPost(requestId: string): Promise<RequestPost> {
    return contract(await Api.getRequest(requestId)) as RequestPost;
}

export async function listProjects(options?: {
    status?: ProjectPost["status"];
    q?: string;
    sort?: "recent" | "oldest" | "title";
    limit?: number;
    cursor?: string;
}): Promise<PageResult<ProjectPost>> {
    const page = await monopolyfunFetch<PageResult<Gen.ProjectView>>("/api/v1/projects", {params: options});
    return contractPage(page, contractList(pageItems(page)) as ProjectPost[]);
}

export async function getProjectPost(projectId: string): Promise<ProjectPost> {
    return contract(await Api.getProject(projectId)) as ProjectPost;
}

export async function getProjectWorkspace(projectId: string): Promise<ProjectWorkspace> {
    return workspace<ProjectPost, "project">(await Api.getPostWorkspace(projectId), "project") as ProjectWorkspace;
}

export async function getProjectDashboard(projectId: string, requestOptions?: RequestInit): Promise<ProjectDashboard> {
    const view = await monopolyfunFetch<ProjectDashboardResponse>(`/api/v1/projects/${encodeURIComponent(projectId)}/dashboard`, requestOptions);
    const repoBindingsSection = dashboardSection<ProjectRepoBinding[]>(view.repoBindings, []);
    const prCiStatusSection = dashboardSection<ProjectPrCiStatus | null>(view.prCiStatus, null);
    const projectMemorySection = dashboardSection<ProjectMemoryOverview | null>(view.projectMemory, null);
    const agentContextSection = dashboardSection<ProjectAgentContext | null>(view.agentContext, null);
    const commercializationSection = dashboardSection<ProjectCommercialization | null>(view.commercialization, null);
    // 中文注释：项目详情页使用后端聚合视图，治理区字段保持可选，公开 workspace 独立渲染。
    return contract({
        ...view,
        workspace: workspace<ProjectPost, "project">(view.workspace, "project") as ProjectWorkspace,
        repoBindings: array(repoBindingsSection.data),
        prCiStatus: prCiStatusSection.data ?? null,
        projectMemory: projectMemorySection.data ?? null,
        agentContext: agentContextSection.data ?? null,
        commercialization: commercializationSection.data ?? null,
        sections: {
            repoBindings: repoBindingsSection,
            prCiStatus: prCiStatusSection,
            projectMemory: projectMemorySection,
            agentContext: agentContextSection,
            commercialization: commercializationSection,
        },
    }) as ProjectDashboard;
}

function dashboardSection<T>(value: DashboardSection<T> | T | null | undefined, fallback: T): DashboardSection<T> {
    if (value && typeof value === "object" && "status" in value && "data" in value) {
        return value as DashboardSection<T>;
    }
    return {status: "visible", data: (value ?? fallback) as T, errorCode: null};
}

export async function listProjectValidationLaunches(projectId: string): Promise<ValidationLaunch[]> {
    return contractList(await monopolyfunFetch<ValidationLaunch[]>(`/api/v1/projects/${encodeURIComponent(projectId)}/launches`)) as ValidationLaunch[];
}

export async function getProjectWorkroom(projectId: string, requestOptions?: RequestInit): Promise<WorkThreadOverview> {
    return contract(await monopolyfunFetch<WorkThreadOverview>(`/api/v1/projects/${encodeURIComponent(projectId)}/workroom`, requestOptions)) as WorkThreadOverview;
}

export async function createWorkThread(projectId: string, input: {
    title: string;
    goal: string;
    deliverables: string[];
    acceptanceCriteria: string[];
    taskValue: number;
    bountyAmountMinor?: number;
    bountyToken?: string;
    repoRef?: string;
    issueUrl?: string;
    reviewerAccountId?: string;
}): Promise<WorkThread> {
    const session = requireClientSession();
    return contract(await monopolyfunFetch<WorkThread>(`/api/v1/projects/${encodeURIComponent(projectId)}/work-threads`, {
        method: "POST",
        body: JSON.stringify({...input, actorAccountId: session.accountId}),
    })) as WorkThread;
}

export async function claimWorkThread(threadId: string): Promise<CommandReceipt> {
    const session = requireClientSession();
    return contract(await monopolyfunFetch<CommandReceipt>(`/api/v1/work-threads/${encodeURIComponent(threadId)}/claim`, {
        method: "POST",
        body: JSON.stringify({actorAccountId: session.accountId, runtime: "openclaw"}),
    })) as CommandReceipt;
}

export async function submitWorkThreadResult(threadId: string, input: {
    summary: string;
    prUrl: string;
    testSummary: string;
    changedFiles: string[];
    evidenceRefs?: string[];
}): Promise<WorkResult> {
    const session = requireClientSession();
    const resultMarkdown = [
        "---",
        "packetType: work_result",
        `workThreadId: ${threadId}`,
        "---",
        "# Result",
        "",
        "## Summary",
        input.summary,
        "",
        "## Evidence",
        `- PR: ${input.prUrl}`,
        `- Test: ${input.testSummary}`,
        ...(input.evidenceRefs ?? []).map((ref) => `- ${ref}`),
        "",
        "## Changed Files",
        ...input.changedFiles.map((file) => `- ${file}`),
        "",
    ].join("\n");
    return contract(await monopolyfunFetch<WorkResult>(`/api/v1/work-threads/${encodeURIComponent(threadId)}/result`, {
        method: "POST",
        body: JSON.stringify({...input, resultMarkdown, actorAccountId: session.accountId, runtime: "openclaw"}),
    })) as WorkResult;
}

export async function reviewWorkThread(threadId: string, input: {
    decision: "accept" | "resubmit" | "reject";
    reason: string;
}): Promise<CommandReceipt> {
    const session = requireClientSession();
    return contract(await monopolyfunFetch<CommandReceipt>(`/api/v1/work-threads/${encodeURIComponent(threadId)}/review`, {
        method: "POST",
        body: JSON.stringify({...input, reviewerAccountId: session.accountId}),
    })) as CommandReceipt;
}

export async function upsertProjectRevenueAddress(projectId: string, input: {
    chainId: string;
    contractAddress: string;
    tokenAddress: string;
}): Promise<ProjectRevenueAddress> {
    const session = requireClientSession();
    return contract(await monopolyfunFetch<ProjectRevenueAddress>(`/api/v1/projects/${encodeURIComponent(projectId)}/revenue-address`, {
        method: "POST",
        body: JSON.stringify({...input, actorAccountId: session.accountId}),
    })) as ProjectRevenueAddress;
}

export async function createDistributionBatch(projectId: string, input: {
    period: string;
    totalRevenueMinor: number;
}): Promise<DistributionBatch> {
    const session = requireClientSession();
    return contract(await monopolyfunFetch<DistributionBatch>(`/api/v1/projects/${encodeURIComponent(projectId)}/distributions`, {
        method: "POST",
        body: JSON.stringify({...input, actorAccountId: session.accountId}),
    })) as DistributionBatch;
}

export async function claimDistribution(projectId: string, period: string, input: {
    walletAddress: string;
    txHash?: string;
}): Promise<DistributionClaim> {
    const session = requireClientSession();
    return contract(await monopolyfunFetch<DistributionClaim>(`/api/v1/projects/${encodeURIComponent(projectId)}/distributions/${encodeURIComponent(period)}/claim`, {
        method: "POST",
        body: JSON.stringify({...input, actorAccountId: session.accountId}),
    })) as DistributionClaim;
}

export async function createProjectValidationLaunch(projectId: string, input: {
    title: string;
    hypothesis: string;
    proofRequests?: Array<{
        title: string;
        intent: string;
        evidenceRequirements?: JsonRecord[];
        acceptanceSignals?: JsonRecord[];
        riskLevel?: string;
        metadata?: JsonRecord;
    }>;
    parentLaunchId?: string;
    sourceRefs?: JsonRecord[];
    metadata?: JsonRecord;
}): Promise<ValidationLaunch> {
    requireClientSession();
    return contract(await monopolyfunFetch<ValidationLaunch>(`/api/v1/projects/${encodeURIComponent(projectId)}/launches`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as ValidationLaunch;
}

export async function publishProjectValidationLaunch(projectId: string, launchId: string): Promise<ValidationLaunch> {
    requireClientSession();
    return contract(await monopolyfunFetch<ValidationLaunch>(`/api/v1/projects/${encodeURIComponent(projectId)}/launches/${encodeURIComponent(launchId)}/publish`, {
        method: "POST",
    })) as ValidationLaunch;
}

export async function settleProjectValidationLaunch(projectId: string, launchId: string, input: {
    reason?: string;
    scoreSnapshot?: JsonRecord;
    curveSnapshot?: JsonRecord;
    rewardSnapshot?: JsonRecord;
    metadata?: JsonRecord;
}): Promise<ValidationLaunch> {
    requireClientSession();
    return contract(await monopolyfunFetch<ValidationLaunch>(`/api/v1/projects/${encodeURIComponent(projectId)}/launches/${encodeURIComponent(launchId)}/settle`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as ValidationLaunch;
}

export async function listProjectValidationTasks(projectId: string, launchId: string): Promise<ValidationTask[]> {
    return contractList(await monopolyfunFetch<ValidationTask[]>(`/api/v1/projects/${encodeURIComponent(projectId)}/launches/${encodeURIComponent(launchId)}/tasks`)) as ValidationTask[];
}

export async function createProjectValidationTask(projectId: string, launchId: string, input: {
    title: string;
    intent: string;
    linkedProofRequestIds?: string[];
    deliverable: string;
    acceptanceCriteria?: string[];
    suggestedEvidence?: JsonRecord[];
    rewardPreview?: JsonRecord;
    templateRef?: string;
    tags?: string[];
    metadata?: JsonRecord;
}): Promise<ValidationTask> {
    requireClientSession();
    return contract(await monopolyfunFetch<ValidationTask>(`/api/v1/projects/${encodeURIComponent(projectId)}/launches/${encodeURIComponent(launchId)}/tasks`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as ValidationTask;
}

export async function claimProjectValidationTask(projectId: string, taskId: string): Promise<ValidationTask> {
    requireClientSession();
    return contract(await monopolyfunFetch<ValidationTask>(`/api/v1/projects/${encodeURIComponent(projectId)}/tasks/${encodeURIComponent(taskId)}/claim`, {
        method: "POST",
    })) as ValidationTask;
}

export async function submitProjectValidationProof(projectId: string, taskId: string, input: {
    summary: string;
    evidenceItems?: JsonRecord[];
    linkedProofRequestIds?: string[];
    notes?: string;
    metadata?: JsonRecord;
}): Promise<ValidationProof> {
    requireClientSession();
    return contract(await monopolyfunFetch<ValidationProof>(`/api/v1/projects/${encodeURIComponent(projectId)}/tasks/${encodeURIComponent(taskId)}/proof`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as ValidationProof;
}

export async function listProjectValidationProofs(projectId: string, launchId: string): Promise<ValidationProof[]> {
    return contractList(await monopolyfunFetch<ValidationProof[]>(`/api/v1/projects/${encodeURIComponent(projectId)}/launches/${encodeURIComponent(launchId)}/proofs`)) as ValidationProof[];
}

export async function listProjectValidationReviewQueue(projectId: string): Promise<ValidationReviewQueueItem[]> {
    requireClientSession();
    return contractList(await monopolyfunFetch<ValidationReviewQueueItem[]>(`/api/v1/projects/${encodeURIComponent(projectId)}/review-queue`)) as ValidationReviewQueueItem[];
}

export async function reviewProjectValidationProof(projectId: string, proofId: string, input: {
    result: "accept" | "request_changes" | "hold";
    reason?: string;
    validationMode?: "ordinary" | "staked";
    stakedShares?: number;
    requestedEvidence?: JsonRecord[];
    riskFlags?: string[];
    scoreInputs?: JsonRecord;
    metadata?: JsonRecord;
}): Promise<ValidationProof> {
    requireClientSession();
    return contract(await monopolyfunFetch<ValidationProof>(`/api/v1/projects/${encodeURIComponent(projectId)}/proofs/${encodeURIComponent(proofId)}/review`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as ValidationProof;
}

export async function listProjectValidationFeedback(projectId: string): Promise<ValidationFeedback[]> {
    requireClientSession();
    return contractList(await monopolyfunFetch<ValidationFeedback[]>(`/api/v1/projects/${encodeURIComponent(projectId)}/validation-feedback`)) as ValidationFeedback[];
}

export async function createProjectValidationFeedback(projectId: string, input: {
    launchId?: string;
    subjectType: string;
    subjectId: string;
    intent: string;
    reason: string;
    evidence?: JsonRecord[];
    suggestedAction?: string;
    metadata?: JsonRecord;
}): Promise<ValidationFeedback> {
    requireClientSession();
    return contract(await monopolyfunFetch<ValidationFeedback>(`/api/v1/projects/${encodeURIComponent(projectId)}/validation-feedback`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as ValidationFeedback;
}

export async function resolveProjectValidationFeedback(projectId: string, feedbackId: string, input: {
    status: "resolved" | "changes_requested" | "held" | "dismissed";
    resolution?: string;
    metadata?: JsonRecord;
}): Promise<ValidationFeedback> {
    requireClientSession();
    return contract(await monopolyfunFetch<ValidationFeedback>(`/api/v1/projects/${encodeURIComponent(projectId)}/validation-feedback/${encodeURIComponent(feedbackId)}/resolve`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as ValidationFeedback;
}

export async function listProjectValidationRewards(projectId: string): Promise<ValidationReward[]> {
    return contractList(await monopolyfunFetch<ValidationReward[]>(`/api/v1/projects/${encodeURIComponent(projectId)}/rewards`)) as ValidationReward[];
}

export async function listProjectRoles(projectId: string): Promise<ProjectRole[]> {
    return contractList(await Api.listProjectRoles(projectId)) as ProjectRole[];
}

export async function listProjectRepoBindings(projectId: string): Promise<ProjectRepoBinding[]> {
    return contractList(await monopolyfunFetch<ProjectRepoBinding[]>(`/api/v1/projects/${encodeURIComponent(projectId)}/development/repo-bindings`)) as ProjectRepoBinding[];
}

export async function getProjectPrCiStatus(projectId: string): Promise<ProjectPrCiStatus> {
    return contract(await monopolyfunFetch<ProjectPrCiStatus>(`/api/v1/projects/${encodeURIComponent(projectId)}/development/pr-ci`)) as ProjectPrCiStatus;
}

export async function bindProjectRepo(projectId: string, input: {
    provider?: string;
    repoUrl: string;
    repoOwner: string;
    repoName: string;
    defaultBranch?: string;
    installationId?: string;
}): Promise<ProjectRepoBinding> {
    requireClientSession();
    return contract(await monopolyfunFetch<ProjectRepoBinding>(`/api/v1/projects/${encodeURIComponent(projectId)}/development/repo-bindings`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as ProjectRepoBinding;
}

export async function ingestProjectPrCiEvent(projectId: string, input: {
    eventType: string;
    validationTaskId?: string;
    repoUrl?: string;
    prNumber?: number;
    prUrl?: string;
    headSha?: string;
    baseBranch?: string;
    branchName?: string;
    state?: string;
    checkName?: string;
    status?: string;
    conclusion?: string;
    detailsUrl?: string;
    payload?: JsonRecord;
}): Promise<ProjectPrCiStatus> {
    requireClientSession();
    return contract(await monopolyfunFetch<ProjectPrCiStatus>(`/api/v1/projects/${encodeURIComponent(projectId)}/development/pr-ci/events`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as ProjectPrCiStatus;
}

export async function getProjectMemory(projectId: string): Promise<ProjectMemoryOverview> {
    return contract(await monopolyfunFetch<ProjectMemoryOverview>(`/api/v1/projects/${encodeURIComponent(projectId)}/memory`)) as ProjectMemoryOverview;
}

export async function getProjectAgentContext(projectId: string): Promise<ProjectAgentContext> {
    return contract(await monopolyfunFetch<ProjectAgentContext>(`/api/v1/projects/${encodeURIComponent(projectId)}/agent-context`)) as ProjectAgentContext;
}

export async function getProjectAgentInbox(projectId: string, requestOptions?: RequestInit): Promise<ProjectAgentInbox> {
    return contract(await monopolyfunFetch<ProjectAgentInbox>(`/api/v1/projects/${encodeURIComponent(projectId)}/agent/inbox`, requestOptions)) as ProjectAgentInbox;
}

export async function runProjectAgentAction(projectId: string, input: {
    actionType: string;
    cardId?: string;
    payload?: JsonRecord;
}): Promise<ProjectAgentActionResult> {
    requireClientSession();
    return contract(await monopolyfunFetch<ProjectAgentActionResult>(`/api/v1/projects/${encodeURIComponent(projectId)}/agent/actions`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as ProjectAgentActionResult;
}

export async function getProjectSourceContract(projectId: string): Promise<ProjectSourceContract> {
    requireClientSession();
    return contract(await monopolyfunFetch<ProjectSourceContract>(`/api/v1/projects/${encodeURIComponent(projectId)}/memory/source-contract`)) as ProjectSourceContract;
}

export async function createProjectMemoryEntry(projectId: string, input: {
    memoryId?: string;
    kind: string;
    content: string;
    sourceRefs: string[];
    confidence?: number;
    visibility?: string;
    riskLevel?: string;
    retrievalTags?: string[];
    supersedes?: string[];
    originEventType?: string;
    originEventId?: string;
    maintenanceReason?: string;
}): Promise<ProjectMemoryEntry> {
    requireClientSession();
    return contract(await monopolyfunFetch<ProjectMemoryEntry>(`/api/v1/projects/${encodeURIComponent(projectId)}/memory/entries`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as ProjectMemoryEntry;
}

export async function approveProjectMemoryEntry(projectId: string, memoryId: string): Promise<ProjectMemoryEntry> {
    requireClientSession();
    return contract(await monopolyfunFetch<ProjectMemoryEntry>(`/api/v1/projects/${encodeURIComponent(projectId)}/memory/entries/${encodeURIComponent(memoryId)}/approve`, {
        method: "POST",
        body: JSON.stringify({}),
    })) as ProjectMemoryEntry;
}

export async function supersedeProjectMemoryEntry(projectId: string, memoryId: string): Promise<ProjectMemoryEntry> {
    requireClientSession();
    return contract(await monopolyfunFetch<ProjectMemoryEntry>(`/api/v1/projects/${encodeURIComponent(projectId)}/memory/entries/${encodeURIComponent(memoryId)}/supersede`, {
        method: "POST",
        body: JSON.stringify({}),
    })) as ProjectMemoryEntry;
}

export async function getOfferWorkspace(offerId: string): Promise<OfferWorkspace> {
    return workspace<OfferPost, "offer">(await Api.getPostWorkspace(offerId), "offer") as OfferWorkspace;
}

export async function getRequestWorkspace(requestId: string): Promise<RequestWorkspace> {
    return workspace<RequestPost, "request">(await Api.getPostWorkspace(requestId), "request") as RequestWorkspace;
}

export async function getOrderDetail(orderId: string): Promise<OrderDetail> {
    return contract(await Api.getOrder(orderId)) as unknown as OrderDetail;
}

export async function listMyOrders(): Promise<Order[]> {
    return contractList(await monopolyfunFetch<Gen.OrderSummary[]>("/api/v1/orders")) as unknown as Order[];
}

export async function publishOffer(input: {
    title: string;
    description: string;
    currency?: string;
    paymentMethod?: PaymentMethodCode;
    paymentProfile?: string;
    paymentNetwork?: string;
    paymentAsset?: string;
    paymentRecipient?: string;
    items: PublishPostItemInput[];
}): Promise<OfferPost> {
    requireClientSession();
    const response = await Api.publishOffer(input);
    return contract(response.offer) as OfferPost;
}

export async function publishRequest(input: {
    title: string;
    description: string;
    currency?: string;
    paymentMethod?: PaymentMethodCode;
    paymentProfile?: string;
    paymentNetwork?: string;
    paymentAsset?: string;
    paymentRecipient?: string;
    deadlineAt?: string;
    items: PublishPostItemInput[];
}): Promise<RequestPost> {
    requireClientSession();
    const response = await Api.publishRequest(input);
    return contract(response.request) as RequestPost;
}

export async function publishProject(input: {
    title: string;
    description: string;
    goal?: string;
    provisionSessionId?: string;
    ownerIntro?: string;
    paymentMethod?: "shares";
    currency?: "SHARES";
    items: PublishProjectItemInput[];
}): Promise<ProjectPost> {
    requireClientSession();
    const response = await Api.publishProject(input);
    return contract(response.project) as ProjectPost;
}

export async function provisionProjectRepo(input: {
    goal: string;
    titleHint?: string;
}): Promise<ProjectRepoProvision> {
    requireClientSession();
    return contract(await monopolyfunFetch<ProjectRepoProvision>("/api/v1/project-repos/provision", {
        method: "POST",
        body: JSON.stringify(input),
    })) as ProjectRepoProvision;
}

export async function updateOfferPost(offerNo: string, input: {
    actorAccountId: string;
    title: string;
    description: string;
    deliveryStandard?: string;
    currency?: string;
    paymentMethod?: PaymentMethodCode;
    paymentProfile?: string;
    paymentNetwork?: string;
    paymentAsset?: string;
    paymentRecipient?: string;
}): Promise<OfferPost> {
    requireClientSession();
    return contract(await monopolyfunFetch(`/api/v1/offers/${encodeURIComponent(offerNo)}`, {
        method: "PATCH",
        body: JSON.stringify(input),
    })) as OfferPost;
}

export async function closeOfferPost(offerNo: string, input: {
    actorAccountId: string;
    reason?: string
}): Promise<OfferPost> {
    requireClientSession();
    return contract(await monopolyfunFetch(`/api/v1/offers/${encodeURIComponent(offerNo)}/close`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as OfferPost;
}

export async function updateRequestPost(requestNo: string, input: {
    actorAccountId: string;
    title: string;
    description: string;
    deliveryStandard?: string;
    currency?: string;
    paymentMethod?: PaymentMethodCode;
    paymentProfile?: string;
    paymentNetwork?: string;
    paymentAsset?: string;
    paymentRecipient?: string;
    deadlineAt?: string;
}): Promise<RequestPost> {
    requireClientSession();
    return contract(await monopolyfunFetch(`/api/v1/requests/${encodeURIComponent(requestNo)}`, {
        method: "PATCH",
        body: JSON.stringify(input),
    })) as RequestPost;
}

export async function closeRequestPost(requestNo: string, input: {
    actorAccountId: string;
    reason?: string
}): Promise<RequestPost> {
    requireClientSession();
    return contract(await monopolyfunFetch(`/api/v1/requests/${encodeURIComponent(requestNo)}/close`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as RequestPost;
}

export async function updateProjectPost(projectNo: string, input: {
    actorAccountId: string;
    title: string;
    description: string;
    goal?: string;
    ownerIntro?: string;
}): Promise<ProjectPost> {
    requireClientSession();
    return contract(await monopolyfunFetch(`/api/v1/projects/${encodeURIComponent(projectNo)}`, {
        method: "PATCH",
        body: JSON.stringify(input),
    })) as ProjectPost;
}

export async function createPostItem(
    postNo: string,
    input: PublishPostItemInput & { actorAccountId: string },
): Promise<PostItem> {
    requireClientSession();
    return contract(await Api.createPostItem(postNo, input)) as unknown as PostItem;
}

export async function createProjectItem(
    projectNo: string,
    input: PublishProjectItemInput & { actorAccountId: string },
): Promise<PostItem> {
    requireClientSession();
    return contract(await Api.createProjectItem(projectNo, input)) as unknown as PostItem;
}

export async function updatePostItem(
    itemId: string,
    input: Parameters<typeof createPostItem>[1],
): Promise<PostItem> {
    requireClientSession();
    return contract(await Api.updatePostItem(itemId, input)) as unknown as PostItem;
}

export async function closePostItem(itemId: string, input: {
    actorAccountId: string;
    reason?: string
}): Promise<PostItem> {
    requireClientSession();
    return contract(await monopolyfunFetch(`/api/v1/items/${encodeURIComponent(itemId)}/close`, {
        method: "POST",
        body: JSON.stringify(input),
    })) as unknown as PostItem;
}

export async function uploadDigitalInventory(itemId: string, input: {
    actorAccountId: string;
    payloads: string[];
}): Promise<DigitalInventoryUploadResult> {
    requireClientSession();
    const items = input.payloads
        .map((payload) => payload.trim())
        .filter(Boolean)
        .map((payload) => ({payload}));
    // 中文注释：数字库存接口跟随 OpenAPI 生成客户端，避免前端手写路径和后端 operationId 分叉。
    return contract(await Api.uploadDigitalInventory(itemId, {
        actorAccountId: input.actorAccountId,
        items
    })) as DigitalInventoryUploadResult;
}

export async function getDigitalInventorySummary(itemId: string, actorAccountId: string): Promise<DigitalInventorySummary> {
    requireClientSession();
    return contract(await Api.getDigitalInventorySummary(itemId, {actorAccountId})) as DigitalInventorySummary;
}

export async function revealDigitalDelivery(orderNo: string): Promise<DigitalDeliveryReveal> {
    requireClientSession();
    return contract(await Api.revealDigitalDelivery(orderNo)) as DigitalDeliveryReveal;
}

export async function claimPostItem(itemId: string, actorAccountId: string, buyerNote?: string, paymentRecipient?: string): Promise<CommandReceipt> {
    requireClientSession();
    return contract(await Api.claimPostItem(itemId, {
        actorAccountId,
        buyerNote,
        paymentRecipient,
    })) as CommandReceipt;
}

export async function claimPostItemWithDeliveryInput(
    itemId: string,
    actorAccountId: string,
    buyerNote?: string,
    deliveryInput?: JsonRecord,
    paymentRecipient?: string,
): Promise<CommandReceipt> {
    requireClientSession();
    return contract(await Api.claimPostItem(itemId, {
        actorAccountId,
        buyerNote,
        paymentRecipient,
        deliveryInput,
    })) as CommandReceipt;
}

export async function registerAccount(input: {
    handle: string;
    password: string;
}): Promise<AuthSession> {
    return contract(await Api.register(input)) as unknown as AuthSession;
}

export async function loginAccount(input: {
    handle: string;
    password: string;
}): Promise<AuthSession> {
    return contract(await Api.login(input)) as unknown as AuthSession;
}

export async function requestPasswordReset(handle: string): Promise<PasswordResetRequestResult> {
    return contract(await Api.requestPasswordReset({handle})) as PasswordResetRequestResult;
}

export async function confirmPasswordReset(input: {
    resetToken: string;
    newPassword: string;
}): Promise<AuthSession> {
    return contract(await Api.confirmPasswordReset(input)) as unknown as AuthSession;
}

export async function logoutAccount(): Promise<void> {
    await Api.logout();
}

export async function getCurrentAccount(): Promise<AuthSession> {
    // 中文注释：刷新页面时只剩 HttpOnly Cookie 也能恢复轻量登录态，前端会话只作为交互加速缓存。
    return contract(await Api.me()) as unknown as AuthSession;
}

export async function recoverCurrentAccount(): Promise<AuthSession | null> {
    const response = await fetch("/internal/auth/session", {
        headers: {Accept: "application/json"},
        credentials: "include",
        cache: "no-store",
    });

    if (response.status === 204) {
        return null;
    }

    if (!response.ok) {
        await throwApiRequestError(response, `API ${response.status} /internal/auth/session`);
    }

    // 中文注释：Shell 只需要账号摘要恢复本地会话，完整权限仍由后端接口实时判断。
    return contract(await response.json()) as unknown as AuthSession;
}

export async function hasCurrentAccountBackofficeAccess(): Promise<boolean> {
    requireClientSession();
    const rootProject = await Api.getRootProject();
    const projectNo = rootProject.projectNo?.trim();
    if (!projectNo) {
        return false;
    }
    const authority = await Api.getMyProjectAuthority(projectNo);
    // 中文注释：侧边栏后台入口跟随 Root Project 能力，和后端 BackofficeController 使用同一条权限事实。
    return authority.capabilities?.includes("backoffice.view") ?? false;
}

export async function submitOrderProgress(
    orderId: string,
    accountId: string,
    stepTitle: string,
    summary: string,
): Promise<CommandReceipt> {
    requireClientSession();
    return contract(await monopolyfunFetch<Gen.CommandReceipt>(`/api/v1/work/orders/${encodeURIComponent(orderId)}/progress`, {
        method: "POST",
        body: JSON.stringify({
            submittedByAccountId: accountId,
            stepTitle,
            summary,
            links: [],
            artifacts: [],
            progressPayload: {source: "web-ui"},
            executionMode: "AGENT",
            agentRuntime: "web-ui",
        }),
    })) as CommandReceipt;
}

export async function submitProof(
    orderId: string,
    accountId: string,
    summary: string,
    artifactRefs: string[] = [],
    criteriaRefs: string[] = [],
    decision?: "accept_original" | "close_original",
): Promise<CommandReceipt> {
    requireClientSession();
    return contract(await monopolyfunFetch<Gen.CommandReceipt>(`/api/v1/work/orders/${encodeURIComponent(orderId)}/proofs`, {
        method: "POST",
        body: JSON.stringify({
            submittedByAccountId: accountId,
            summary,
            proofPayload: {source: "web-ui", decision: decision ?? null},
            artifacts: artifactRefs,
            links: [],
            executionMode: "AGENT",
            agentRuntime: "web-ui",
            decision: decision ? decision.toUpperCase() : undefined,
            evidenceRefs: artifactRefs,
            contentHashes: [],
            criteriaRefs,
            visibility: "public",
            executionTraceRef: undefined,
        }),
    })) as CommandReceipt;
}

export async function acceptOrder(orderId: string, accountId: string): Promise<CommandReceipt> {
    requireClientSession();
    return contract(await monopolyfunFetch<Gen.CommandReceipt>(`/api/v1/work/orders/${encodeURIComponent(orderId)}/accept`, {
        method: "POST",
        body: JSON.stringify({
            acceptedByAccountId: accountId,
            note: "accepted from web ui",
        }),
    })) as CommandReceipt;
}

export async function retryInstantFulfillment(orderId: string): Promise<Order> {
    requireClientSession();
    return contract(await monopolyfunFetch(`/api/v1/work/orders/${encodeURIComponent(orderId)}/instant-fulfillment/retry`, {
        method: "POST",
    })) as unknown as Order;
}

export async function retryInstantDelivery(orderId: string): Promise<Order> {
    // 中文注释：UI 文案使用“直接发货”，后端 master 契约仍是 instant fulfillment。
    return retryInstantFulfillment(orderId);
}

export async function openDispute(orderId: string, accountId: string, reason: string, evidenceRefs: string[] = []): Promise<CommandReceipt> {
    requireClientSession();
    return contract(await monopolyfunFetch<Gen.CommandReceipt>(`/api/v1/work/orders/${encodeURIComponent(orderId)}/dispute`, {
        method: "POST",
        body: JSON.stringify({
            actorAccountId: accountId,
            reason,
            evidenceRefs,
        }),
    })) as CommandReceipt;
}

export async function cancelDispute(orderId: string, accountId: string, reason: string): Promise<CommandReceipt> {
    requireClientSession();
    return contract(await monopolyfunFetch<Gen.CommandReceipt>(`/api/v1/work/orders/${encodeURIComponent(orderId)}/cancel-dispute`, {
        method: "POST",
        body: JSON.stringify({
            actorAccountId: accountId,
            reason,
        }),
    })) as CommandReceipt;
}

export async function abandonPayment(orderId: string, accountId: string, reason = "abandoned_payment"): Promise<CommandReceipt> {
    requireClientSession();
    return contract(await monopolyfunFetch<Gen.CommandReceipt>(`/api/v1/work/orders/${encodeURIComponent(orderId)}/abandon-payment`, {
        method: "POST",
        body: JSON.stringify({
            actorAccountId: accountId,
            reason,
        }),
    })) as CommandReceipt;
}

export async function openAppeal(orderId: string, accountId: string, reason: string): Promise<CommandReceipt> {
    requireClientSession();
    return contract(await monopolyfunFetch<Gen.CommandReceipt>(`/api/v1/work/orders/${encodeURIComponent(orderId)}/appeal`, {
        method: "POST",
        body: JSON.stringify({
            actorAccountId: accountId,
            reason,
        }),
    })) as CommandReceipt;
}

export async function assignReviewer(orderId: string, actorAccountId: string, reviewerAccountId: string, reviewDueAt?: string): Promise<CommandReceipt> {
    requireClientSession();
    return contract(await monopolyfunFetch<Gen.CommandReceipt>(`/api/v1/work/orders/${encodeURIComponent(orderId)}/assign-reviewer`, {
        method: "POST",
        body: JSON.stringify({
            actorAccountId,
            reviewerAccountId,
            reviewDueAt,
        }),
    })) as CommandReceipt;
}

export async function backofficeOverrideReview(
    orderId: string,
    actorAccountId: string,
    decision: "accept_original" | "close_original",
    reason: string,
): Promise<CommandReceipt> {
    requireClientSession();
    return contract(await monopolyfunFetch<Gen.CommandReceipt>(`/api/v1/work/orders/${encodeURIComponent(orderId)}/override-review`, {
        method: "POST",
        body: JSON.stringify({
            actorAccountId,
            decision: decision.toUpperCase() as "ACCEPT_ORIGINAL" | "CLOSE_ORIGINAL",
            reason,
        }),
    })) as CommandReceipt;
}

export async function createUploadTicket(
    orderId: string,
    file: File,
    options?: {
        purpose?: "proof" | "delivery" | "dispute" | "review" | "progress";
        visibility?: "participants" | "reviewer_only" | "private"
    },
): Promise<UploadTicket> {
    const checksumSha256 = await sha256Hex(file);
    requireClientSession();
    return contract(await Api.presign({
        orderId,
        filename: file.name,
        contentType: file.type || "application/octet-stream",
        contentLengthBytes: file.size,
        checksumSha256,
        ...(options?.purpose ? {purpose: options.purpose} : {}),
        ...(options?.visibility ? {visibility: options.visibility} : {}),
    })) as UploadTicket;
}

export async function createPaymentIntent(
    orderId: string,
    accountId: string,
    input?: {
        payer?: string;
        paymentPayload?: JsonRecord;
        syncSettle?: boolean;
    },
): Promise<PaymentIntentResponse> {
    requireClientSession();
    return contract(await Api.createIntent(orderId, {
        accountId,
        ...(input?.payer ? {payer: input.payer} : {}),
        ...(input?.paymentPayload ? {paymentPayload: input.paymentPayload} : {}),
        ...(input?.syncSettle !== undefined ? {syncSettle: input.syncSettle} : {}),
    })) as PaymentIntentResponse;
}

export async function refreshPaymentIntent(intentId: string, actorAccountId: string): Promise<PaymentIntent> {
    requireClientSession();
    return contract(await Api.refreshIntent(intentId, {
        actorAccountId,
        reason: "refresh_okx_direct_pay",
    })) as PaymentIntent;
}

export async function completeUpload(ticket: UploadTicket, file: File): Promise<UploadCompletion> {
    const checksumSha256 = await sha256Hex(file);
    requireClientSession();
    return contract(await Api.complete(ticket.assetId, {
        contentType: file.type || "application/octet-stream",
        contentLengthBytes: file.size,
        checksumSha256,
    })) as UploadCompletion;
}

async function uploadArtifactWithTicket(orderId: string, file: File, options?: UploadOrderArtifactOptions): Promise<UploadCompletion> {
    const ticket = await createUploadTicket(orderId, file, options);
    await uploadDirect(ticket, file, options);
    return completeUpload(ticket, file);
}

export async function uploadOrderArtifact(orderId: string, file: File, options?: UploadOrderArtifactOptions): Promise<UploadCompletion> {
    return uploadArtifactWithTicket(orderId, file, options);
}

export async function uploadProofArtifact(orderId: string, file: File, options?: UploadOrderArtifactOptions): Promise<UploadCompletion> {
    return uploadOrderArtifact(orderId, file, options);
}

export async function uploadDisputeEvidenceArtifact(orderId: string, file: File, options?: UploadOrderArtifactOptions): Promise<UploadCompletion> {
    // 中文注释：争议附件单独标记为 dispute，后续审计和详情页能区分交付证明与争议证据。
    return uploadOrderArtifact(orderId, file, {...options, purpose: "dispute", visibility: "participants"});
}

function uploadDirect(ticket: UploadTicket, file: File, options?: UploadOrderArtifactOptions): Promise<void> {
    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        let abortHandler: (() => void) | null = null;
        xhr.open(ticket.uploadMethod, ticket.uploadUrl);
        Object.entries(ticket.uploadHeaders ?? {}).forEach(([key, value]) => xhr.setRequestHeader(key, String(value)));
        xhr.upload.onprogress = (event) => {
            if (!event.lengthComputable) {
                return;
            }
            options?.onProgress?.({
                loaded: event.loaded,
                total: event.total,
                percent: Math.round((event.loaded / event.total) * 100),
            });
        };
        xhr.onload = () => {
            cleanupAbortHandler();
            if (xhr.status >= 200 && xhr.status < 300) {
                options?.onProgress?.({loaded: file.size, total: file.size, percent: 100});
                resolve();
                return;
            }
            reject(new UiError("ui.upload.failed", {status: xhr.status}, `Upload failed with ${xhr.status}`));
        };
        xhr.onerror = () => {
            cleanupAbortHandler();
            reject(new UiError("ui.upload.failed", {status: xhr.status}, "Upload failed"));
        };
        xhr.onabort = () => {
            cleanupAbortHandler();
            reject(new UiError("ui.upload.aborted", {}, "Upload aborted"));
        };
        if (options?.signal) {
            if (options.signal.aborted) {
                xhr.abort();
                return;
            }
            abortHandler = () => xhr.abort();
            options.signal.addEventListener("abort", abortHandler, {once: true});
        }
        // 中文注释：上传进度只能由 XHR upload 事件稳定提供，fetch 仍保留给普通 API 请求。
        xhr.send(file);

        function cleanupAbortHandler() {
            if (abortHandler && options?.signal) {
                options.signal.removeEventListener("abort", abortHandler);
                abortHandler = null;
            }
        }
    });
}

export async function createUploadDownload(assetId: string): Promise<UploadDownload> {
    requireClientSession();
    return monopolyfunFetch<UploadDownload>(`/api/v1/uploads/${assetId}/download`, {
        method: "POST",
    });
}

export async function listAccountSharesLedger(accountId: string, options?: {
    limit?: number;
    cursor?: string
}): Promise<SharesLedgerEntry[]> {
    const page = await Api.getAccountSharesLedger(accountId, pageParams(options));
    return contractList(pageItems(page as unknown as PageResult<Gen.SharesLedgerEntryEntity>)) as SharesLedgerEntry[];
}

export async function listMarketSharesLedger(marketId: string, options?: {
    limit?: number;
    cursor?: string
}): Promise<SharesLedgerEntry[]> {
    const page = await Api.getMarketSharesLedger(marketId, pageParams(options));
    return contractList(pageItems(page as unknown as PageResult<Gen.SharesLedgerEntryEntity>)) as SharesLedgerEntry[];
}

export async function listPendingShareReleases(): Promise<ShareReleaseRequest[]> {
    requireClientSession();
    return contractList(await Api.listMyPendingShareReleaseRequests()) as ShareReleaseRequest[];
}

export async function getShareRelease(requestId: string, options?: {
    includeAgent?: boolean
}): Promise<ShareReleaseRequest> {
    requireClientSession();
    const query = options?.includeAgent ? "?includeAgent=true" : "";
    return contract(await monopolyfunFetch<Gen.ShareReleaseRequestView>(`/api/v1/share-release-requests/${encodeURIComponent(requestId)}${query}`)) as ShareReleaseRequest;
}

export async function approveShareRelease(requestId: string): Promise<ShareReleaseRequest> {
    requireClientSession();
    return contract(await Api.approveShareReleaseRequest(requestId)) as ShareReleaseRequest;
}

export function getStatusLabel(status: OrderStatus, _locale?: string) {
    void _locale;
    const labels: Record<string, string> = {
        claimed: "已领取",
        delivered: "已交付",
        accepted_open: "已验收",
        disputed: "争议中",
        final_accepted: "已完成",
        final_closed: "已关闭",
    };
    return labels[lower(status)] ?? status;
}

export function getDisplayPhaseLabel(displayPhase: string, _locale?: string) {
    void _locale;
    const labels: Record<string, string> = {
        delivery_result_due: "等待交付结果",
        locked_waiting_progress: "等待第一条进度",
        in_progress: "持续执行中",
        waiting_lead_acceptance: "等待负责人验收",
        accepted_window_open: "争议窗口开放中",
        review_listing_open: "评审任务已打开",
        final_accepted: "已最终完成",
        final_closed: "已关闭",
    };

    return labels[displayPhase] ?? displayPhase;
}

export function formatDate(value: string) {
    return new Intl.DateTimeFormat("zh-CN", {
        month: "long",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        timeZone: "Asia/Shanghai"
    }).format(
        new Date(value),
    );
}

async function sha256Hex(file: Blob) {
    const buffer = await file.arrayBuffer();
    return Array.from(sha256(new Uint8Array(buffer)) as Uint8Array)
        .map((value) => value.toString(16).padStart(2, "0"))
        .join("");
}
