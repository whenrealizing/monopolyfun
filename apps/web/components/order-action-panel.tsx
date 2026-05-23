"use client";

import { type ReactNode, useMemo, useState, useSyncExternalStore, useTransition } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useRouter } from "@/i18n/navigation";
import { useLocale, useTranslations } from "next-intl";
import { CheckCircle2, Copy, Eye, FileUp, PackageCheck, RefreshCw, Scale, Wallet, X, XCircle } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { useToast } from "@/components/ui/toast";
import { UiError } from "@/lib/error-messages";
import {
  acceptOrder as acceptOrderCommand,
  abandonPayment as abandonOrderPayment,
  assignReviewer as assignReviewerCommand,
  cancelDispute as cancelOrderDispute,
  completeWorkbenchItem,
  createPaymentIntent,
  refreshPaymentIntent,
  retryInstantDelivery,
  revealDigitalDelivery as revealDigitalDeliveryCommand,
  formatDate,
  openAppeal as openOrderAppeal,
  openDispute as openOrderDispute,
  backofficeOverrideReview as backofficeOverrideReviewCommand,
  submitOrderProgress,
  type Account,
  type ActionView,
  type PaymentIntent,
  uploadProofArtifact,
  uploadDisputeEvidenceArtifact,
  submitProof as submitOrderProof,
  type CommandReceipt,
  type DigitalDeliveryReveal,
  type Order,
} from "@/lib/api";
import { readStoredSession, subscribeSession } from "@/lib/client-preferences";
import { formatMajorMoney, formatMinorMoney } from "@/lib/format-money";
import { isPayerRole, orderPanelPhaseLabel, payerRoleLabel } from "@/lib/order-display";

type LocalStatus = Order["status"];
type OrderActionTranslator = ReturnType<typeof useTranslations>;
type OperationResult = {
  status: LocalStatus;
  tone: "success" | "danger";
};
type DeliveryAttachment = {
  id: string;
  file: File;
  previewUrl?: string;
};
type DisputeAttachment = DeliveryAttachment;
type EthereumProvider = {
  request(input: { method: string; params?: unknown[] }): Promise<unknown>;
};

declare global {
  interface Window {
    ethereum?: EthereumProvider;
  }
}

const XLAYER_CHAIN_ID = 196;
const XLAYER_CHAIN_HEX = "0xc4";
const WALLET_REQUEST_TIMEOUT_MS = 90_000;
const proofSubmissionSchema = z.object({
  summary: z.string().trim().min(8),
  artifactAttached: z.literal(true),
  acceptanceCount: z.number().min(1),
});
const disputeSubmissionSchema = z.object({
  reason: z.string().trim().min(1),
});
const EIP3009_TYPES = {
  TransferWithAuthorization: [
    { name: "from", type: "address" },
    { name: "to", type: "address" },
    { name: "value", type: "uint256" },
    { name: "validAfter", type: "uint256" },
    { name: "validBefore", type: "uint256" },
    { name: "nonce", type: "bytes32" },
  ],
};

function readPaymentRequirements(intent?: PaymentIntent) {
  const value = intent?.metadata?.paymentRequirements;
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
}

function readPaymentRecord(value: unknown) {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
}

function readSnapshotText(snapshot: unknown, key: string) {
  const record = readPaymentRecord(snapshot);
  const value = record?.[key];
  return typeof value === "string" && value.trim() ? value.trim() : "";
}

function paymentFailureReason(intent: PaymentIntent | undefined, t: OrderActionTranslator) {
  const rawStatus = readPaymentRecord(intent?.metadata?.rawStatus);
  const verify = readPaymentRecord(rawStatus?.verify);
  const invalidReason = typeof verify?.invalidReason === "string" ? verify.invalidReason : "";
  const invalidMessage = typeof verify?.invalidMessage === "string" ? verify.invalidMessage : "";
  if (invalidReason === "insufficient_balance" && invalidMessage) {
    return t("payment.failure.insufficientBalance", { message: invalidMessage });
  }
  return invalidMessage || invalidReason;
}

function normalizePaymentStatus(intent?: PaymentIntent) {
  return intent?.status?.toLowerCase() ?? "missing";
}

function isPaymentSettled(intent?: PaymentIntent) {
  const status = normalizePaymentStatus(intent);
  return status === "captured";
}

function isPaymentRetryable(intent?: PaymentIntent) {
  const status = normalizePaymentStatus(intent);
  return status === "failed" || status === "cancelled";
}

function paymentAmountLabel(order: Order, intent: PaymentIntent | undefined, t: OrderActionTranslator, locale: string) {
  if (intent) return formatMinorMoney(intent.amountMinor, intent.currency, locale);
  if (order.settlementType === "money" && typeof order.settlementAmount === "number") {
    return formatMajorMoney(order.settlementAmount, "USD", locale);
  }
  return t("payment.amountPending");
}

function paymentDueAt(intent?: PaymentIntent) {
  const value = intent?.metadata?.paymentDueAt;
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function clientRandomId() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

type PaymentStatusTone = "success" | "warning" | "danger" | "info";

function paymentStatusPresentation(intent: PaymentIntent | undefined, t: OrderActionTranslator): {
  tone: PaymentStatusTone;
  label: string;
  title: string;
  description: string;
  actionLabel: string;
  retryable: boolean;
} {
  const status = normalizePaymentStatus(intent);
  if (status === "captured") {
    return {
      tone: "success",
      label: t("payment.status.paid.label"),
      title: t("payment.status.paid.title"),
      description: t("payment.status.paid.description"),
      actionLabel: t("payment.status.paid.action"),
      retryable: false,
    };
  }
  if (status === "authorized") {
    return {
      tone: "warning",
      label: t("payment.status.authorized.label"),
      title: t("payment.status.authorized.title"),
      description: t("payment.status.authorized.description"),
      actionLabel: t("payment.status.authorized.action"),
      retryable: false,
    };
  }
  if (status === "failed") {
    return {
      tone: "danger",
      label: t("payment.status.failed.label"),
      title: t("payment.status.failed.title"),
      description: t("payment.status.failed.description"),
      actionLabel: t("payment.status.failed.action"),
      retryable: true,
    };
  }
  if (status === "cancelled") {
    return {
      tone: "danger",
      label: t("payment.status.cancelled.label"),
      title: t("payment.status.cancelled.title"),
      description: t("payment.status.cancelled.description"),
      actionLabel: t("payment.status.cancelled.action"),
      retryable: true,
    };
  }
  if (status === "refunded") {
    return {
      tone: "info",
      label: t("payment.status.refunded.label"),
      title: t("payment.status.refunded.title"),
      description: t("payment.status.refunded.description"),
      actionLabel: t("payment.status.refunded.action"),
      retryable: false,
    };
  }
  if (status === "disputed") {
    return {
      tone: "warning",
      label: t("payment.status.disputed.label"),
      title: t("payment.status.disputed.title"),
      description: t("payment.status.disputed.description"),
      actionLabel: t("payment.status.disputed.action"),
      retryable: false,
    };
  }
  if (status === "pending") {
    return {
      tone: "warning",
      label: t("payment.status.pending.label"),
      title: t("payment.status.pending.title"),
      description: t("payment.status.pending.description"),
      actionLabel: t("payment.status.pending.action"),
      retryable: false,
    };
  }
  return {
    tone: "info",
    label: t("payment.status.missing.label"),
    title: t("payment.status.missing.title"),
    description: t("payment.status.missing.description"),
    actionLabel: t("payment.status.missing.action"),
    retryable: false,
  };
}

function readRequirementString(requirements: Record<string, unknown>, key: string, t: OrderActionTranslator) {
  const value = requirements[key];
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(t("errors.missingPaymentRequirement", { key }));
  }
  return value.trim();
}

function accountOptionLabel(account: Account, t: OrderActionTranslator) {
  const displayName = account.displaySkin?.displayName ?? account.displayName;
  const handle = account.displaySkin?.displayHandle ?? account.handle;
  const skinSuffix = account.displaySkin?.source === "verified_identity" ? t("account.verifiedSuffix", { handle: account.handle }) : "";
  return `${displayName} · @${handle.replace(/^@+/, "")}${skinSuffix}`;
}

function readRequirementExtra(requirements: Record<string, unknown>, t: OrderActionTranslator) {
  const extra = requirements.extra;
  if (!extra || typeof extra !== "object" || Array.isArray(extra)) {
    throw new Error(t("errors.missingPaymentExtra"));
  }
  const name = (extra as Record<string, unknown>).name;
  const version = (extra as Record<string, unknown>).version;
  if (typeof name !== "string" || typeof version !== "string") {
    throw new Error(t("errors.missingPaymentExtraVersion"));
  }
  return { name, version };
}

function randomBytes32() {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return `0x${Array.from(bytes).map((byte) => byte.toString(16).padStart(2, "0")).join("")}`;
}

function walletErrorCode(error: unknown) {
  return typeof error === "object" && error !== null && "code" in error ? Number((error as { code?: unknown }).code) : undefined;
}

function walletErrorMessage(error: unknown) {
  return typeof error === "object" && error !== null && "message" in error ? String((error as { message?: unknown }).message ?? "") : "";
}

function normalizeWalletError(error: unknown, t: OrderActionTranslator, fallbackKey = "errors.walletRequestFailed") {
  const code = walletErrorCode(error);
  const message = walletErrorMessage(error).toLowerCase();
  if (message === t("errors.walletRequestTimeout").toLowerCase()) {
    return new Error(t("errors.walletRequestTimeout"));
  }
  if (code === 4001 || message.includes("reject") || message.includes("denied") || message.includes("cancel")) {
    return new Error(t("errors.walletRejected"));
  }
  return new Error(t(fallbackKey));
}

function walletRequestTimeout(t: OrderActionTranslator, fallbackKey = "errors.walletRequestTimeout") {
  return new Promise<never>((_, reject) => {
    window.setTimeout(() => reject(new Error(t(fallbackKey))), WALLET_REQUEST_TIMEOUT_MS);
  });
}

function withWalletTimeout<T>(request: Promise<T>, t: OrderActionTranslator, fallbackKey = "errors.walletRequestFailed") {
  return Promise.race([request, walletRequestTimeout(t, fallbackKey)]);
}

async function switchToXLayer(provider: EthereumProvider, t: OrderActionTranslator) {
  try {
    await withWalletTimeout(provider.request({ method: "wallet_switchEthereumChain", params: [{ chainId: XLAYER_CHAIN_HEX }] }), t, "errors.walletSwitchFailed");
  } catch (caught) {
    if (walletErrorCode(caught) !== 4902) throw normalizeWalletError(caught, t, "errors.walletSwitchFailed");
    try {
      await withWalletTimeout(provider.request({
        method: "wallet_addEthereumChain",
        params: [{
          chainId: XLAYER_CHAIN_HEX,
          chainName: "X Layer",
          nativeCurrency: { name: "OKB", symbol: "OKB", decimals: 18 },
          rpcUrls: ["https://rpc.xlayer.tech", "https://xlayerrpc.okx.com"],
          blockExplorerUrls: ["https://www.oklink.com/xlayer"],
        }],
      }), t, "errors.walletSwitchFailed");
      await withWalletTimeout(provider.request({ method: "wallet_switchEthereumChain", params: [{ chainId: XLAYER_CHAIN_HEX }] }), t, "errors.walletSwitchFailed");
    } catch (addOrSwitchError) {
      throw normalizeWalletError(addOrSwitchError, t, "errors.walletSwitchFailed");
    }
  }
}

async function signX402Payload(provider: EthereumProvider, account: string, requirements: Record<string, unknown>, t: OrderActionTranslator) {
  const nowSeconds = Math.floor(Date.now() / 1000);
  const maxTimeout = typeof requirements.maxTimeoutSeconds === "number" ? requirements.maxTimeoutSeconds : 300;
  const authorization = {
    from: account,
    to: readRequirementString(requirements, "payTo", t),
    value: readRequirementString(requirements, "amount", t),
    validAfter: String(Math.max(0, nowSeconds - 5)),
    validBefore: String(nowSeconds + maxTimeout),
    nonce: randomBytes32(),
  };
  const extra = readRequirementExtra(requirements, t);
  const typedData = {
    domain: {
      name: extra.name,
      version: extra.version,
      chainId: XLAYER_CHAIN_ID,
      verifyingContract: readRequirementString(requirements, "asset", t),
    },
    types: {
      EIP712Domain: [
        { name: "name", type: "string" },
        { name: "version", type: "string" },
        { name: "chainId", type: "uint256" },
        { name: "verifyingContract", type: "address" },
      ],
      ...EIP3009_TYPES,
    },
    primaryType: "TransferWithAuthorization",
    message: authorization,
  };
  // 中文注释：签名 payload 保持 x402 标准结构，服务端负责确认支付并写回订单状态。
  let signature: unknown;
  try {
    signature = await withWalletTimeout(provider.request({
      method: "eth_signTypedData_v4",
      params: [account, JSON.stringify(typedData)],
    }), t, "errors.walletSignFailed");
  } catch (caught) {
    throw normalizeWalletError(caught, t, "errors.walletSignFailed");
  }
  if (typeof signature !== "string" || !signature) throw new Error(t("errors.walletSignFailed"));
  return {
    x402Version: 2,
    accepted: requirements,
    payload: { authorization, signature },
  };
}

export function OrderActionPanel({
  order,
  availableActions,
  reviewerCandidates,
  paymentIntent,
  showDisputeManagement = true,
  showDisputeManagementHeading = true,
  showActionSummary = true,
  showOrderActions = true,
}: {
  order: Order;
  availableActions: ActionView[];
  reviewerCandidates: Account[];
  paymentIntent?: PaymentIntent;
  showDisputeManagement?: boolean;
  showDisputeManagementHeading?: boolean;
  showActionSummary?: boolean;
  showOrderActions?: boolean;
}) {
  const t = useTranslations("Orders.actionPanel");
  const locale = useLocale() as "zh-CN" | "en";
  const router = useRouter();
  const toast = useToast();
  const [isPending, startTransition] = useTransition();
  const [status, setStatus] = useState<LocalStatus>(order.status);
  const [progressStepTitle, setProgressStepTitle] = useState("");
  const [progressSummary, setProgressSummary] = useState("");
  const proofForm = useForm<{ summary: string }>({ defaultValues: { summary: "" } });
  const [summary, setSummary] = useState("");
  const [autoDeliverySummary, setAutoDeliverySummary] = useState("");
  const [autoDeliveryUrl, setAutoDeliveryUrl] = useState("");
  const [autoDeliveryAttachments, setAutoDeliveryAttachments] = useState<DeliveryAttachment[]>([]);
  const [artifactFile, setArtifactFile] = useState<File | null>(null);
  const [reviewDecision, setReviewDecision] = useState<"accept_original" | "close_original">("accept_original");
  const [reviewerAccountId, setReviewerAccountId] = useState("");
  const [overrideReason, setOverrideReason] = useState("");
  const [appealReason, setAppealReason] = useState(t("defaults.appealReason"));
  const [cancelDisputeReason, setCancelDisputeReason] = useState(t("defaults.cancelDisputeReason"));
  const [showDisputeForm, setShowDisputeForm] = useState(false);
  const [disputeReason, setDisputeReason] = useState("");
  const [disputeEvidenceUrl, setDisputeEvidenceUrl] = useState("");
  const [disputeAttachments, setDisputeAttachments] = useState<DisputeAttachment[]>([]);
  const [confirmingDispute, setConfirmingDispute] = useState(false);
  const [confirmingAbandonPayment, setConfirmingAbandonPayment] = useState(false);
  const [confirmingCancelDispute, setConfirmingCancelDispute] = useState(false);
  const [confirmingOverrideDecision, setConfirmingOverrideDecision] = useState<"accept_original" | "close_original" | null>(null);
  const [showCancelDisputeDialog, setShowCancelDisputeDialog] = useState(false);
  const [showAppealDialog, setShowAppealDialog] = useState(false);
  const [showOverrideDialog, setShowOverrideDialog] = useState(false);
  const [operationResult, setOperationResult] = useState<OperationResult | null>(null);
  const [localPaymentIntent, setLocalPaymentIntent] = useState<PaymentIntent | undefined>(paymentIntent);
  const [paymentBusyAction, setPaymentBusyAction] = useState<"start" | "refresh" | "pay" | null>(null);
  const [deliverySubmitting, setDeliverySubmitting] = useState(false);
  const [revealedDelivery, setRevealedDelivery] = useState<DigitalDeliveryReveal | null>(null);
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  const session = useSyncExternalStore(subscribeSession, readStoredSession, () => null);
  const actionById = useMemo(() => new Map(availableActions.map((action) => [action.id, action])), [availableActions]);
  const hasAction = (id: ActionView["id"]) => Boolean(id && actionById.has(id));
  const actionReason = (id: ActionView["id"]) => id ? actionById.get(id)?.disabledReason ?? "" : "";
  const currentPaymentIntent = localPaymentIntent ?? paymentIntent;
  const isOkxPayment = order.paymentMethod === "okx_direct_pay" || currentPaymentIntent?.provider === "okx";
  const isMoneyOrder = String(order.settlementType ?? "").toLowerCase() === "money";
  const isReviewOrder = order.postKind === "review" || Boolean(order.parentOrderId);
  const acceptanceCriteria = Array.isArray(order.acceptanceCriteriaSnapshot) ? order.acceptanceCriteriaSnapshot : [];
  const paymentStatusView = paymentStatusPresentation(currentPaymentIntent, t);
  const paymentSettled = isPaymentSettled(currentPaymentIntent);
  const canCompleteMoneyPayment = hasAction("complete_money_payment");
  const canStartPayment = canCompleteMoneyPayment && !paymentSettled;
  const isFinal = status === "final_closed" || status === "final_accepted";
  const showPaymentOperation = showOrderActions && isMoneyOrder && (canCompleteMoneyPayment || (isPayerRole(order.currentAccountRole) && currentPaymentIntent && !paymentSettled));
  const payerLabel = order.postKind === "request" ? t("payment.payer.request") : t("payment.payer.buyer");
  const autoDeliveryBlockedReason = isMoneyOrder && !paymentSettled ? t("autoDelivery.waitForPayment", { payer: payerLabel }) : actionReason("submit_delivery_result");
  const showDeliveryResultForm = showOrderActions && hasAction("submit_delivery_result") && !autoDeliveryBlockedReason;
  const showDeliveryBlockedNotice = showOrderActions && hasAction("submit_delivery_result") && Boolean(autoDeliveryBlockedReason);
  const showProgressForm = showOrderActions && hasAction("submit_progress") && !actionReason("submit_progress") && !showDeliveryBlockedNotice;
  const showActionIntro = showActionSummary && !showPaymentOperation && !showDeliveryResultForm && (showDisputeManagement || status !== "disputed");
  const paymentDisabledReason = paymentSettled
    ? t("payment.disabled.settled")
    : actionReason("complete_money_payment")
    || (!canStartPayment && currentPaymentIntent ? t("payment.disabled.payerOnly", { payer: payerLabel }) : "");
  const visibleActions = useMemo(() => {
    return availableActions.filter((action) => {
      if (action.id !== "open_dispute" || status !== "delivered") return true;
      return isPayerRole(order.currentAccountRole) || action.role === "authority";
    });
  }, [availableActions, order.currentAccountRole, status]);
  const visibleActionById = useMemo(() => new Map(visibleActions.map((action) => [action.id, action])), [visibleActions]);
  const hasVisibleAction = (id: ActionView["id"]) => Boolean(id && visibleActionById.has(id));
  const visibleActionReason = (id: ActionView["id"]) => id ? visibleActionById.get(id)?.disabledReason ?? "" : "";
  const disputeReasonMissing = disputeReason.trim().length === 0;
  const emptyActionText = status === "disputed" ? t("disputeManagement.waitingReviewer") : t("emptyActions");
  const hasDisputeManagementAction = hasAction("cancel_dispute") || hasAction("assign_reviewer") || hasAction("override_accept_original") || hasAction("override_close_original") || hasAction("open_appeal");
  const assignReviewerBlockedReason = actionReason("assign_reviewer")
    || (reviewerCandidates.length === 0 ? t("disputeManagement.noReviewers") : "")
    || (!reviewerAccountId ? t("disputeManagement.chooseReviewerFirst") : "");
  const stockPayloadPreview = readSnapshotText(order.deliveryPayload, "payloadPreview");
  const hasStockDeliverySnapshot = order.deliveryMode === "stock_fulfillment" && Boolean(order.deliveryReceipt || stockPayloadPreview);
  const abandonPaymentControl = showOrderActions && hasAction("abandon_payment") ? (
    <div className="relative inline-flex">
      {actionReason("abandon_payment") ? <ActionDisabledReason reason={actionReason("abandon_payment")} /> : null}
      <Button type="button" size="sm" variant="outline" disabled={Boolean(actionReason("abandon_payment")) || isPending} onClick={() => setConfirmingAbandonPayment(true)}>
        {t("payment.abandon")}
      </Button>
      {confirmingAbandonPayment ? (
        <ConfirmActionPopover
          title={t("payment.abandonConfirmTitle")}
          description={t("payment.abandonConfirmDescription")}
          cancelLabel={t("payment.abandonCancel")}
          confirmLabel={t("payment.abandonConfirm")}
          confirmVariant="outlineAccent"
          busy={isPending}
          onCancel={() => setConfirmingAbandonPayment(false)}
          onConfirm={abandonPayment}
        />
      ) : null}
    </div>
  ) : null;
  function submitProof() {
    runCommand(async () => {
      if (!session?.accountId) {
        throw new UiError("auth.required");
      }
      proofSubmissionSchema.parse({
        summary,
        artifactAttached: Boolean(artifactFile),
        acceptanceCount: acceptanceCriteria.length,
      });
      let artifactRefs: string[] = [];
      if (artifactFile) {
        setUploadProgress(0);
        const completed = await uploadProofArtifact(order.orderNo, artifactFile, {
          onProgress: (progress) => setUploadProgress(progress.percent),
        });
        artifactRefs = [completed.artifactRef];
      }
      // 中文注释：proof 默认引用订单快照里的完成标准，agent 和人工提交都按同一组 criteria 判定。
      return submitOrderProof(order.orderNo, session.accountId, summary, artifactRefs, acceptanceCriteria, isReviewOrder ? reviewDecision : undefined);
    });
  }

  function submitProgress() {
    runCommand(async () => {
      if (!session?.accountId) {
        throw new UiError("auth.required");
      }
      const receipt = await submitOrderProgress(order.orderNo, session.accountId, progressStepTitle, progressSummary);
      setProgressStepTitle("");
      setProgressSummary("");
      return receipt;
    });
  }

  function acceptOrder() {
    runCommand(() => {
      if (!session?.accountId) {
        throw new UiError("auth.required");
      }
      return acceptOrderCommand(order.orderNo, session.accountId);
    });
  }

  function abandonPayment() {
    runCommand(() => {
      if (!session?.accountId) {
        throw new UiError("auth.required");
      }
      setConfirmingAbandonPayment(false);
      return abandonOrderPayment(order.orderNo, session.accountId);
    });
  }

  function startPaymentSession() {
    runPaymentOperation("start", async () => {
      if (!session?.accountId) throw new UiError("auth.required");
      const response = await createPaymentIntent(order.orderNo, session.accountId);
      return response.paymentIntent;
    });
  }

  function openDispute() {
    runCommand(async () => {
      if (!session?.accountId) {
        throw new UiError("auth.required");
      }
      disputeSubmissionSchema.parse({ reason: disputeReason });
      const evidenceRefs: string[] = [];
      const evidenceUrl = disputeEvidenceUrl.trim();
      if (evidenceUrl) evidenceRefs.push(evidenceUrl);
      const artifactRefs = await Promise.all(disputeAttachments.map(async (attachment) => (await uploadDisputeEvidenceArtifact(order.orderNo, attachment.file, {
        onProgress: (progress) => setUploadProgress(progress.percent),
      })).artifactRef));
      evidenceRefs.push(...artifactRefs);
      const receipt = await openOrderDispute(order.orderNo, session.accountId, disputeReason.trim(), evidenceRefs);
      setShowDisputeForm(false);
      setConfirmingDispute(false);
      return receipt;
    });
  }

  function openAppeal() {
    runCommand(async () => {
      if (!session?.accountId) {
        throw new UiError("auth.required");
      }
      const receipt = await openOrderAppeal(order.orderNo, session.accountId, appealReason);
      setShowAppealDialog(false);
      return receipt;
    });
  }

  function cancelDispute() {
    runCommand(async () => {
      if (!session?.accountId) {
        throw new UiError("auth.required");
      }
      const receipt = await cancelOrderDispute(order.orderNo, session.accountId, cancelDisputeReason);
      setShowCancelDisputeDialog(false);
      setConfirmingCancelDispute(false);
      return receipt;
    });
  }

  function completeAutoDelivery() {
    setDeliverySubmitting(true);
    runCommand(async () => {
      try {
        if (!session?.accountId) {
          throw new UiError("auth.required");
        }
        const artifactRefs = await Promise.all(autoDeliveryAttachments.map(async (attachment) => (await uploadProofArtifact(order.orderNo, attachment.file, {
          onProgress: (progress) => setUploadProgress(progress.percent),
        })).artifactRef));
        const deliveryUrl = autoDeliveryUrl.trim();
        const links = deliveryUrl ? [{ label: t("autoDelivery.deliveryLink"), href: deliveryUrl }] : [];
        // 中文注释：订单页复用 workbench 完成入口，agent 和人工调试走同一条交付回写链路。
        return completeWorkbenchItem(`wb-delivery-result-${order.orderNo}`, {
          actorAccountId: session.accountId,
          deliverySummary: autoDeliverySummary,
          deliveryPayload: {
            source: "web-ui",
            ...(deliveryUrl ? { url: deliveryUrl } : {}),
            ...(artifactRefs.length > 0 ? { artifacts: artifactRefs } : {}),
          },
          deliveryReceipt: {
            source: "order_page",
            completedBy: session.accountId,
            ...(artifactRefs.length > 0 ? { artifacts: artifactRefs } : {}),
          },
          links,
          artifacts: artifactRefs,
          agentRuntime: order.agentRuntimeId,
        });
      } finally {
        setDeliverySubmitting(false);
      }
    });
  }

  function retryDirectDelivery() {
    runOrderRefresh(async () => {
      if (!session?.accountId) {
        throw new UiError("auth.required");
      }
      // 中文注释：直接发货重试沿用订单幂等键，前端只触发服务端状态机继续推进。
      return retryInstantDelivery(order.orderNo);
    });
  }

  function revealStockDelivery() {
    startTransition(async () => {
      try {
        // 中文注释：托管库存明文只在订单参与方主动 reveal 时读取，订单快照继续只展示 preview。
        setRevealedDelivery(await revealDigitalDeliveryCommand(order.orderNo));
      } catch (caught) {
        toast.notifyError(caught, "ui.order.command.failed");
      }
    });
  }

  async function copyRevealedDelivery() {
    if (!revealedDelivery?.payload) return;
    try {
      if (!navigator.clipboard?.writeText) throw new Error("clipboard unavailable");
      await navigator.clipboard.writeText(revealedDelivery.payload);
      toast.notify({ tone: "success", title: t("stockDelivery.copySuccess") });
    } catch {
      toast.notify({ tone: "error", title: t("stockDelivery.copyFailed") });
    }
  }

  function refreshCurrentPaymentStatus() {
    runPaymentOperation("refresh", async () => {
      if (!session?.accountId) throw new UiError("auth.required");
      if (!currentPaymentIntent?.id) throw new Error(t("errors.createPaymentFirst"));
      return refreshPaymentIntent(currentPaymentIntent.id, session.accountId);
    });
  }

  function signAndSubmitOkxPayment() {
    runPaymentOperation("pay", async () => {
      if (!session?.accountId) throw new UiError("auth.required");
      if (!window.ethereum) throw new Error(t("errors.walletMissing"));
      const prepared = currentPaymentIntent ?? (await createPaymentIntent(order.orderNo, session.accountId)).paymentIntent;
      const requirements = readPaymentRequirements(prepared);
      if (!requirements) throw new Error(t("errors.requirementsMissing"));
      let accounts: string[];
      try {
        accounts = await withWalletTimeout(window.ethereum.request({ method: "eth_requestAccounts" }), t, "errors.walletConnectFailed") as string[];
      } catch (caught) {
        throw normalizeWalletError(caught, t, "errors.walletConnectFailed");
      }
      const account = accounts[0];
      if (!account) throw new Error(t("errors.walletAccountMissing"));
      await switchToXLayer(window.ethereum, t);
      const paymentPayload = await signX402Payload(window.ethereum, account, requirements, t);
      const response = await createPaymentIntent(order.orderNo, session.accountId, { payer: account, paymentPayload, syncSettle: true });
      return response.paymentIntent;
    });
  }

  function assignReviewer() {
    runCommand(() => {
      if (!session?.accountId) {
        throw new UiError("auth.required");
      }
      if (!reviewerAccountId) {
        throw new UiError("ui.order.reviewer.required");
      }
      return assignReviewerCommand(order.orderNo, session.accountId, reviewerAccountId);
    });
  }

  function overrideReview(decision: "accept_original" | "close_original") {
    runCommand(async () => {
      if (!session?.accountId) {
        throw new UiError("auth.required");
      }
      const receipt = await backofficeOverrideReviewCommand(order.orderNo, session.accountId, decision, overrideReason);
      setShowOverrideDialog(false);
      setConfirmingOverrideDecision(null);
      return receipt;
    });
  }

  function runCommand(command: () => Promise<CommandReceipt>) {
    startTransition(async () => {
      try {
        const nextReceipt = await command();
        const nextStatus = nextReceipt.status.toLowerCase() as LocalStatus;
        setOperationResult({
          status: nextStatus,
          tone: nextStatus === "disputed" || nextStatus === "final_closed" ? "danger" : "success",
        });
        setStatus(nextStatus);
        router.refresh();
      } catch (caught) {
        toast.notifyError(caught, "ui.order.command.failed");
      } finally {
        setUploadProgress(null);
      }
    });
  }

  function runPaymentOperation(action: "start" | "refresh" | "pay", command: () => Promise<PaymentIntent>) {
    setPaymentBusyAction(action);
    startTransition(async () => {
      try {
        const intent = await command();
        setLocalPaymentIntent(intent);
        const nextStatusView = paymentStatusPresentation(intent, t);
        const amount = paymentAmountLabel(order, intent, t, locale);
        if (isPaymentSettled(intent)) {
          toast.notify({
            tone: "success",
            title: nextStatusView.title,
            description: t("payment.syncedDescription", { status: nextStatusView.label, amount }),
          });
        } else if (action === "refresh") {
          toast.notify({
            tone: "info",
            title: t("payment.synced"),
            description: t("payment.syncedDescription", { status: nextStatusView.label, amount }),
          });
        }
        // 中文注释：支付操作展示真实支付状态，不伪造命令回执、追踪或审计编号。
        setOperationResult(null);
        router.refresh();
      } catch (caught) {
        toast.notifyError(caught, "ui.order.command.failed");
      } finally {
        setPaymentBusyAction(null);
      }
    });
  }

  function runOrderRefresh(command: () => Promise<Order>) {
    setOperationResult(null);
    startTransition(async () => {
      try {
        const refreshed = await command();
        setStatus(refreshed.status);
        router.refresh();
      } catch (caught) {
        toast.notifyError(caught, "ui.order.command.failed");
      }
    });
  }

  if (isFinal && availableActions.length === 0 && !operationResult && !hasStockDeliverySnapshot) {
    return null;
  }

  return (
    <div className={showPaymentOperation ? "bg-[var(--background)]" : "bg-[var(--background)] py-4"}>
      {showActionIntro ? (
        <div className="flex items-start justify-between gap-3 px-1">
          <div>
            <div className="text-sm font-medium text-[var(--foreground)]">{t("heading")}</div>
            <div className="mt-1 text-sm leading-6 text-[var(--muted-foreground)]">
              {orderPanelPhaseLabel(order, currentPaymentIntent, status, locale)}
            </div>
          </div>
          {!isFinal && order.nextProgressDueAt ? (
            <div className="text-xs leading-5 text-[var(--muted-foreground)]">
              {t("nextProgressDue", { date: formatDate(order.nextProgressDueAt) })}
            </div>
          ) : null}
        </div>
      ) : null}

      {showOrderActions && hasAction("submit_proof") ? (
        <div className="mt-4 space-y-3">
          <label className="block">
            <span className="text-sm font-medium text-[var(--foreground)]">{t("proof.summary")}</span>
            <textarea
              className="mf-control-field mt-2 min-h-28 w-full resize-none px-3 py-3 leading-6"
              suppressHydrationWarning
              value={summary}
              onChange={(event) => {
                setSummary(event.target.value);
                proofForm.setValue("summary", event.target.value, { shouldDirty: true, shouldValidate: true });
              }}
              placeholder={t("proof.summaryPlaceholder")}
            />
          </label>
          {isReviewOrder ? (
            <label className="block">
              <span className="text-[11px] font-medium uppercase text-[var(--muted-foreground)]">{t("proof.reviewDecision")}</span>
              <Select value={reviewDecision} onValueChange={(value) => setReviewDecision(value as "accept_original" | "close_original")}>
                <SelectTrigger className="mt-2">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="accept_original">{t("decision.acceptOriginal")}</SelectItem>
                  <SelectItem value="close_original">{t("decision.closeOriginal")}</SelectItem>
                </SelectContent>
              </Select>
            </label>
          ) : null}
          {acceptanceCriteria.length > 0 ? (
            <div className="px-1">
              <div className="text-xs text-[var(--muted-foreground)]">{t("proof.acceptanceCriteria")}</div>
              <ul className="mt-2 grid gap-1 text-xs leading-5 text-[var(--muted-foreground)]">
                {acceptanceCriteria.map((criterion) => (
                  <li key={criterion}>- {criterion}</li>
                ))}
              </ul>
            </div>
          ) : null}
          <label className="block">
            <span className="text-[11px] font-medium uppercase text-[var(--muted-foreground)]">{t("proof.attachment")}</span>
            <input
              className="mf-control-field mt-2 w-full px-3 py-3"
              suppressHydrationWarning
              type="file"
              accept="image/png,image/jpeg,image/webp,application/pdf"
              onChange={(event) => setArtifactFile(event.target.files?.[0] ?? null)}
            />
            <div className="mt-2 text-xs text-[var(--muted-foreground)]">
              {t("proof.attachmentHint")}
            </div>
            {uploadProgress !== null ? (
              <div className="mt-2 h-2 overflow-hidden rounded-sm bg-[var(--muted)]">
                <div className="h-full bg-[var(--primary)] transition-[width]" style={{ width: `${uploadProgress}%` }} />
              </div>
            ) : null}
          </label>
          {!artifactFile ? <ActionDisabledReason reason={t("proof.attachmentRequired")} /> : null}
          {actionReason("submit_proof") ? <ActionDisabledReason reason={actionReason("submit_proof")} /> : null}
          <Button onClick={submitProof} disabled={summary.trim().length < 8 || !artifactFile || acceptanceCriteria.length === 0 || Boolean(actionReason("submit_proof")) || isPending} loading={isPending} variant="primary">
            <FileUp className="h-4 w-4" />
            {t("proof.submit")}
          </Button>
        </div>
      ) : null}

      {showPaymentOperation ? (
        <div className="mt-4 max-w-3xl">
          {showActionSummary ? (
            <div>
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs leading-5">
                <span className="text-[var(--muted-foreground)]">{locale === "en" ? "Current status" : "当前状态"}</span>
                <span className="font-medium text-[var(--accent-blue)]">
                  {orderPanelPhaseLabel(order, currentPaymentIntent, status, locale)}
                </span>
              </div>
              <div className="mt-2 space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="mf-chip">{paymentStatusView.label}</span>
                  <span className="text-base font-medium leading-6 text-[var(--foreground)]">{paymentStatusView.title}</span>
                </div>
                <p className="max-w-2xl text-sm leading-6 text-[var(--muted-foreground)]">{paymentStatusView.description}</p>
              </div>
            </div>
          ) : null}
          <div className={`${showActionSummary ? "mt-4" : ""} flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between`}>
            <PaymentStatusSummary order={order} intent={currentPaymentIntent} paymentDueAt={!paymentSettled ? paymentDueAt(currentPaymentIntent) : undefined} t={t} locale={locale} />
            <div className="shrink-0">
              {isOkxPayment ? (
                <OkxPaymentControls
                  intent={currentPaymentIntent}
                  statusView={paymentStatusView}
                busy={isPending}
                busyAction={paymentBusyAction}
                locale={locale}
                onRefresh={refreshCurrentPaymentStatus}
                onSignAndSubmit={signAndSubmitOkxPayment}
                  canStart={canStartPayment}
                  disabledReason={paymentDisabledReason}
                  t={t}
                  trailingAction={abandonPaymentControl}
                />
              ) : (
                <>
                  <div className="flex flex-wrap justify-end gap-2">
                    {currentPaymentIntent ? (
                      <Button size="sm" type="button" variant="outline" onClick={refreshCurrentPaymentStatus} disabled={isPending} loading={paymentBusyAction === "refresh"}>
                        <RefreshCw className="h-4 w-4" />
                        {locale === "en" ? "Refresh" : "刷新"}
                      </Button>
                    ) : null}
                    {!currentPaymentIntent && hasAction("complete_money_payment") ? (
                      <>
                        {actionReason("complete_money_payment") ? <ActionDisabledReason reason={actionReason("complete_money_payment")} /> : null}
                        <Button size="sm" onClick={startPaymentSession} disabled={Boolean(actionReason("complete_money_payment")) || isPending} loading={isPending} variant="outline">
                          {t("payment.createSession")}
                        </Button>
                      </>
                    ) : null}
                    {abandonPaymentControl}
                  </div>
                  <div className="mt-2 text-xs leading-5 text-right text-[var(--muted-foreground)]">{t("payment.backendSessionHint")}</div>
                </>
              )}
            </div>
          </div>
        </div>
      ) : null}

      {showDeliveryBlockedNotice ? (
        <div className="mt-4 bg-[rgba(240,180,95,0.1)] px-3 py-3">
          <div className="text-sm font-medium text-[var(--foreground)]">{t("autoDelivery.heading")}</div>
          <div className="mt-1 text-sm leading-6 text-[var(--muted-foreground)]">{autoDeliveryBlockedReason}</div>
        </div>
      ) : null}

      {showDeliveryResultForm ? (
        <div className="mt-4 space-y-4 px-1">
          <div>
            <div className="text-base font-medium text-[var(--foreground)]">{t("autoDelivery.heading")}</div>
            <p className="mt-1 text-sm leading-6 text-[var(--muted-foreground)]">{t("autoDelivery.description")}</p>
          </div>
          <label className="block">
            <span className="text-sm font-medium text-[var(--foreground)]">{t("autoDelivery.summary")}</span>
              <textarea
                className="mf-control-field mt-2 min-h-24 w-full resize-none px-3 py-3 leading-6"
                suppressHydrationWarning
                value={autoDeliverySummary}
                onChange={(event) => setAutoDeliverySummary(event.target.value)}
                placeholder={t("autoDelivery.summaryPlaceholder", { reviewer: payerRoleLabel(order.postKind, locale) })}
              />
            </label>
          <label className="block">
            <span className="text-sm font-medium text-[var(--foreground)]">
              {t("autoDelivery.url")} <span className="text-[var(--muted-foreground)]">{t("optional")}</span>
            </span>
              <input
                className="mf-control-field mt-2 w-full px-3"
                suppressHydrationWarning
                value={autoDeliveryUrl}
                onChange={(event) => setAutoDeliveryUrl(event.target.value)}
                placeholder={t("autoDelivery.urlPlaceholder", { reviewer: payerRoleLabel(order.postKind, locale) })}
              />
          </label>
          <label className="block">
            <span className="text-sm font-medium text-[var(--foreground)]">
              {t("autoDelivery.attachment")} <span className="text-[var(--muted-foreground)]">{t("optional")}</span>
            </span>
            <span className="mf-control-field mt-2 flex min-h-10 cursor-pointer items-center justify-between gap-3 px-3 text-sm">
              <span className={autoDeliveryAttachments.length ? "truncate text-[var(--foreground)]" : "truncate text-[var(--muted-foreground)]"}>
                {autoDeliveryAttachments.length ? t("autoDelivery.attachmentCount", { count: autoDeliveryAttachments.length }) : t("autoDelivery.noAttachment")}
              </span>
              <span className="inline-flex h-8 shrink-0 items-center gap-2 rounded-[10px] border border-[var(--border)] bg-[var(--background)] px-3 text-xs font-medium text-[var(--foreground)] transition-colors hover:bg-[rgb(30,31,33)]">
                <FileUp className="h-3.5 w-3.5" />
                {t("autoDelivery.chooseAttachment")}
              </span>
              <input
                className="sr-only"
                suppressHydrationWarning
                type="file"
                multiple
                accept="image/png,image/jpeg,image/webp,application/pdf"
                onChange={(event) => {
                  const selected = Array.from(event.target.files ?? []);
                  selected.forEach((file) => {
                    const id = `${file.name}-${file.lastModified}-${clientRandomId()}`;
                    const attachment: DeliveryAttachment = { id, file };
                    setAutoDeliveryAttachments((attachments) => [...attachments, attachment]);
                    if (file.type.startsWith("image/")) {
                      const reader = new FileReader();
                      reader.onload = () => {
                        setAutoDeliveryAttachments((attachments) => attachments.map((item) => (
                          item.id === id ? { ...item, previewUrl: typeof reader.result === "string" ? reader.result : undefined } : item
                        )));
                      };
                      reader.readAsDataURL(file);
                    }
                  });
                  event.currentTarget.value = "";
                }}
              />
            </span>
            {autoDeliveryAttachments.length > 0 ? (
              <div className="mt-3 grid max-w-2xl gap-2 sm:grid-cols-2">
                {autoDeliveryAttachments.map((attachment) => (
                  <div key={attachment.id} className="flex min-w-0 max-w-[20rem] items-center gap-3 rounded-[12px] border border-[var(--border)] bg-[var(--background)] p-2">
                    {attachment.previewUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={attachment.previewUrl} alt="" className="h-12 w-12 shrink-0 rounded-[8px] object-cover" />
                    ) : (
                      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-[8px] bg-[rgb(33,34,37)] text-[var(--muted-foreground)]">
                        <FileUp className="h-4 w-4" />
                      </div>
                    )}
                    <div className="min-w-0 max-w-[calc(100%-6rem)] flex-1">
                      <div className="block max-w-full overflow-hidden text-ellipsis whitespace-nowrap text-sm text-[var(--foreground)]" title={attachment.file.name}>{attachment.file.name}</div>
                      <div className="mt-0.5 text-xs text-[var(--muted-foreground)]">{formatFileSize(attachment.file.size)}</div>
                    </div>
                    <button
                      type="button"
                      className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] text-[var(--muted-foreground)] transition-colors hover:bg-[rgb(30,31,33)] hover:text-[var(--foreground)]"
                      onClick={() => setAutoDeliveryAttachments((attachments) => attachments.filter((item) => item.id !== attachment.id))}
                      aria-label={t("autoDelivery.removeAttachment")}
                    >
                      <X className="h-4 w-4" />
                    </button>
                  </div>
                ))}
              </div>
            ) : null}
            <div className="mt-2 text-xs text-[var(--muted-foreground)]">
              {t("autoDelivery.attachmentHint")}
            </div>
          </label>
          {autoDeliveryBlockedReason ? <ActionDisabledReason reason={autoDeliveryBlockedReason} /> : null}
          <div className="flex">
            <Button onClick={completeAutoDelivery} disabled={!autoDeliverySummary.trim() || Boolean(autoDeliveryBlockedReason) || deliverySubmitting || isPending} loading={deliverySubmitting || isPending} variant="primary">
              <FileUp className="h-4 w-4" />
              {t("autoDelivery.submit", { reviewer: payerRoleLabel(order.postKind, locale) })}
            </Button>
          </div>
        </div>
      ) : null}

      {showProgressForm ? (
        <div className="mt-6 space-y-3 border-t border-[rgba(255,255,255,0.08)] px-1 pt-4">
          <div>
            <div className="text-sm font-medium text-[var(--foreground)]">{t("progress.heading")}</div>
            <p className="mt-1 text-sm leading-6 text-[var(--muted-foreground)]">{t("progress.description")}</p>
          </div>
          <input
            className="mf-control-field w-full px-3"
            suppressHydrationWarning
            value={progressStepTitle}
            onChange={(event) => setProgressStepTitle(event.target.value)}
            placeholder={t("progress.titlePlaceholder")}
          />
          <textarea
            className="mf-control-field min-h-24 w-full resize-none px-3 py-3 leading-6"
            suppressHydrationWarning
            value={progressSummary}
            onChange={(event) => setProgressSummary(event.target.value)}
            placeholder={t("progress.summaryPlaceholder")}
          />
          {actionReason("submit_progress") ? <ActionDisabledReason reason={actionReason("submit_progress")} /> : null}
          <Button onClick={submitProgress} disabled={progressStepTitle.trim().length < 2 || progressSummary.trim().length < 6 || Boolean(actionReason("submit_progress")) || isPending} loading={isPending} variant="outline">
            {t("progress.submit")}
          </Button>
        </div>
      ) : null}

      {showOrderActions && order.deliveryMode === "instant_fulfillment" ? (
        <div className="mt-4 rounded-[12px] bg-[rgba(72,108,230,0.08)] px-3 py-3">
          <div className="flex items-start gap-2">
            <RefreshCw className="mt-0.5 h-4 w-4 shrink-0 text-[var(--accent-blue)]" />
            <div>
              <div className="text-sm font-medium text-[var(--foreground)]">{t("instantDelivery.heading")}</div>
              <div className="mt-1 text-sm leading-6 text-[var(--muted-foreground)]">
                {order.deliveryReceipt ? t("instantDelivery.done") : t("instantDelivery.waiting")}
              </div>
            </div>
          </div>
          <div className="mt-3 text-xs leading-5 text-[var(--muted-foreground)]">
            {t("instantDelivery.status")}：{order.deliveryReceipt ? t("instantDelivery.delivered") : t("instantDelivery.waitingReceipt")}
          </div>
          {hasAction("retry_instant_fulfillment") ? (
            <div className="mt-3">
              {actionReason("retry_instant_fulfillment") ? <ActionDisabledReason reason={actionReason("retry_instant_fulfillment")} /> : null}
              <Button onClick={retryDirectDelivery} disabled={Boolean(actionReason("retry_instant_fulfillment")) || isPending} loading={isPending} variant="outline">
                <RefreshCw className="h-4 w-4" />
                {t("instantDelivery.retry")}
              </Button>
            </div>
          ) : null}
        </div>
      ) : null}

      {showOrderActions && hasStockDeliverySnapshot ? (
        <div className="mt-4 bg-[var(--background)] py-3">
          <div className="flex items-center justify-between gap-3">
            <div className="flex min-w-0 items-center gap-2">
              <PackageCheck className="h-4 w-4 shrink-0 text-[var(--muted-foreground)]" />
              <div className="text-sm font-medium text-[var(--foreground)]">{t("stockDelivery.heading")}</div>
              {revealedDelivery ? (
                <Button type="button" size="sm" variant="ghost" className="h-7 w-7 px-0" onClick={copyRevealedDelivery} aria-label={t("stockDelivery.copy")}>
                  <Copy className="h-4 w-4" />
                </Button>
              ) : null}
            </div>
          </div>
          {revealedDelivery ? (
            <div className="mt-3 rounded-[8px] bg-[var(--surface-1)] p-3">
              <pre className="max-h-48 overflow-auto whitespace-pre-wrap break-words text-sm leading-6 text-[var(--foreground)]">{revealedDelivery.payload}</pre>
            </div>
          ) : (
            <div className="mt-3">
              <Button onClick={revealStockDelivery} disabled={isPending} loading={isPending} variant="outline">
                <Eye className="h-4 w-4" />
                {t("stockDelivery.reveal")}
              </Button>
            </div>
          )}
        </div>
      ) : null}

      {showOrderActions && (hasAction("accept_order") || hasVisibleAction("open_dispute")) ? (
        <div className="mt-4 grid gap-3">
          <div className="flex flex-wrap items-center gap-2">
            {hasAction("accept_order") ? (
              <div>
                {actionReason("accept_order") ? <ActionDisabledReason reason={actionReason("accept_order")} /> : null}
                <Button onClick={acceptOrder} disabled={Boolean(actionReason("accept_order")) || isPending} loading={isPending} variant="primary">
                  <CheckCircle2 className="h-4 w-4" />
                  {t("actions.accept")}
                </Button>
              </div>
            ) : null}
            {hasVisibleAction("open_dispute") ? (
              <div>
                {visibleActionReason("open_dispute") ? <ActionDisabledReason reason={visibleActionReason("open_dispute")} /> : null}
                <Button
                  type="button"
                  onClick={() => setShowDisputeForm(true)}
                  disabled={Boolean(visibleActionReason("open_dispute")) || isPending}
                  variant="danger"
                >
                  <Scale className="h-4 w-4" />
                  {t("actions.openDispute")}
                </Button>
              </div>
            ) : null}
          </div>
          {hasVisibleAction("open_dispute") ? (
            <Dialog open={showDisputeForm} onOpenChange={(open) => {
              setShowDisputeForm(open);
              if (!open) setConfirmingDispute(false);
            }}>
              <DialogContent className="max-w-xl" showClose={false}>
                <DialogHeader>
                  <DialogTitle>{t("disputeOpen.heading")}</DialogTitle>
                  <DialogDescription>{t("disputeOpen.description")}</DialogDescription>
                </DialogHeader>
                <div className="grid gap-3">
              <label className="block">
                <span className="text-sm font-medium text-[var(--foreground)]">{t("disputeOpen.reason")}</span>
                <textarea
                  className="mf-control-field mt-2 min-h-28 w-full resize-none px-3 py-3 leading-6"
                  suppressHydrationWarning
                  value={disputeReason}
                  onChange={(event) => {
                    setDisputeReason(event.target.value);
                    setConfirmingDispute(false);
                  }}
                  placeholder={t("disputeOpen.reasonPlaceholder")}
                />
                {disputeReasonMissing ? <div className="mt-2 text-xs text-[var(--muted-foreground)]">{t("disputeOpen.reasonRequired")}</div> : null}
              </label>
              <label className="block">
                <span className="text-sm font-medium text-[var(--foreground)]">
                  {t("disputeOpen.evidenceUrl")} <span className="text-[var(--muted-foreground)]">{t("optional")}</span>
                </span>
                <input
                  className="mf-control-field mt-2 w-full px-3"
                  suppressHydrationWarning
                  value={disputeEvidenceUrl}
                  onChange={(event) => {
                    setDisputeEvidenceUrl(event.target.value);
                    setConfirmingDispute(false);
                  }}
                  placeholder={t("disputeOpen.evidenceUrlPlaceholder")}
                />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-[var(--foreground)]">
                  {t("disputeOpen.attachment")} <span className="text-[var(--muted-foreground)]">{t("optional")}</span>
                </span>
                <span className="mf-control-field mt-2 flex min-h-10 cursor-pointer items-center justify-between gap-3 px-3 text-sm">
                  <span className={disputeAttachments.length ? "truncate text-[var(--foreground)]" : "truncate text-[var(--muted-foreground)]"}>
                    {disputeAttachments.length ? t("disputeOpen.attachmentCount", { count: disputeAttachments.length }) : t("disputeOpen.noAttachment")}
                  </span>
                  <span className="inline-flex h-8 shrink-0 items-center gap-2 rounded-[10px] border border-[var(--border)] bg-[var(--background)] px-3 text-xs font-medium text-[var(--foreground)] transition-colors hover:bg-[rgb(30,31,33)]">
                    <FileUp className="h-3.5 w-3.5" />
                    {t("disputeOpen.chooseAttachment")}
                  </span>
                  <input
                    className="sr-only"
                    suppressHydrationWarning
                    type="file"
                    multiple
                    accept="image/png,image/jpeg,image/webp,application/pdf"
                    onChange={(event) => {
                      const selected = Array.from(event.target.files ?? []);
                      selected.forEach((file) => {
                        const id = `${file.name}-${file.lastModified}-${clientRandomId()}`;
                        const attachment: DisputeAttachment = { id, file };
                        setDisputeAttachments((attachments) => [...attachments, attachment]);
                        if (file.type.startsWith("image/")) {
                          const reader = new FileReader();
                          reader.onload = () => {
                            setDisputeAttachments((attachments) => attachments.map((item) => (
                              item.id === id ? { ...item, previewUrl: typeof reader.result === "string" ? reader.result : undefined } : item
                            )));
                          };
                          reader.readAsDataURL(file);
                        }
                      });
                      setConfirmingDispute(false);
                      event.currentTarget.value = "";
                    }}
                  />
                </span>
                {disputeAttachments.length > 0 ? (
                  <AttachmentPreviewGrid
                    attachments={disputeAttachments}
                    onRemove={(id) => {
                      setDisputeAttachments((attachments) => attachments.filter((item) => item.id !== id));
                      setConfirmingDispute(false);
                    }}
                    removeLabel={t("disputeOpen.removeAttachment")}
                  />
                ) : null}
                <div className="mt-2 text-xs text-[var(--muted-foreground)]">{t("disputeOpen.attachmentHint")}</div>
              </label>
                </div>
              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setShowDisputeForm(false)} disabled={isPending}>
                  {t("disputeOpen.cancel")}
                </Button>
                <div className="relative">
                  <Button type="button" variant="danger" disabled={disputeReasonMissing || Boolean(visibleActionReason("open_dispute")) || isPending} onClick={() => setConfirmingDispute(true)}>
                    <Scale className="h-4 w-4" />
                    {t("disputeOpen.review")}
                  </Button>
                  {confirmingDispute ? (
                    <ConfirmActionPopover
                      title={t("disputeOpen.confirmTitle")}
                      description={t("disputeOpen.confirmDescription")}
                      cancelLabel={t("disputeOpen.cancelConfirm")}
                      confirmLabel={t("disputeOpen.confirm")}
                      confirmVariant="danger"
                      busy={isPending}
                      onCancel={() => setConfirmingDispute(false)}
                      onConfirm={openDispute}
                    />
                  ) : null}
                </div>
              </DialogFooter>
              </DialogContent>
            </Dialog>
          ) : null}
        </div>
      ) : null}

      {showDisputeManagement && hasDisputeManagementAction ? (
        <div className="mt-4 px-1">
          {showDisputeManagementHeading ? <div className="text-sm font-medium text-[var(--foreground)]">{t("disputeManagement.heading")}</div> : null}
          <div className={showDisputeManagementHeading ? "mt-3 flex flex-wrap items-center gap-x-5 gap-y-3" : "flex flex-wrap items-center gap-x-5 gap-y-3"}>
            {hasAction("cancel_dispute") ? (
              <div className="flex items-center gap-2">
                {actionReason("cancel_dispute") ? <ActionDisabledReason reason={actionReason("cancel_dispute")} /> : null}
                <Button className="w-auto border-[var(--primary)]/50 bg-[rgba(72,108,230,0.12)] text-[var(--foreground)] hover:bg-[rgba(72,108,230,0.18)]" variant="outline" disabled={Boolean(actionReason("cancel_dispute")) || isPending} onClick={() => setShowCancelDisputeDialog(true)}>
                  {t("disputeManagement.cancel")}
                </Button>
                <Dialog open={showCancelDisputeDialog} onOpenChange={(open) => {
                  if (!isPending) {
                    setShowCancelDisputeDialog(open);
                    if (!open) setConfirmingCancelDispute(false);
                  }
                }}>
                  <DialogContent className="max-w-xl" showClose={false}>
                    <DialogHeader>
                      <DialogTitle>{t("disputeManagement.cancelConfirmTitle")}</DialogTitle>
                      <DialogDescription>{t("disputeManagement.cancelConfirmDescription")}</DialogDescription>
                    </DialogHeader>
                    <label className="grid gap-2">
                      <span className="text-sm font-medium text-[var(--foreground)]">{t("disputeManagement.cancelReason")}</span>
                      <textarea
                        className="mf-control-field min-h-24 w-full px-3 py-3"
                        suppressHydrationWarning
                        value={cancelDisputeReason}
                        onChange={(event) => {
                          setCancelDisputeReason(event.target.value);
                          setConfirmingCancelDispute(false);
                        }}
                        placeholder={t("disputeManagement.cancelPlaceholder")}
                      />
                    </label>
                    <DialogFooter>
                      <Button type="button" variant="outline" disabled={isPending} onClick={() => setShowCancelDisputeDialog(false)}>
                        {t("disputeOpen.cancel")}
                      </Button>
                      <div className="relative">
                        <Button type="button" className="border-[var(--primary)]/50 bg-[rgba(72,108,230,0.12)] text-[var(--foreground)] hover:bg-[rgba(72,108,230,0.18)]" variant="outline" disabled={cancelDisputeReason.trim().length < 4 || Boolean(actionReason("cancel_dispute")) || isPending} onClick={() => setConfirmingCancelDispute(true)}>
                          {t("disputeManagement.cancel")}
                        </Button>
                        {confirmingCancelDispute ? (
                          <ConfirmActionPopover
                            title={t("disputeManagement.cancelConfirmTitle")}
                            description={t("disputeManagement.cancelConfirmDescription")}
                            cancelLabel={t("disputeManagement.backToEdit")}
                            confirmLabel={t("disputeManagement.cancel")}
                            confirmVariant="outlineAccent"
                            busy={isPending}
                            onCancel={() => setConfirmingCancelDispute(false)}
                            onConfirm={cancelDispute}
                          />
                        ) : null}
                      </div>
                    </DialogFooter>
                  </DialogContent>
                </Dialog>
              </div>
            ) : null}
            {hasAction("open_appeal") ? (
              <div className="flex items-center gap-2">
                {actionReason("open_appeal") ? <ActionDisabledReason reason={actionReason("open_appeal")} /> : null}
                <Button className="w-auto border-[var(--primary)]/50 bg-[rgba(72,108,230,0.12)] text-[var(--foreground)] hover:bg-[rgba(72,108,230,0.18)]" variant="outline" disabled={Boolean(actionReason("open_appeal")) || isPending} onClick={() => setShowAppealDialog(true)}>
                  {t("disputeManagement.appeal")}
                </Button>
                <Dialog open={showAppealDialog} onOpenChange={(open) => !isPending && setShowAppealDialog(open)}>
                  <DialogContent className="max-w-xl" showClose={false}>
                    <DialogHeader>
                      <DialogTitle>{t("disputeManagement.appeal")}</DialogTitle>
                      <DialogDescription>{t("disputeManagement.appealHint")}</DialogDescription>
                    </DialogHeader>
                    <textarea
                      className="mf-control-field min-h-24 w-full px-3 py-3"
                      suppressHydrationWarning
                      value={appealReason}
                      onChange={(event) => setAppealReason(event.target.value)}
                      placeholder={t("disputeManagement.appealPlaceholder")}
                    />
                    <DialogFooter>
                      <Button type="button" variant="outline" disabled={isPending} onClick={() => setShowAppealDialog(false)}>
                        {t("disputeOpen.cancel")}
                      </Button>
                      <Button type="button" variant="primary" loading={isPending} disabled={appealReason.trim().length < 4 || Boolean(actionReason("open_appeal")) || isPending} onClick={openAppeal}>
                        {t("disputeManagement.appeal")}
                      </Button>
                    </DialogFooter>
                  </DialogContent>
                </Dialog>
              </div>
            ) : null}
            {hasAction("assign_reviewer") ? (
              <div className="flex flex-wrap items-start gap-2 border-l border-[var(--border)] pl-5">
                <Select
                  value={reviewerAccountId}
                  disabled={reviewerCandidates.length === 0 || Boolean(actionReason("assign_reviewer")) || isPending}
                  onValueChange={setReviewerAccountId}
                >
                  <SelectTrigger className="w-[15rem] max-w-full">
                    <SelectValue placeholder={t("disputeManagement.selectReviewer")} />
                  </SelectTrigger>
                  <SelectContent>
                    {reviewerCandidates.map((account) => (
                      <SelectItem key={account.id} value={account.id}>
                        {accountOptionLabel(account, t)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <TooltipProvider delayDuration={160}>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <span className="inline-flex">
                        <Button className="w-auto border-[var(--primary)]/50 bg-[rgba(72,108,230,0.12)] text-[var(--foreground)] hover:bg-[rgba(72,108,230,0.18)]" variant="outline" loading={isPending} disabled={Boolean(assignReviewerBlockedReason) || isPending} onClick={assignReviewer}>
                          {t("disputeManagement.assignReviewer")}
                        </Button>
                      </span>
                    </TooltipTrigger>
                    {assignReviewerBlockedReason ? <TooltipContent side="top">{assignReviewerBlockedReason}</TooltipContent> : null}
                  </Tooltip>
                </TooltipProvider>
              </div>
            ) : null}
            {hasAction("override_accept_original") || hasAction("override_close_original") ? (
              <div className="flex items-center gap-2 border-l border-[var(--border)] pl-5">
                <Button className="w-auto border-[var(--primary)]/50 bg-[rgba(72,108,230,0.12)] text-[var(--foreground)] hover:bg-[rgba(72,108,230,0.18)]" variant="outline" disabled={isPending} onClick={() => setShowOverrideDialog(true)}>
                  {t("disputeManagement.openOverride")}
                </Button>
                <Dialog open={showOverrideDialog} onOpenChange={(open) => {
                  if (!isPending) {
                    setShowOverrideDialog(open);
                    if (!open) setConfirmingOverrideDecision(null);
                  }
                }}>
                  <DialogContent className="max-w-xl" showClose={false}>
                    <DialogHeader>
                      <DialogTitle>{t("disputeManagement.openOverride")}</DialogTitle>
                      <DialogDescription>{t("disputeManagement.overrideConfirmDescription")}</DialogDescription>
                    </DialogHeader>
                    <textarea
                      className="mf-control-field min-h-24 w-full px-3 py-3"
                      suppressHydrationWarning
                      value={overrideReason}
                      onChange={(event) => {
                        setOverrideReason(event.target.value);
                        setConfirmingOverrideDecision(null);
                      }}
                      placeholder={t("disputeManagement.overridePlaceholder")}
                    />
                    <DialogFooter>
                      <Button type="button" variant="outline" disabled={isPending} onClick={() => setShowOverrideDialog(false)}>
                        {t("disputeOpen.cancel")}
                      </Button>
                      {hasAction("override_accept_original") ? (
                        <div className="relative">
                          <Button type="button" variant="primary" disabled={Boolean(actionReason("override_accept_original")) || isPending || overrideReason.trim().length < 4} onClick={() => setConfirmingOverrideDecision("accept_original")}>
                            {t("disputeManagement.overrideAccept")}
                          </Button>
                          {confirmingOverrideDecision === "accept_original" ? (
                            <ConfirmActionPopover
                              title={t("disputeManagement.overrideAcceptConfirmTitle")}
                              description={t("disputeManagement.overrideConfirmDescription")}
                              cancelLabel={t("disputeManagement.backToEdit")}
                              confirmLabel={t("disputeManagement.overrideAccept")}
                              confirmVariant="primary"
                              busy={isPending}
                              onCancel={() => setConfirmingOverrideDecision(null)}
                              onConfirm={() => overrideReview("accept_original")}
                            />
                          ) : null}
                        </div>
                      ) : null}
                      {hasAction("override_close_original") ? (
                        <div className="relative">
                          <Button type="button" variant="danger" disabled={Boolean(actionReason("override_close_original")) || isPending || overrideReason.trim().length < 4} onClick={() => setConfirmingOverrideDecision("close_original")}>
                            {t("disputeManagement.overrideClose")}
                          </Button>
                          {confirmingOverrideDecision === "close_original" ? (
                            <ConfirmActionPopover
                              title={t("disputeManagement.overrideCloseConfirmTitle")}
                              description={t("disputeManagement.overrideConfirmDescription")}
                              cancelLabel={t("disputeManagement.backToEdit")}
                              confirmLabel={t("disputeManagement.overrideClose")}
                              confirmVariant="danger"
                              busy={isPending}
                              onCancel={() => setConfirmingOverrideDecision(null)}
                              onConfirm={() => overrideReview("close_original")}
                            />
                          ) : null}
                        </div>
                      ) : null}
                    </DialogFooter>
                  </DialogContent>
                </Dialog>
              </div>
            ) : null}
          </div>
        </div>
      ) : null}

      {availableActions.length === 0 && !isFinal ? (
        <div className="mt-4 px-1 text-sm leading-6 text-[var(--muted-foreground)]">
          {emptyActionText}
        </div>
      ) : null}

      {operationResult ? (
        <div className="mt-4 flex items-start gap-2 bg-[var(--background)] p-3">
          {operationResult.tone === "danger" ? <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-[var(--accent-red)]" /> : <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-[var(--accent-green)]" />}
          <div>
            <div className="text-sm font-medium text-[var(--foreground)]">{t("result.heading")}</div>
            <div className="mt-1 text-sm leading-6 text-[var(--muted-foreground)]">
              {t("result.description", { status: orderPanelPhaseLabel(order, currentPaymentIntent, operationResult.status, locale) })}
            </div>
          </div>
        </div>
      ) : null}

    </div>
  );
}

function PaymentStatusSummary({ order, intent, paymentDueAt, t, locale }: { order: Order; intent?: PaymentIntent; paymentDueAt?: string; t: OrderActionTranslator; locale: string }) {
  const failureReason = paymentFailureReason(intent, t);
  return (
    <div className="min-w-0 text-sm leading-6 text-[var(--foreground)]">
      <div>
        <div className="text-[11px] font-medium uppercase leading-4 text-[var(--muted-foreground)]">{t("payment.payableAmount")}</div>
        <div className="mt-1 text-xl font-semibold leading-7 text-[var(--foreground)]">
          {paymentAmountLabel(order, intent, t, locale)}
        </div>
        {paymentDueAt ? (
          <div className="mt-1 text-xs leading-5 text-[var(--muted-foreground)]">
            {t("payment.dueAt", { date: formatDate(paymentDueAt) })}
          </div>
        ) : null}
      </div>
      {failureReason ? (
        <div className="mt-2 rounded-[12px] bg-[rgba(245,98,98,0.1)] px-3 py-2 text-xs leading-5 text-[var(--foreground)]">
          {/* 中文注释：OKX 返回的 invalidMessage 是用户能直接修复的失败原因，支付区优先展示。 */}
          {failureReason}
        </div>
      ) : null}
    </div>
  );
}

function OkxPaymentControls({
  intent,
  statusView,
  busy,
  busyAction,
  locale,
  canStart,
  disabledReason,
  t,
  onRefresh,
  onSignAndSubmit,
  trailingAction,
}: {
  intent?: PaymentIntent;
  statusView: ReturnType<typeof paymentStatusPresentation>;
  busy: boolean;
  busyAction: "start" | "refresh" | "pay" | null;
  locale: string;
  canStart: boolean;
  disabledReason: string;
  t: OrderActionTranslator;
  onRefresh: () => void;
  onSignAndSubmit: () => void;
  trailingAction?: ReactNode;
}) {
  const requirements = readPaymentRequirements(intent);
  const settled = isPaymentSettled(intent);
  const retryable = isPaymentRetryable(intent);
  const signDisabled = busy || retryable || !canStart || Boolean(disabledReason) || Boolean(intent && !requirements) || settled;

  return (
    <div className="grid gap-2">
      {!settled ? (
        <div className="flex flex-wrap gap-2">
          {intent ? (
            <Button size="sm" type="button" variant="outline" onClick={onRefresh} disabled={busy} loading={busyAction === "refresh"}>
              <RefreshCw className="h-4 w-4" />
              {locale === "en" ? "Refresh" : "刷新"}
            </Button>
          ) : null}
          <Button size="sm" onClick={onSignAndSubmit} disabled={signDisabled} loading={busyAction === "pay"} variant="primary">
            <Wallet className="h-4 w-4" />
            {busyAction === "pay" ? t("payment.paying") : statusView.actionLabel}
          </Button>
          {trailingAction}
        </div>
      ) : null}
      {!settled && disabledReason ? <ActionDisabledReason reason={disabledReason} /> : null}
    </div>
  );
}

function AttachmentPreviewGrid({
  attachments,
  removeLabel,
  onRemove,
}: {
  attachments: DeliveryAttachment[];
  removeLabel: string;
  onRemove: (id: string) => void;
}) {
  return (
    <div className="mt-3 grid max-w-2xl gap-2 sm:grid-cols-2">
      {attachments.map((attachment) => (
        <div key={attachment.id} className="flex min-w-0 max-w-[20rem] items-center gap-3 rounded-[12px] border border-[var(--border)] bg-[var(--background)] p-2">
          {attachment.previewUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={attachment.previewUrl} alt="" className="h-12 w-12 shrink-0 rounded-[8px] object-cover" />
          ) : (
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-[8px] bg-[rgb(33,34,37)] text-[var(--muted-foreground)]">
              <FileUp className="h-4 w-4" />
            </div>
          )}
          <div className="min-w-0 max-w-[calc(100%-6rem)] flex-1">
            <div className="block max-w-full overflow-hidden text-ellipsis whitespace-nowrap text-sm text-[var(--foreground)]" title={attachment.file.name}>{attachment.file.name}</div>
            <div className="mt-0.5 text-xs text-[var(--muted-foreground)]">{formatFileSize(attachment.file.size)}</div>
          </div>
          <button
            type="button"
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] text-[var(--muted-foreground)] transition-colors hover:bg-[rgb(30,31,33)] hover:text-[var(--foreground)]"
            onClick={() => onRemove(attachment.id)}
            aria-label={removeLabel}
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      ))}
    </div>
  );
}

function ConfirmActionPopover({
  title,
  description,
  cancelLabel,
  confirmLabel,
  confirmVariant,
  busy,
  onCancel,
  onConfirm,
}: {
  title: string;
  description: string;
  cancelLabel: string;
  confirmLabel: string;
  confirmVariant: "primary" | "danger" | "outlineAccent";
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="absolute bottom-[calc(100%+8px)] right-0 z-30 w-[260px] animate-popover-in rounded-[12px] border border-[var(--border)] bg-[rgb(24,25,27)] p-2 shadow-[var(--shadow-md)]">
      <div className="px-2 py-1.5 text-sm font-normal text-[var(--foreground)]">{title}</div>
      <div className="px-2 pb-2 text-xs leading-5 text-[var(--muted-foreground)]">{description}</div>
      <div className="grid grid-cols-2 gap-1.5">
        <Button type="button" variant="outline" size="sm" disabled={busy} onClick={onCancel}>
          {cancelLabel}
        </Button>
        <Button
          type="button"
          variant={confirmVariant === "outlineAccent" ? "outline" : confirmVariant}
          size="sm"
          className={confirmVariant === "outlineAccent" ? "border-[var(--primary)]/50 bg-[rgba(72,108,230,0.12)] text-[var(--foreground)] hover:bg-[rgba(72,108,230,0.18)]" : undefined}
          loading={busy}
          onClick={onConfirm}
        >
          {confirmLabel}
        </Button>
      </div>
    </div>
  );
}

function ActionDisabledReason({ reason }: { reason: string }) {
  return (
    <div className="inline-flex w-fit max-w-full justify-self-start rounded-[8px] border border-[var(--border)] bg-[var(--surface-control)] px-2.5 py-1.5 text-xs leading-5 text-[var(--muted-foreground)]">
      {reason}
    </div>
  );
}
