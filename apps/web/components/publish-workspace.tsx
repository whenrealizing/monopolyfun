"use client";

import { Link } from "@/i18n/navigation";
import type { FormEvent, ReactNode } from "react";
import { useEffect, useRef, useState, useSyncExternalStore, useTransition } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { AlertCircle, CheckCircle2, ClipboardList, ClipboardPaste, HelpCircle, Package, Plus, Trash2 } from "lucide-react";

import { readPublishType, type PublishType } from "@/components/publish-shared";
import { Button } from "@/components/ui/button";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { useToast } from "@/components/ui/toast";
import { usePathname, useRouter } from "@/i18n/navigation";
import {
  getOfferWorkspace,
  createProjectValidationLaunch,
  createProjectValidationTask,
  type PostItemFulfillmentMode,
  type PublishPostItemInput,
  type PublishProjectItemInput,
  publishProjectValidationLaunch,
  publishOffer,
  publishProject,
  publishRequest,
  uploadDigitalInventory,
} from "@/lib/api";
import { MARKET_SURFACE_META, surfaceAccentStyle } from "@/components/market-card-primitives";
import { buildAuthModalHref, buildPathWithSearch } from "@/lib/auth-modal-route";
import { offerHref, projectHref, requestHref } from "@/lib/business-routes";
import { readStoredSession, subscribeSession } from "@/lib/client-preferences";
import { presentError } from "@/lib/error-messages";
import { cn } from "@/lib/utils";

type PublishKind = "offer" | "request" | "project";
type FieldError = { field: string; message: string };
type IndexedFieldError = FieldError & { index: number };
type FieldErrorsByName = Record<string, string | undefined>;
type PublishTranslator = ReturnType<typeof useTranslations>;
type SingleItemDraft = {
  amount: string;
  acceptanceCriteria: string;
  description: string;
  deliveryStandard: string;
  digitalInventory: string;
  fulfillmentMode: PostItemFulfillmentMode;
  taskName: string;
  difficultyScore: string;
  quantity: string;
};
type TradeDraftCache = {
  version: 1;
  kind: "offer" | "request";
  title: string;
  description: string;
  paymentRecipient: string;
  drafts: SingleItemDraft[];
};
type ProjectDraftCache = {
  version: 1;
  title?: string;
  description?: string;
  goal: string;
  drafts: SingleItemDraft[];
};

const MAX_INITIAL_ITEMS = 20;
const TRADE_DRAFT_CACHE_PREFIX = "monopolyfun:publish:trade:draft:v1:";
const PROJECT_DRAFT_CACHE_KEY = "monopolyfun:publish:project:draft:v1";
const PAYMENT_METHOD_OKX_DIRECT_PAY = "okx_direct_pay";
const OKX_DIRECT_PAY_NETWORK = "eip155:196";
const EVM_ADDRESS_PATTERN = /^0x[0-9a-fA-F]{40}$/;
const restoredTradeDraftToastKinds = new Set<"offer" | "request">();
let restoredProjectDraftToastShown = false;

export function PublishWorkspace({ initialType = "offer" }: { initialType?: PublishType }) {
  const t = useTranslations("Publish");
  const router = useRouter();
  const searchParams = useSearchParams();
  const routeType = readPublishType(searchParams.get("type"));
  const [activeType, setActiveType] = useState<PublishType>(initialType);
  const tradeTabs = [
    { id: "offer" as const, label: t("tabs.offer.label"), description: t("tabs.offer.description"), icon: Package },
    { id: "request" as const, label: t("tabs.request.label"), description: t("tabs.request.description"), icon: ClipboardList },
  ];

  useEffect(() => {
    queueMicrotask(() => setActiveType(routeType));
  }, [routeType]);

  function changeTradeType(type: "offer" | "request") {
    setActiveType(type);
    router.replace(type === "offer" ? "/publish?type=trade" : `/publish?type=${type}`);
  }

  if (activeType === "project") {
    return <ProjectComposer />;
  }

  const typeTabs = <CreateTypeTabs activeType={activeType} onChange={changeTradeType} items={tradeTabs} />;

  return (
    <div className="flex flex-col gap-6">
      {activeType === "offer" ? <OfferRequestComposer kind="offer" typeTabs={typeTabs} /> : null}
      {activeType === "request" ? <OfferRequestComposer kind="request" typeTabs={typeTabs} /> : null}
    </div>
  );
}

function CreateTypeTabs({
  activeType,
  onChange,
  items,
}: {
  activeType: "offer" | "request";
  onChange: (type: "offer" | "request") => void;
  items: Array<{
    id: "offer" | "request";
    label: string;
    description: string;
    icon: typeof Package;
  }>;
}) {
  return (
    <div className="grid gap-2 sm:grid-cols-2">
      {items.map((item) => {
        const Icon = item.icon;
        const active = item.id === activeType;
        return (
          <button
            key={item.id}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(item.id)}
            className={cn(
              "relative flex min-h-[96px] flex-col gap-2 overflow-hidden rounded-[6px] px-4 py-3 text-left transition",
              active
                ? "bg-[var(--primary-soft)] text-[var(--foreground)]"
                : "bg-[var(--surface-1)] text-[var(--foreground)] hover:bg-[var(--surface-hover)]",
            )}
          >
            {active ? <span className="absolute inset-y-3 left-0 w-1 rounded-r-full bg-[var(--primary)]" /> : null}
            <span className="flex items-center gap-2 text-sm font-normal leading-5">
              <Icon className="h-4 w-4" />
              {item.label}
            </span>
            <span className="text-xs leading-4 text-[var(--muted-foreground)]">
              {item.description}
            </span>
          </button>
        );
      })}
    </div>
  );
}

function OfferRequestComposer({ kind, typeTabs }: { kind: "offer" | "request"; typeTabs?: ReactNode }) {
  const t = useTranslations("Publish");
  const toast = useToast();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const session = useSyncExternalStore(subscribeSession, readStoredSession, () => null);
  const [isPending, startTransition] = useTransition();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [paymentRecipient, setPaymentRecipient] = useState("");
  const [drafts, setDrafts] = useState<SingleItemDraft[]>(() => [createDefaultSingleItem()]);
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [draftLoaded, setDraftLoaded] = useState(false);
  const [draftLoadedKind, setDraftLoadedKind] = useState<"offer" | "request" | null>(null);
  const restoredDraftMessage = t("draft.restored");
  const currentHref = buildPathWithSearch(pathname, searchParams);
  const loginHref = buildAuthModalHref({ pathname, searchParams, mode: "login", returnTo: currentHref });
  const titleValue = title.trim();
  const descriptionValue = description.trim();
  const fieldErrors = buildPostFieldErrors(t, { title: titleValue, description: descriptionValue });
  const paymentErrors = kind === "offer" ? buildPaymentErrors(t, paymentRecipient) : [];
  const itemErrors = buildIndexedItemErrors(t, kind, { title: titleValue, description: descriptionValue, drafts });
  const hasValidationErrors = fieldErrors.length > 0 || paymentErrors.length > 0 || itemErrors.length > 0;
  const postErrorsByName = fieldErrorsByName(fieldErrors);
  const itemErrorsByIndex = buildItemErrorsByIndex(itemErrors);
  const visiblePostErrorsByName: FieldErrorsByName = submitAttempted ? postErrorsByName : {};
  const visiblePaymentErrors = submitAttempted || paymentRecipient.trim() ? paymentErrors : [];
  const visibleItemErrorsByIndex = submitAttempted ? itemErrorsByIndex : new Map<number, Record<string, string | undefined>>();
  const hasDraftContent = hasTradeDraftContent({ title, description, paymentRecipient, drafts });

  useEffect(() => {
    queueMicrotask(() => {
      setDraftLoaded(false);
      setDraftLoadedKind(null);
      const cached = readTradeDraftCache(kind, session?.accountId);
      if (!cached) {
        setTitle("");
        setDescription("");
        setPaymentRecipient("");
        setDrafts([createDefaultSingleItem()]);
        setDraftLoadedKind(kind);
        setDraftLoaded(true);
        return;
      }
      setTitle(cached.title);
      setDescription(cached.description);
      setPaymentRecipient(cached.paymentRecipient);
      setDrafts(cached.drafts.length > 0 ? cached.drafts : [createDefaultSingleItem()]);
      if (!restoredTradeDraftToastKinds.has(kind) && hasTradeDraftContent(cached)) {
        restoredTradeDraftToastKinds.add(kind);
        toast.notify({ tone: "info", title: restoredDraftMessage });
      }
      setDraftLoadedKind(kind);
      setDraftLoaded(true);
    });
  }, [kind, restoredDraftMessage, session?.accountId, toast]);

  useEffect(() => {
    if (!draftLoaded || draftLoadedKind !== kind || isSubmitting) {
      return;
    }
    if (!hasDraftContent) {
      clearTradeDraftCache(kind, session?.accountId);
      return;
    }
    writeTradeDraftCache(kind, session?.accountId, {
      version: 1,
      kind,
      title,
      description,
      paymentRecipient,
      drafts,
    });
  }, [description, draftLoaded, draftLoadedKind, drafts, hasDraftContent, isSubmitting, kind, paymentRecipient, session?.accountId, title]);

  useEffect(() => {
    if (!draftLoaded || draftLoadedKind !== kind || isSubmitting || !hasDraftContent) {
      return;
    }
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [draftLoaded, draftLoadedKind, hasDraftContent, isSubmitting, kind]);

  async function pastePaymentRecipient() {
    if (typeof navigator === "undefined" || !navigator.clipboard?.readText) {
      toast.notify({ tone: "error", title: t("errors.clipboardReadFailed") });
      return;
    }
    try {
      const text = await navigator.clipboard.readText();
      setPaymentRecipient(text.trim());
    } catch {
      toast.notify({ tone: "error", title: t("errors.clipboardReadFailed") });
    }
  }

  function updateDraft(index: number, draft: SingleItemDraft) {
    setDrafts((current) => current.map((item, itemIndex) => itemIndex === index ? draft : item));
  }

  function addDraft() {
    if (drafts.length >= MAX_INITIAL_ITEMS) {
      toast.notify({ tone: "error", title: t("errors.maxItems.item", { count: MAX_INITIAL_ITEMS }) });
      return;
    }
    // 中文注释：offer/request 也直接维护 items 数组，发布时一次性提交多个可交易单项。
    setDrafts((current) => [...current, createDefaultSingleItem()]);
  }

  function removeDraft(index: number) {
    setDrafts((current) => current.length <= 1 ? current : current.filter((_, itemIndex) => itemIndex !== index));
  }

  function submit(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    if (isSubmitting) {
      return;
    }
    if (!session?.accountId) {
      toast.notify({ tone: "error", title: t("errors.loginRequired") });
      return;
    }
    if (hasValidationErrors) {
      setSubmitAttempted(true);
      // 中文注释：多 item 发布按索引汇总错误，用户能直接定位到对应卡片。
      toast.notify({ tone: "error", title: formatPublishErrorSummary(t, kind, fieldErrors, paymentErrors, itemErrors) });
      return;
    }

    setIsSubmitting(true);
    startTransition(async () => {
      try {
        const items = drafts.map((draft) => buildSingleItemPayload(t, kind, { title: titleValue, description: descriptionValue, draft }));
        if (kind === "offer") {
          const offer = await publishOffer({
            title: titleValue,
            description: descriptionValue,
            currency: "USD",
            paymentMethod: PAYMENT_METHOD_OKX_DIRECT_PAY,
            paymentNetwork: OKX_DIRECT_PAY_NETWORK,
            paymentRecipient: paymentRecipient.trim(),
            items,
          });
          try {
            await uploadInitialStockInventory(offer.offerNo, drafts, session.accountId);
          } catch {
            toast.notify({ tone: "error", title: t("errors.inventoryUploadFailed") });
            router.push(offerHref(offer));
            router.refresh();
            return;
          }
          clearTradeDraftCache(kind, session.accountId);
          resetTradeForm();
          router.push(offerHref(offer));
          router.refresh();
          return;
        }

        const request = await publishRequest({
          title: titleValue,
          description: descriptionValue,
          currency: "USD",
          paymentMethod: PAYMENT_METHOD_OKX_DIRECT_PAY,
          paymentNetwork: OKX_DIRECT_PAY_NETWORK,
          items,
        });
        clearTradeDraftCache(kind, session.accountId);
        resetTradeForm();
        router.push(requestHref(request));
        router.refresh();
      } catch (error) {
        toast.notify({ tone: "error", title: formatCaughtPublishError(t, kind, error) });
      } finally {
        setIsSubmitting(false);
      }
    });
  }

  function resetTradeForm() {
    setTitle("");
    setDescription("");
    setPaymentRecipient("");
    setDrafts([createDefaultSingleItem()]);
    setSubmitAttempted(false);
  }

  return (
    <div data-entry={`type=${kind}`} className="grid items-start gap-x-5 gap-y-3 lg:grid-cols-[minmax(0,1fr)_320px]">
      <form className="flex min-w-0 self-start flex-col gap-5" onSubmit={submit}>
        <h1 className="text-[32px] font-semibold leading-tight text-[var(--foreground)]">{t(`trade.${kind}.headerTitle`)}</h1>
        {typeTabs ? <div>{typeTabs}</div> : null}

        <div className="rounded-[12px] border border-[var(--border)] bg-[var(--background)] p-5">
          <SharedPostFields
            title={title}
            description={description}
            errorsByName={visiblePostErrorsByName}
            titlePlaceholder={t(`trade.${kind}.titlePlaceholder`)}
            descriptionPlaceholder={t(`trade.${kind}.descriptionPlaceholder`)}
            onTitleChange={setTitle}
            onDescriptionChange={setDescription}
          />
        </div>

        {kind === "offer" ? (
          <div className="rounded-[12px] border border-[var(--border)] bg-[var(--background)] p-5">
            <Field label={t("fields.sellerWallet")}>
              <div className="flex gap-2">
                <input
                  value={paymentRecipient}
                  onChange={(event) => setPaymentRecipient(event.target.value)}
                  placeholder={t("fields.sellerWallet")}
                  aria-invalid={visiblePaymentErrors.length > 0}
                  className={cn(fieldClassName(visiblePaymentErrors.length > 0), "min-w-0 flex-1")}
                />
                <Button
                  type="button"
                  variant="outline"
                  className="h-[40px] min-h-[40px] shrink-0 border-transparent bg-[var(--surface-control)] px-3 hover:bg-[rgb(30,31,33)]"
                  onClick={pastePaymentRecipient}
                >
                  <ClipboardPaste className="h-4 w-4" />
                  {t("actions.paste")}
                </Button>
              </div>
              {visiblePaymentErrors[0]?.message ? (
                <FieldHint hint={visiblePaymentErrors[0].message} tone="error" />
              ) : EVM_ADDRESS_PATTERN.test(paymentRecipient.trim()) ? (
                <p className="flex min-h-5 items-center gap-1.5 text-xs leading-5 text-[var(--success)]">
                  <CheckCircle2 className="h-3.5 w-3.5" />
                  {t("hints.walletValid")}
                </p>
              ) : (
                <FieldHint hint="" />
              )}
            </Field>
          </div>
        ) : null}

        <div className="rounded-[12px] border border-[var(--border)] bg-[var(--background)] p-5">
          <PublishItemList
            kind={kind}
            drafts={drafts}
            errorsByIndex={visibleItemErrorsByIndex}
            onAdd={addDraft}
            onRemove={removeDraft}
            onUpdate={updateDraft}
          />
        </div>

        <LoginNotice accountId={session?.accountId} loginHref={loginHref} />

        <div className="flex justify-start">
          <Button type="submit" variant="primary" loading={isPending || isSubmitting} disabled={isPending || isSubmitting || hasValidationErrors}>
            {isPending || isSubmitting ? t("submit.publishing") : t(`submit.${kind}`)}
          </Button>
        </div>
      </form>

      <TradePreview
        kind={kind}
        title={titleValue}
        description={descriptionValue}
        drafts={drafts}
        paymentReady={kind === "request" || paymentErrors.length === 0}
        ownerHandle={session?.handle ? `@${session.handle.replace(/^@+/, "")}` : "@member"}
        ownerInitials={session?.displayName ? initialsFromName(session.displayName) : session?.handle ? initialsFromName(session.handle) : "MF"}
      />
    </div>
  );
}

function TradePreview({
  kind,
  title,
  description,
  drafts,
  paymentReady,
  ownerHandle,
  ownerInitials,
}: {
  kind: "offer" | "request";
  title: string;
  description: string;
  drafts: SingleItemDraft[];
  paymentReady: boolean;
  ownerHandle: string;
  ownerInitials: string;
}) {
  const t = useTranslations("Publish");
  const amountReady = drafts.every((draft) => isMoneyAmount(draft.amount));
  const quantityReady = drafts.every((draft) => isPositiveInteger(draft.quantity));
  const deliveryReady = drafts.every((draft) => draft.deliveryStandard.trim());
  const acceptanceReady = drafts.every((draft) => draft.acceptanceCriteria.trim());
  const itemsReady = drafts.every((draft) => draft.taskName.trim());
  const amountPreview = formatAmountPreview(drafts);
  const missingLabels = [
    !title ? t("preview.items.title") : null,
    !description ? t("preview.items.description") : null,
    !amountReady ? t(`preview.items.${kind === "offer" ? "price" : "budget"}`) : null,
    !quantityReady ? (kind === "offer" ? t("fields.inventory") : t("fields.requestSlots")) : null,
    !itemsReady ? (kind === "offer" ? t("items.offer.listTitle") : t("items.request.listTitle")) : null,
    !deliveryReady ? t("fields.projectDeliveryStandard") : null,
    !acceptanceReady ? t("fields.projectAcceptanceStandard") : null,
    kind === "offer" && !paymentReady ? t("fields.sellerWallet") : null,
  ].filter(Boolean);
  const previewReady = missingLabels.length === 0;
  const PreviewStatusIcon = previewReady ? CheckCircle2 : AlertCircle;
  const marketT = useTranslations("MarketSurface");
  const inventoryPreview = formatPublishInventoryPreview(marketT, kind, drafts);
  const settlementPreview = kind === "offer" && !paymentReady ? null : marketT("card.settlement.cash");

  return (
    <aside className="min-w-0 lg:sticky lg:top-3 lg:self-start">
      <h2 className="mb-3 text-xl font-normal text-[var(--foreground)]">{t("preview.title")}</h2>
      <PublishListingPreviewCard
        kind={kind}
        amount={amountPreview}
        title={title || t("preview.emptyTitle")}
        description={description || t("preview.emptyDescription")}
        metaLabel={compactPreviewMeta(inventoryPreview, settlementPreview)}
        ownerHandle={ownerHandle}
        ownerInitials={ownerInitials}
      />
      <div
        className={cn(
          "mt-4 flex items-start gap-2 rounded-[12px] bg-[var(--surface-control)] px-3 py-2 text-xs font-normal leading-5",
          previewReady ? "text-[var(--success)]" : "text-[var(--muted-foreground)]",
        )}
      >
        <PreviewStatusIcon className={cn("mt-0.5 h-3.5 w-3.5 shrink-0", previewReady ? "text-[var(--success)]" : "text-[var(--warning)]")} />
        <span>
          {previewReady ? t("preview.ready") : t("preview.remaining", { fields: missingLabels.join(t("preview.separator")) })}
        </span>
      </div>
    </aside>
  );
}

function formatAmountPreview(drafts: SingleItemDraft[]) {
  const amounts = drafts
    .map((draft) => Number(draft.amount.trim()))
    .filter((amount) => Number.isFinite(amount) && amount > 0);
  if (amounts.length === 0) return "--";
  const min = Math.min(...amounts);
  const max = Math.max(...amounts);
  return min === max ? `$${formatMoneyCompact(min)}` : `$${formatMoneyCompact(min)}-$${formatMoneyCompact(max)}`;
}

function formatMoneyCompact(value: number) {
  return Number.isInteger(value) ? value.toFixed(0) : value.toFixed(2);
}

function formatPublishInventoryPreview(
  t: ReturnType<typeof useTranslations>,
  kind: "offer" | "request",
  drafts: SingleItemDraft[],
) {
  const quantities = drafts
    .map((draft) => publishDraftQuantity(kind, draft))
    .filter((quantity) => Number.isInteger(quantity) && quantity > 0);
  const total = quantities.reduce((sum, quantity) => sum + quantity, 0);
  if (total <= 0) return null;
  if (kind === "offer") return t("inventory.remaining", { count: total });
  return t("inventory.claimable", { remaining: total, total });
}

function compactPreviewMeta(...items: Array<string | null | undefined>) {
  return items.filter((item) => item && item.trim()).join(" · ");
}

function initialsFromName(value: string) {
  const initials = value
    .trim()
    .split(/\s+/)
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
  return initials || "MF";
}

function formatProjectTaskCountPreview(t: PublishTranslator, completed: number, total: number) {
  if (completed < total) return t("preview.namedItems", { completed, total });
  return t("preview.taskCount", { count: total });
}

function formatDifficultyPreview(t: PublishTranslator, drafts: SingleItemDraft[]) {
  const difficulties = drafts
    .map((draft) => Number(draft.difficultyScore.trim()))
    .filter((difficulty) => Number.isFinite(difficulty) && difficulty >= 0.5 && difficulty <= 8);
  if (difficulties.length === 0) return "--";
  const min = Math.min(...difficulties);
  const max = Math.max(...difficulties);
  return min === max ? formatDifficultyScore(min) : t("preview.difficultyRange", { min: formatDifficultyScore(min), max: formatDifficultyScore(max) });
}

function formatDifficultyScore(value: number) {
  return Number.isInteger(value) ? value.toFixed(0) : value.toFixed(1);
}

function PublishListingPreviewCard({
  kind,
  amount,
  title,
  description,
  metaLabel,
  ownerHandle,
  ownerInitials,
}: {
  kind: PublishKind;
  amount: string | null;
  title: string;
  description: string;
  metaLabel?: string;
  ownerHandle: string;
  ownerInitials: string;
}) {
  const meta = MARKET_SURFACE_META[kind];
  return (
    <div
      className="relative isolate flex h-[224px] flex-col overflow-hidden rounded-[10px] border border-[var(--border-strong)] bg-[var(--background)] p-3.5 transition duration-200"
      style={surfaceAccentStyle(meta.accent)}
    >
      <div className="flex items-center">
        <span
          className="inline-flex h-6 min-w-11 items-center justify-center rounded-[7px] px-2 text-[11px] font-normal"
          style={{
            backgroundColor: "color-mix(in srgb, var(--opportunity-accent) 13%, transparent)",
            color: "var(--opportunity-accent)",
          }}
        >
          {meta.heading}
        </span>
      </div>

      <div className="mt-4 min-w-0">
        <h3 className="min-h-[21px] truncate text-[17px] font-normal leading-[1.22] text-[var(--foreground)]">
          {title}
        </h3>
        <div
          aria-hidden={!amount}
          className={cn("mt-3 truncate text-[20px] font-black leading-none text-[var(--foreground)]", amount ? null : "invisible")}
        >
          {amount ?? "\u00a0"}
        </div>
        <p className="mt-2 line-clamp-2 min-h-[40px] text-[13px] leading-5 text-[var(--muted-foreground)]">
          {description}
        </p>
      </div>

      {metaLabel ? (
        <div className="mt-3 min-w-0 text-xs font-normal text-[var(--muted-foreground)]">
          <span className="truncate">{metaLabel}</span>
        </div>
      ) : null}

      <div className="mt-auto flex items-center gap-2 pt-3">
        <span
          className="flex h-4 w-4 shrink-0 items-center justify-center overflow-hidden rounded-full text-[8px] font-normal text-white"
          style={{ background: "linear-gradient(135deg, hsl(226 76% 48%), hsl(246 72% 36%))" }}
        >
          {ownerInitials}
        </span>
        <span className="min-w-0 truncate text-[14px] font-normal leading-4 text-[var(--muted-foreground)]">
          {ownerHandle}
        </span>
      </div>
    </div>
  );
}

function ProjectComposer() {
  const t = useTranslations("Publish");
  const toast = useToast();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const session = useSyncExternalStore(subscribeSession, readStoredSession, () => null);
  const [isPending, startTransition] = useTransition();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [goal, setGoal] = useState("");
  const [drafts, setDrafts] = useState<SingleItemDraft[]>(() => [createDefaultSingleItem()]);
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [draftLoaded, setDraftLoaded] = useState(false);
  const restoredDraftMessage = t("draft.restored");
  const currentHref = buildPathWithSearch(pathname, searchParams);
  const loginHref = buildAuthModalHref({ pathname, searchParams, mode: "login", returnTo: currentHref });
  const titleValue = title.trim();
  const descriptionValue = description.trim();
  const goalValue = goal.trim();
  const fieldErrors = buildProjectFieldErrors(t, { title: titleValue, goal: goalValue });
  const itemErrors = buildIndexedItemErrors(t, "project", { title: titleValue, description: descriptionValue, drafts });
  const hasValidationErrors = fieldErrors.length > 0 || itemErrors.length > 0;
  const projectErrorsByName = projectFieldErrorsByName(fieldErrors);
  const itemErrorsByIndex = buildItemErrorsByIndex(itemErrors);
  const visibleProjectErrorsByName: FieldErrorsByName = submitAttempted ? projectErrorsByName : {};
  const visibleItemErrorsByIndex = submitAttempted ? itemErrorsByIndex : new Map<number, Record<string, string | undefined>>();
  const hasDraftContent = hasProjectDraftContent({ title, description, goal, drafts });

  useEffect(() => {
    queueMicrotask(() => {
      const cached = readProjectDraftCache(session?.accountId);
      if (!cached) {
        setDraftLoaded(true);
        return;
      }
      setTitle(cached.title ?? "");
      setDescription(cached.description ?? "");
      setGoal(cached.goal);
      setDrafts(cached.drafts.length > 0 ? cached.drafts : [createDefaultSingleItem()]);
      if (!restoredProjectDraftToastShown && hasProjectDraftContent(cached)) {
        restoredProjectDraftToastShown = true;
        toast.notify({ tone: "info", title: restoredDraftMessage });
      }
      setDraftLoaded(true);
    });
  }, [restoredDraftMessage, session?.accountId, toast]);

  useEffect(() => {
    if (!draftLoaded || isSubmitting) {
      return;
    }
    if (!hasDraftContent) {
      clearProjectDraftCache(session?.accountId);
      return;
    }
    writeProjectDraftCache(session?.accountId, {
      version: 1,
      title,
      description,
      goal,
      drafts,
    });
  }, [description, draftLoaded, drafts, goal, hasDraftContent, isSubmitting, session?.accountId, title]);

  useEffect(() => {
    if (!draftLoaded || isSubmitting || !hasDraftContent) {
      return;
    }
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [draftLoaded, hasDraftContent, isSubmitting]);

  function updateDraft(index: number, draft: SingleItemDraft) {
    setDrafts((current) => current.map((item, itemIndex) => itemIndex === index ? draft : item));
  }

  function addDraft() {
    if (drafts.length >= MAX_INITIAL_ITEMS) {
      toast.notify({ tone: "error", title: t("errors.maxItems.task", { count: MAX_INITIAL_ITEMS }) });
      return;
    }
    // 中文注释：项目发布页直接维护任务数组，提交时与后端 items 契约保持一致。
    setDrafts((current) => [...current, createDefaultSingleItem()]);
  }

  function removeDraft(index: number) {
    setDrafts((current) => current.length <= 1 ? current : current.filter((_, itemIndex) => itemIndex !== index));
  }

  function submit(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    if (isSubmitting) {
      return;
    }
    if (!session?.accountId) {
      toast.notify({ tone: "error", title: t("errors.loginRequired") });
      return;
    }
    if (hasValidationErrors) {
      setSubmitAttempted(true);
      toast.notify({ tone: "error", title: formatPublishErrorSummary(t, "project", fieldErrors, [], itemErrors) });
      return;
    }

    setIsSubmitting(true);
    startTransition(async () => {
      try {
        const project = await publishProject({
          title: titleValue,
          description: descriptionValue || buildProjectDescriptionFallback(t, titleValue),
          goal: goalValue,
          items: drafts.map((draft) => buildProjectItemPayload(t, { draft })),
        });
        await createInitialProjectProgress(project.projectNo, goalValue, drafts);
        clearProjectDraftCache(session.accountId);
        resetProjectForm();
        router.push(projectHref(project));
        router.refresh();
      } catch (error) {
        toast.notify({ tone: "error", title: formatCaughtPublishError(t, "project", error) });
      } finally {
        setIsSubmitting(false);
      }
    });
  }

  function resetProjectForm() {
    setTitle("");
    setDescription("");
    setGoal("");
    setDrafts([createDefaultSingleItem()]);
    setSubmitAttempted(false);
  }

  return (
    <div data-entry="type=project" className="grid items-start gap-x-5 gap-y-3 lg:grid-cols-[minmax(0,1fr)_320px]">
      <form className="flex min-w-0 self-start flex-col gap-5" onSubmit={submit}>
        <h1 className="text-[32px] font-semibold leading-tight text-[var(--foreground)]">{t("project.headerTitle")}</h1>

        <div className="rounded-[12px] border border-[var(--border)] bg-[var(--background)] p-5">
          <div className="grid gap-5">
            <Field label={t("fields.companyTitle")}>
              <input
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                placeholder={t("project.titlePlaceholder")}
                aria-invalid={Boolean(visibleProjectErrorsByName.title)}
                className={fieldClassName(Boolean(visibleProjectErrorsByName.title))}
              />
              <FieldHint hint={visibleProjectErrorsByName.title} tone="error" />
            </Field>

            <Field label={t("fields.initialGoal")}>
              <textarea
                value={goal}
                onChange={(event) => setGoal(event.target.value)}
                placeholder={t("project.goalPlaceholder")}
                aria-invalid={Boolean(visibleProjectErrorsByName.goal)}
                maxLength={1000}
                rows={4}
                className={fieldClassName(Boolean(visibleProjectErrorsByName.goal), "textarea")}
              />
              <FieldHint hint={visibleProjectErrorsByName.goal} tone="error" />
            </Field>

            <Field label={t("fields.companyDescription")}>
              <textarea
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                placeholder={t("project.descriptionPlaceholder")}
                maxLength={500}
                rows={3}
                className={fieldClassName(false, "textarea")}
              />
              <FieldHint hint="" />
            </Field>
          </div>
        </div>

        <div className="rounded-[12px] border border-[var(--border)] bg-[var(--background)] p-5">
          <PublishItemList
            kind="project"
            drafts={drafts}
            errorsByIndex={visibleItemErrorsByIndex}
            onAdd={addDraft}
            onRemove={removeDraft}
            onUpdate={updateDraft}
          />
        </div>
        <LoginNotice accountId={session?.accountId} loginHref={loginHref} />

        <div className="flex justify-start">
          <Button type="submit" variant="primary" loading={isPending || isSubmitting} disabled={isPending || isSubmitting || hasValidationErrors}>
            {isPending || isSubmitting ? t("submit.publishing") : t("submit.project")}
          </Button>
        </div>
      </form>

      <ProjectPreview
        title={titleValue}
        description={descriptionValue || (titleValue ? buildProjectDescriptionFallback(t, titleValue) : "")}
        goal={goalValue}
        drafts={drafts}
        ownerHandle={session?.handle ? `@${session.handle.replace(/^@+/, "")}` : "@member"}
        ownerInitials={session?.displayName ? initialsFromName(session.displayName) : session?.handle ? initialsFromName(session.handle) : "MF"}
      />
    </div>
  );
}

function ProjectPreview({
  title,
  description,
  goal,
  drafts,
  ownerHandle,
  ownerInitials,
}: {
  title?: string;
  description?: string;
  goal: string;
  drafts: SingleItemDraft[];
  ownerHandle: string;
  ownerInitials: string;
}) {
  const t = useTranslations("Publish");
  const completedTasks = drafts.filter((draft) => draft.taskName.trim()).length;
  const difficultyReady = drafts.every((draft) => isDifficultyScore(draft.difficultyScore));
  const tasksReady = drafts.every((draft) => draft.taskName.trim());
  const deliveryReady = drafts.every((draft) => draft.deliveryStandard.trim());
  const acceptanceReady = drafts.every((draft) => draft.acceptanceCriteria.trim());
  const difficultyPreview = formatDifficultyPreview(t, drafts);
  const marketT = useTranslations("MarketSurface");
  const missingLabels = [
    !title ? t("preview.items.companyTitle") : null,
    !goal ? t("preview.items.initialGoal") : null,
    !tasksReady ? t("items.project.listTitle") : null,
    !deliveryReady ? t("fields.deliveryStandard") : null,
    !acceptanceReady ? t("fields.acceptanceStandard") : null,
    !difficultyReady ? t("fields.difficulty") : null,
  ].filter(Boolean);
  const previewReady = missingLabels.length === 0;
  const PreviewStatusIcon = previewReady ? CheckCircle2 : AlertCircle;
  const taskPreview = formatProjectTaskCountPreview(t, completedTasks, drafts.length);

  return (
    <aside className="min-w-0 lg:sticky lg:top-3 lg:self-start">
      <h2 className="mb-3 text-xl font-normal text-[var(--foreground)]">{t("preview.title")}</h2>
      <PublishListingPreviewCard
        kind="project"
        amount={null}
        title={title || t("preview.projectEmptyTitle")}
        description={description || t("preview.projectEmptyDescription")}
        metaLabel={compactPreviewMeta(taskPreview, difficultyPreview !== "--" ? difficultyPreview : null, marketT("card.settlement.shares"))}
        ownerHandle={ownerHandle}
        ownerInitials={ownerInitials}
      />
      <div
        className={cn(
          "mt-4 flex items-start gap-2 rounded-[12px] bg-[var(--surface-control)] px-3 py-2 text-xs font-normal leading-5",
          previewReady ? "text-[var(--success)]" : "text-[var(--muted-foreground)]",
        )}
      >
        <PreviewStatusIcon className={cn("mt-0.5 h-3.5 w-3.5 shrink-0", previewReady ? "text-[var(--success)]" : "text-[var(--warning)]")} />
        <span>
          {previewReady ? t("preview.ready") : t("preview.remaining", { fields: missingLabels.join(t("preview.separator")) })}
        </span>
      </div>
    </aside>
  );
}

function SharedPostFields({
  title,
  description,
  errorsByName,
  titlePlaceholder,
  descriptionPlaceholder,
  onTitleChange,
  onDescriptionChange,
}: {
  title: string;
  description: string;
  errorsByName: Record<string, string | undefined>;
  titlePlaceholder: string;
  descriptionPlaceholder: string;
  onTitleChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
}) {
  const t = useTranslations("Publish");
  return (
    <div className="grid gap-5">
      <Field label={t("fields.title")}>
        <input
          value={title}
          onChange={(event) => onTitleChange(event.target.value)}
          placeholder={titlePlaceholder}
          aria-invalid={Boolean(errorsByName.title)}
          maxLength={80}
          className={fieldClassName(Boolean(errorsByName.title))}
        />
        <FieldHint hint={errorsByName.title} tone="error" />
      </Field>

      <Field label={t("fields.description")}>
        <textarea
          value={description}
          onChange={(event) => onDescriptionChange(event.target.value)}
          placeholder={descriptionPlaceholder}
          aria-invalid={Boolean(errorsByName.description)}
          maxLength={1000}
          rows={4}
          className={fieldClassName(Boolean(errorsByName.description), "textarea")}
        />
        <FieldHint hint={errorsByName.description} tone="error" />
      </Field>
    </div>
  );
}

function SingleItemFields({
  kind,
  draft,
  errorsByName,
  separated,
  sectionAction,
  sectionIndex,
  sectionTitle,
  onChange,
}: {
  kind: PublishKind;
  draft: SingleItemDraft;
  errorsByName: Record<string, string | undefined>;
  separated?: boolean;
  sectionAction?: ReactNode;
  sectionIndex?: number;
  sectionTitle?: string;
  onChange: (draft: SingleItemDraft) => void;
}) {
  const t = useTranslations("Publish");
  function updateDraft(patch: Partial<SingleItemDraft>) {
    onChange({ ...draft, ...patch });
  }

  function updateFulfillmentMode(mode: PostItemFulfillmentMode) {
    const inventoryCount = parseDigitalInventoryPayloads(draft.digitalInventory).length;
    const stockDefaults = mode === "stock_fulfillment"
      ? defaultStockFulfillmentFields(t, draft)
      : {};
    updateDraft({
      ...stockDefaults,
      fulfillmentMode: mode,
      quantity: mode === "stock_fulfillment" && inventoryCount > 0 ? String(inventoryCount) : draft.quantity,
    });
  }

  function updateDigitalInventory(value: string) {
    const inventoryCount = parseDigitalInventoryPayloads(value).length;
    updateDraft({
      digitalInventory: value,
      quantity: draft.fulfillmentMode === "stock_fulfillment" && inventoryCount > 0 ? String(inventoryCount) : draft.quantity,
    });
  }

  if (kind === "project") {
    return (
      <SingleItemSection
        title={sectionTitle ?? t("items.project.defaultTitle")}
        action={sectionAction}
        index={sectionIndex}
        separated={separated}
      >
        <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_260px]">
          <Field label={t("fields.taskName")}>
            <input
              value={draft.taskName}
              onChange={(event) => updateDraft({ taskName: event.target.value })}
              placeholder={t("items.project.taskPlaceholder")}
              aria-invalid={Boolean(errorsByName.taskName)}
              className={fieldClassName(Boolean(errorsByName.taskName))}
            />
            <FieldHint hint={errorsByName.taskName} tone="error" />
          </Field>
          <Field label={t("fields.difficulty")} meta={<DifficultyHelp />} metaPlacement="inline">
            <DifficultyScoreField value={draft.difficultyScore} hasError={Boolean(errorsByName.difficultyScore)} onChange={(value) => updateDraft({ difficultyScore: value })} />
            <FieldHint hint={errorsByName.difficultyScore} tone="error" />
          </Field>
        </div>
        <Field label={t("fields.taskDescription")} optional>
          <textarea
            value={draft.description}
            onChange={(event) => updateDraft({ description: event.target.value })}
            placeholder={t("items.project.descriptionPlaceholder")}
            maxLength={1000}
            rows={3}
            className={fieldClassName(false, "textarea")}
          />
          <FieldHint hint="" />
        </Field>
        <Field label={t("fields.projectDeliveryStandard")}>
          <textarea
            value={draft.deliveryStandard}
            onChange={(event) => updateDraft({ deliveryStandard: event.target.value })}
            placeholder={t("items.project.deliveryPlaceholder")}
            aria-invalid={Boolean(errorsByName.deliveryStandard)}
            maxLength={1000}
            rows={4}
            className={fieldClassName(Boolean(errorsByName.deliveryStandard), "textarea")}
          />
          <FieldHint hint={errorsByName.deliveryStandard} tone="error" />
        </Field>
        <Field label={t("fields.projectAcceptanceStandard")}>
          <textarea
            value={draft.acceptanceCriteria}
            onChange={(event) => updateDraft({ acceptanceCriteria: event.target.value })}
            placeholder={t("items.project.acceptancePlaceholder")}
            aria-invalid={Boolean(errorsByName.acceptanceCriteria)}
            maxLength={1000}
            rows={3}
            className={fieldClassName(Boolean(errorsByName.acceptanceCriteria), "textarea")}
          />
          <FieldHint hint={errorsByName.acceptanceCriteria} tone="error" />
        </Field>
      </SingleItemSection>
    );
  }

  return (
    <SingleItemSection
      title={sectionTitle ?? t(`items.${kind}.defaultTitle`)}
      action={sectionAction}
      index={sectionIndex}
      separated={separated}
    >
      <div className="grid gap-3 sm:grid-cols-[minmax(0,2.2fr)_minmax(0,1fr)_minmax(0,0.8fr)]">
        <Field label={t("fields.itemName")}>
          <input
            value={draft.taskName}
            onChange={(event) => updateDraft({ taskName: event.target.value })}
            placeholder={t(`items.${kind}.taskPlaceholder`)}
            aria-invalid={Boolean(errorsByName.taskName)}
            className={fieldClassName(Boolean(errorsByName.taskName))}
          />
          <FieldHint hint={errorsByName.taskName} tone="error" />
        </Field>
        <Field label={kind === "offer" ? t("fields.price") : t("fields.budget")}>
          <input
            value={draft.amount}
            onChange={(event) => updateDraft({ amount: sanitizeMoneyInput(event.target.value) })}
            placeholder={t(`items.${kind}.amountPlaceholder`)}
            inputMode="decimal"
            aria-invalid={Boolean(errorsByName.amount)}
            className={fieldClassName(Boolean(errorsByName.amount))}
          />
          <FieldHint hint={errorsByName.amount} tone="error" />
        </Field>
        <Field label={kind === "offer" ? t("fields.inventory") : t("fields.requestSlots")}>
          {kind === "offer" && draft.fulfillmentMode === "stock_fulfillment" ? (
            <input
              value={String(parseDigitalInventoryPayloads(draft.digitalInventory).length)}
              readOnly
              aria-readonly="true"
              className={cn(fieldClassName(Boolean(errorsByName.quantity)), "cursor-not-allowed bg-[var(--surface-control)] text-[var(--muted-foreground)]")}
            />
          ) : (
          <input
            value={draft.quantity}
            onChange={(event) => updateDraft({ quantity: sanitizeIntegerInput(event.target.value) })}
            placeholder={t("items.quantityPlaceholder")}
            inputMode="numeric"
            aria-invalid={Boolean(errorsByName.quantity)}
            className={fieldClassName(Boolean(errorsByName.quantity))}
          />
          )}
          <FieldHint hint={errorsByName.quantity} tone="error" />
        </Field>
      </div>
      <Field label={t("fields.deliveryStandard")}>
        <textarea
          value={draft.deliveryStandard}
          onChange={(event) => updateDraft({ deliveryStandard: event.target.value })}
          placeholder={t(`items.${kind}.deliveryPlaceholder`)}
          aria-invalid={Boolean(errorsByName.deliveryStandard)}
          maxLength={1000}
          rows={4}
          className={fieldClassName(Boolean(errorsByName.deliveryStandard), "textarea")}
        />
        <FieldHint hint={errorsByName.deliveryStandard} tone="error" />
      </Field>
      <Field label={t("fields.acceptanceStandard")}>
        <textarea
          value={draft.acceptanceCriteria}
          onChange={(event) => updateDraft({ acceptanceCriteria: event.target.value })}
          placeholder={t(`items.${kind}.acceptancePlaceholder`)}
          aria-invalid={Boolean(errorsByName.acceptanceCriteria)}
          maxLength={1000}
          rows={3}
          className={fieldClassName(Boolean(errorsByName.acceptanceCriteria), "textarea")}
        />
        <FieldHint hint={errorsByName.acceptanceCriteria} tone="error" />
      </Field>
      {kind === "offer" ? (
        <>
          <PublishDeliveryModeSelector value={draft.fulfillmentMode} onChange={updateFulfillmentMode} />
          {draft.fulfillmentMode === "stock_fulfillment" ? (
            <Field
              label={t("digitalInventory.fieldLabel")}
              meta={t("digitalInventory.currentCount", { count: parseDigitalInventoryPayloads(draft.digitalInventory).length })}
              metaPlacement="inline"
            >
              <textarea
                value={draft.digitalInventory}
                onChange={(event) => updateDigitalInventory(event.target.value)}
                placeholder={t("digitalInventory.placeholder")}
                aria-invalid={Boolean(errorsByName.digitalInventory)}
                rows={4}
                className={cn(fieldClassName(Boolean(errorsByName.digitalInventory), "textarea"), "font-mono text-sm leading-6")}
              />
              <FieldHint hint={errorsByName.digitalInventory} tone="error" />
            </Field>
          ) : null}
        </>
      ) : null}
    </SingleItemSection>
  );
}

function PublishDeliveryModeSelector({
  value,
  onChange,
}: {
  value: PostItemFulfillmentMode;
  onChange: (value: PostItemFulfillmentMode) => void;
}) {
  const t = useTranslations("Publish");
  const options: Array<{ value: PostItemFulfillmentMode; label: string }> = [
    { value: "reviewed_delivery", label: t("deliveryMode.reviewed.label") },
    { value: "stock_fulfillment", label: t("deliveryMode.stock.label") },
  ];
  return (
    <div className="grid gap-2">
      <div className="text-sm font-medium text-[var(--foreground)]">{t("deliveryMode.label")}</div>
      <RadioGroup
        value={value}
        onValueChange={(nextValue) => onChange(nextValue as PostItemFulfillmentMode)}
        className="flex flex-wrap gap-x-5 gap-y-2"
      >
        {options.map((option) => (
          <label
            key={option.value}
            className="inline-flex cursor-pointer items-center gap-2 text-sm"
          >
            <RadioGroupItem value={option.value} />
            <span className="font-normal text-[var(--foreground)]">{option.label}</span>
          </label>
        ))}
      </RadioGroup>
    </div>
  );
}

function PublishItemList({
  kind,
  drafts,
  errorsByIndex,
  onAdd,
  onRemove,
  onUpdate,
}: {
  kind: PublishKind;
  drafts: SingleItemDraft[];
  errorsByIndex: Map<number, Record<string, string | undefined>>;
  onAdd: () => void;
  onRemove: (index: number) => void;
  onUpdate: (index: number, draft: SingleItemDraft) => void;
}) {
  const t = useTranslations("Publish");
  const [pendingDeleteIndex, setPendingDeleteIndex] = useState<number | null>(null);
  const deletePopoverRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (pendingDeleteIndex === null) {
      return;
    }
    function handlePointerDown(event: PointerEvent) {
      const target = event.target;
      if (target instanceof Node && deletePopoverRef.current?.contains(target)) {
        return;
      }
      setPendingDeleteIndex(null);
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setPendingDeleteIndex(null);
      }
    }
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [pendingDeleteIndex]);

  function requestRemove(index: number) {
    const draft = drafts[index];
    if (draft && isSingleItemDraftFilled(draft)) {
      setPendingDeleteIndex(index);
      return;
    }
    onRemove(index);
  }

  function confirmRemove(index: number) {
    setPendingDeleteIndex(null);
    onRemove(index);
  }

  return (
    <div className="grid gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-sm font-medium text-[var(--foreground)]">{itemListTitle(t, kind)}</div>
          <p className="mt-1 text-sm leading-6 text-[var(--muted-foreground)]">
            {itemListDescription(t, kind, drafts.length)}
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="border-transparent bg-[rgb(33,34,37)] shadow-[var(--shadow-sm)] hover:bg-[rgb(30,31,33)]"
          onClick={onAdd}
          disabled={drafts.length >= MAX_INITIAL_ITEMS}
        >
          <Plus className="h-4 w-4" />
          {addItemLabel(t, kind)}
        </Button>
      </div>

      <div className="grid gap-0">
        {drafts.map((draft, index) => (
          <SingleItemFields
            key={index}
            kind={kind}
            draft={draft}
            errorsByName={errorsByIndex.get(index) ?? {}}
            sectionTitle={itemSectionTitle(t, kind, index)}
            sectionIndex={index + 1}
            sectionAction={drafts.length > 1 ? (
              <div className="relative">
                <button
                  type="button"
                  className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-[10px] text-[var(--danger)] transition hover:bg-[rgba(213,84,63,0.12)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]"
                  aria-label={t("actions.delete")}
                  title={t("actions.delete")}
                  onClick={() => pendingDeleteIndex === index ? setPendingDeleteIndex(null) : requestRemove(index)}
                >
                  <Trash2 className="h-4 w-4" />
                </button>
                {pendingDeleteIndex === index ? (
                  <div
                    ref={deletePopoverRef}
                    className={cn(
                      "absolute right-0 z-30 w-[220px] animate-popover-in rounded-[12px] border border-[var(--border)] bg-[rgb(24,25,27)] p-2 shadow-[var(--shadow-md)]",
                      index === drafts.length - 1 ? "bottom-[calc(100%+8px)]" : "top-[calc(100%+8px)]",
                    )}
                  >
                    <p className="px-1.5 pb-2 text-xs leading-5 text-[var(--muted-foreground)]">
                      {t("actions.deleteFilledConfirm", { unit: itemListUnit(t, kind) })}
                    </p>
                    <div className="grid grid-cols-2 gap-1.5">
                      <button
                        type="button"
                        className="h-9 rounded-[10px] px-3 text-sm text-[var(--foreground)] transition-colors hover:bg-[rgb(33,34,37)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]"
                        onClick={() => setPendingDeleteIndex(null)}
                      >
                        {t("actions.cancelDelete")}
                      </button>
                      <Button
                        type="button"
                        variant="danger"
                        size="sm"
                        className="h-9 rounded-[10px] px-3"
                        onClick={() => confirmRemove(index)}
                      >
                        {t("actions.confirmDelete")}
                      </Button>
                    </div>
                  </div>
                ) : null}
              </div>
            ) : null}
            separated={index > 0}
            onChange={(nextDraft) => onUpdate(index, nextDraft)}
          />
        ))}
      </div>
    </div>
  );
}

function SingleItemSection({
  title,
  action,
  children,
  index,
  separated = false,
}: {
  title: string;
  action?: ReactNode;
  children: ReactNode;
  index?: number;
  separated?: boolean;
}) {
  return (
    <section className={cn("grid gap-4 py-4 first:pt-0 last:pb-0", separated && "border-t border-[var(--border)]")}>
      <div className="flex min-h-8 flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2.5">
          {index ? (
            <span className="inline-flex h-7 min-w-7 shrink-0 items-center justify-center rounded-full bg-[var(--surface-field)] px-2 text-xs font-normal text-[var(--muted-foreground)]">
              {index}
            </span>
          ) : (
            <div className="min-w-0 text-[0.95rem] font-medium leading-5 text-[var(--foreground)]">{title}</div>
          )}
        </div>
        {action}
      </div>
      {children}
    </section>
  );
}

function LoginNotice({ accountId, loginHref }: { accountId?: string; loginHref: string }) {
  const t = useTranslations("Publish");
  if (accountId) return null;
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-[12px] border border-[var(--border)] bg-[var(--background)] px-3 py-3 text-sm text-[var(--muted-foreground)]">
      <span>{t("loginNotice.text")}</span>
      <Button asChild variant="outline" size="sm">
        <Link href={loginHref}>{t("loginNotice.action")}</Link>
      </Button>
    </div>
  );
}

function Field({
  label,
  meta,
  metaPlacement = "end",
  optional,
  children,
}: {
  label: string;
  meta?: ReactNode;
  metaPlacement?: "end" | "inline";
  optional?: boolean;
  children: ReactNode;
}) {
  const t = useTranslations("Publish");
  return (
    <label className="flex flex-col gap-2">
      <span className={cn("flex items-center text-sm font-medium text-[var(--foreground)]", metaPlacement === "inline" ? "justify-start gap-1.5" : "justify-between gap-3")}>
        <span className="inline-flex items-center gap-1.5">
          {label}
          {optional ? <span className="text-xs font-normal text-[var(--placeholder)]">{t("fields.optional")}</span> : null}
        </span>
        {meta ? <span className="inline-flex text-xs font-normal text-[var(--muted-foreground)]">{meta}</span> : null}
      </span>
      {children}
    </label>
  );
}

function DifficultyHelp() {
  const t = useTranslations("Publish");
  return (
    <TooltipProvider delayDuration={160}>
      <Tooltip>
        <TooltipTrigger asChild>
          <button
            type="button"
            className="inline-flex h-6 w-6 items-center justify-center rounded-full text-[var(--muted-foreground)] transition hover:bg-[var(--surface-control)] hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ring)]"
            aria-label={t("difficultyHelp.title")}
          >
            <HelpCircle className="h-4 w-4" />
          </button>
        </TooltipTrigger>
        <TooltipContent side="top" align="end" className="w-[280px] p-3 text-left">
          <div className="text-sm font-normal leading-5 text-[var(--foreground)]">{t("difficultyHelp.title")}</div>
          <div className="mt-1 text-xs font-normal leading-5 text-[var(--muted-foreground)]">{t("difficultyHelp.description")}</div>
          <div className="mt-2 grid gap-1 text-xs font-normal leading-5 text-[var(--muted-foreground)]">
            {(t.raw("difficultyHelp.levels") as string[]).map((item) => (
              <div key={item}>{item}</div>
            ))}
          </div>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

function createDefaultSingleItem(): SingleItemDraft {
  return {
    amount: "",
    acceptanceCriteria: "",
    description: "",
    deliveryStandard: "",
    digitalInventory: "",
    fulfillmentMode: "reviewed_delivery",
    taskName: "",
    difficultyScore: "1",
    quantity: "1",
  };
}

function draftAccountScope(accountId?: string) {
  return accountId?.trim() ? accountId.trim() : "anonymous";
}

function tradeDraftCacheKey(kind: "offer" | "request", accountId?: string) {
  // 中文注释：草稿按账号隔离，避免同一浏览器切换账号后读到前一个账号的未发布内容。
  return `${TRADE_DRAFT_CACHE_PREFIX}${draftAccountScope(accountId)}:${kind}`;
}

function projectDraftCacheKey(accountId?: string) {
  return `${PROJECT_DRAFT_CACHE_KEY}:${draftAccountScope(accountId)}`;
}

function readTradeDraftCache(kind: "offer" | "request", accountId?: string): TradeDraftCache | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const raw = window.localStorage.getItem(tradeDraftCacheKey(kind, accountId));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<TradeDraftCache>;
    if (parsed.version !== 1 || parsed.kind !== kind) {
      return null;
    }
    return {
      version: 1,
      kind: parsed.kind,
      title: typeof parsed.title === "string" ? parsed.title : "",
      description: typeof parsed.description === "string" ? parsed.description : "",
      paymentRecipient: typeof parsed.paymentRecipient === "string" ? parsed.paymentRecipient : "",
      drafts: sanitizeCachedDrafts(parsed.drafts),
    };
  } catch {
    return null;
  }
}

function writeTradeDraftCache(kind: "offer" | "request", accountId: string | undefined, cache: TradeDraftCache) {
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.localStorage.setItem(tradeDraftCacheKey(kind, accountId), JSON.stringify(cache));
  } catch {
    // localStorage 可能被浏览器隐私设置禁用，发布页直接退化为无草稿。
  }
}

function clearTradeDraftCache(kind: "offer" | "request", accountId?: string) {
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.localStorage.removeItem(tradeDraftCacheKey(kind, accountId));
  } catch {
    // ignore
  }
}

function readProjectDraftCache(accountId?: string): ProjectDraftCache | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const raw = window.localStorage.getItem(projectDraftCacheKey(accountId));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<ProjectDraftCache>;
    if (parsed.version !== 1) {
      return null;
    }
    return {
      version: 1,
      title: typeof parsed.title === "string" ? parsed.title : "",
      description: typeof parsed.description === "string" ? parsed.description : "",
      goal: typeof parsed.goal === "string" ? parsed.goal : "",
      drafts: sanitizeCachedDrafts(parsed.drafts),
    };
  } catch {
    return null;
  }
}

function writeProjectDraftCache(accountId: string | undefined, cache: ProjectDraftCache) {
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.localStorage.setItem(projectDraftCacheKey(accountId), JSON.stringify(cache));
  } catch {
    // localStorage 可能被浏览器隐私设置禁用，发布页直接退化为无草稿。
  }
}

function clearProjectDraftCache(accountId?: string) {
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.localStorage.removeItem(projectDraftCacheKey(accountId));
  } catch {
    // ignore
  }
}

function hasTradeDraftContent({
  title,
  description,
  paymentRecipient,
  drafts,
}: {
  title: string;
  description: string;
  paymentRecipient: string;
  drafts: SingleItemDraft[];
}) {
  return Boolean(
    title.trim()
      || description.trim()
      || paymentRecipient.trim()
      || drafts.some(isSingleItemDraftFilled),
  );
}

function hasProjectDraftContent({
  title,
  description,
  goal,
  drafts,
}: {
  title?: string;
  description?: string;
  goal: string;
  drafts: SingleItemDraft[];
}) {
  return Boolean(
    title?.trim()
      || description?.trim()
      || goal.trim()
      || drafts.some(isProjectSingleItemDraftFilled),
  );
}

function isSingleItemDraftFilled(draft: SingleItemDraft) {
  return Boolean(
    draft.taskName.trim()
      || draft.amount.trim()
      || draft.acceptanceCriteria.trim()
      || draft.description.trim()
      || draft.deliveryStandard.trim()
      || draft.digitalInventory.trim()
      || draft.fulfillmentMode !== "reviewed_delivery"
      || draft.quantity.trim() !== "1",
  );
}

function isProjectSingleItemDraftFilled(draft: SingleItemDraft) {
  return Boolean(
    isSingleItemDraftFilled(draft)
      || draft.difficultyScore.trim() !== "1",
  );
}

function sanitizeCachedDrafts(value: unknown) {
  if (!Array.isArray(value)) {
    return [createDefaultSingleItem()];
  }
  const drafts = value.slice(0, MAX_INITIAL_ITEMS).map((item) => {
    const record = item && typeof item === "object" ? item as Partial<SingleItemDraft> : {};
    return {
      amount: typeof record.amount === "string" ? sanitizeMoneyInput(record.amount) : "",
      acceptanceCriteria: typeof record.acceptanceCriteria === "string" ? record.acceptanceCriteria : "",
      description: typeof record.description === "string" ? record.description : "",
      deliveryStandard: typeof record.deliveryStandard === "string" ? record.deliveryStandard : "",
      digitalInventory: typeof record.digitalInventory === "string" ? record.digitalInventory : "",
      fulfillmentMode: sanitizeFulfillmentMode(record.fulfillmentMode),
      taskName: typeof record.taskName === "string" ? record.taskName : "",
      difficultyScore: typeof record.difficultyScore === "string" ? record.difficultyScore : "1",
      quantity: typeof record.quantity === "string" ? sanitizeIntegerInput(record.quantity) : "1",
    };
  });
  return drafts.length > 0 ? drafts : [createDefaultSingleItem()];
}

function sanitizeIntegerInput(value: string) {
  return value.replace(/\D/g, "");
}

function sanitizeFulfillmentMode(value: unknown): PostItemFulfillmentMode {
  return value === "stock_fulfillment" ? "stock_fulfillment" : "reviewed_delivery";
}

function parseDigitalInventoryPayloads(value: string) {
  return Array.from(new Set(value
    .split("\n")
    .map((item) => item.trim())
    .filter(Boolean)));
}

function defaultStockFulfillmentFields(t: PublishTranslator, draft: SingleItemDraft): Partial<SingleItemDraft> {
  return {
    deliveryStandard: draft.deliveryStandard.trim() ? draft.deliveryStandard : t("digitalInventory.defaultDeliveryStandard"),
    acceptanceCriteria: draft.acceptanceCriteria.trim() ? draft.acceptanceCriteria : t("digitalInventory.defaultAcceptanceCriteria"),
  };
}

function sanitizeMoneyInput(value: string) {
  const normalized = value.replace(/[^\d.]/g, "");
  const [integerPart = "", ...decimalParts] = normalized.split(".");
  if (decimalParts.length === 0) return integerPart;
  return `${integerPart}.${decimalParts.join("").slice(0, 2)}`;
}

function buildSingleItemErrors(
  t: PublishTranslator,
  kind: PublishKind,
  input: { title: string; description: string; draft: SingleItemDraft },
) {
  const errors: FieldError[] = [];
  if (kind === "project") {
    if (!input.draft.taskName.trim()) errors.push({ field: "taskName", message: t("errors.required", { label: t("fields.taskName") }) });
    if (!isDifficultyScore(input.draft.difficultyScore)) errors.push({ field: "difficultyScore", message: t("errors.difficultyRange") });
    if (!input.draft.deliveryStandard.trim()) errors.push({ field: "deliveryStandard", message: t("errors.required", { label: t("fields.deliveryStandard") }) });
    if (!input.draft.acceptanceCriteria.trim()) errors.push({ field: "acceptanceCriteria", message: t("errors.required", { label: t("fields.acceptanceStandard") }) });
    return errors;
  }
  if (!input.draft.taskName.trim()) errors.push({ field: "taskName", message: t("errors.required", { label: kind === "offer" ? t("fields.serviceItemName") : t("fields.taskName") }) });
  if (!isMoneyAmount(input.draft.amount)) errors.push({ field: "amount", message: t("hints.moneyAmount", { label: kind === "offer" ? t("fields.price") : t("fields.budget") }) });
  if (kind === "offer" && input.draft.fulfillmentMode === "stock_fulfillment") {
    if (parseDigitalInventoryPayloads(input.draft.digitalInventory).length === 0) {
      errors.push({ field: "digitalInventory", message: t("errors.digitalInventoryRequired") });
    }
  } else if (!isPositiveInteger(input.draft.quantity)) {
    errors.push({ field: "quantity", message: t("errors.positiveInteger", { label: kind === "offer" ? t("fields.inventory") : t("fields.requestSlots") }) });
  }
  if (!input.draft.deliveryStandard.trim()) errors.push({ field: "deliveryStandard", message: t("errors.required", { label: t("fields.deliveryStandard") }) });
  if (!input.draft.acceptanceCriteria.trim()) errors.push({ field: "acceptanceCriteria", message: t("errors.required", { label: t("fields.acceptanceStandard") }) });
  return errors;
}

function buildIndexedItemErrors(t: PublishTranslator, kind: PublishKind, input: { title: string; description: string; drafts: SingleItemDraft[] }) {
  // 中文注释：多任务校验保留每条任务的索引，错误提示和输入框能精确对应。
  return input.drafts.flatMap((draft, index) =>
    buildSingleItemErrors(t, kind, { title: input.title, description: input.description, draft })
      .map((error) => ({ ...error, index })),
  );
}

function buildItemErrorsByIndex(errors: IndexedFieldError[]) {
  const byIndex = new Map<number, Record<string, string | undefined>>();
  for (const error of errors) {
    byIndex.set(error.index, {
      ...byIndex.get(error.index),
      [error.field]: error.message,
    });
  }
  return byIndex;
}

function formatPublishErrorSummary(t: PublishTranslator, kind: PublishKind, fieldErrors: FieldError[], paymentErrors: FieldError[], itemErrors: IndexedFieldError[]) {
  return `${t("errors.summaryTitle")}\n${formatPublishErrors(t, kind, fieldErrors, paymentErrors, itemErrors).map((message) => `- ${message}`).join("\n")}`;
}

function formatPublishErrors(t: PublishTranslator, kind: PublishKind, fieldErrors: FieldError[], paymentErrors: FieldError[], itemErrors: IndexedFieldError[]) {
  return [
    ...fieldErrors.map((error) => error.message),
    ...paymentErrors.map((error) => error.message),
    ...itemErrors.map((error) => t("errors.itemPrefix", { unit: itemListUnit(t, kind), index: error.index + 1, message: error.message })),
  ];
}

function formatCaughtPublishError(t: PublishTranslator, kind: PublishKind, error: unknown) {
  const presented = presentError(error, "ui.listing.create.failed");
  const fieldMessages = formatApiFieldErrors(t, kind, presented.fieldErrors);
  if (fieldMessages.length === 0) {
    return presented.message;
  }
  // 中文注释：后端 ApiError.fields 是稳定字段码，发布页把它转换为用户能定位到的卡片级错误。
  return `${presented.message}\n${fieldMessages.map((message) => `- ${message}`).join("\n")}`;
}

function formatApiFieldErrors(t: PublishTranslator, kind: PublishKind, fieldErrors: Record<string, string>) {
  return Object.entries(fieldErrors).map(([field, message]) => {
    const itemMatch = /^items\[(\d+)]\.(.+)$/.exec(field);
    if (itemMatch) {
      const itemIndex = Number(itemMatch[1]) + 1;
      return t("errors.apiField", { label: t("errors.itemPrefix", { unit: itemListUnit(t, kind), index: itemIndex, message: apiItemFieldLabel(t, kind, itemMatch[2]) }), message });
    }
    return t("errors.apiField", { label: apiPostFieldLabel(t, kind, field), message });
  });
}

function apiPostFieldLabel(t: PublishTranslator, kind: PublishKind, field: string) {
  if (field === "title") return kind === "project" ? t("fields.companyTitle") : t("fields.title");
  if (field === "description") return kind === "project" ? t("fields.companyDescription") : t("fields.description");
  if (field === "goal") return t("fields.initialGoal");
  if (field === "paymentRecipient") return t("fields.paymentRecipient");
  if (field === "deadlineAt") return t("fields.deadline");
  return field;
}

function apiItemFieldLabel(t: PublishTranslator, kind: PublishKind, field: string) {
  if (field === "name") return kind === "offer" ? t("fields.serviceItemName") : t("fields.taskName");
  if (field === "amount") return kind === "offer" ? t("fields.price") : t("fields.budget");
  if (field === "quantity") return kind === "offer" ? t("fields.inventory") : t("fields.requestSlots");
  if (field === "deliveryStandard") return kind === "project" ? t("fields.projectDeliveryStandard") : t("fields.deliveryStandard");
  if (field === "acceptanceCriteria") return kind === "project" ? t("fields.projectAcceptanceStandard") : t("fields.acceptanceStandard");
  if (field === "difficultyScore") return t("fields.difficulty");
  if (field === "digitalInventory") return t("digitalInventory.fieldLabel");
  return field;
}

function itemListTitle(t: PublishTranslator, kind: PublishKind) {
  if (kind === "offer") return t("items.offer.listTitle");
  if (kind === "request") return t("items.request.listTitle");
  return t("items.project.listTitle");
}

function itemListDescription(t: PublishTranslator, kind: PublishKind, count: number) {
  if (kind === "project") return t("items.project.listDescription", { count });
  return t("items.listDescription", { count, unit: itemListUnit(t, kind) });
}

function itemListUnit(t: PublishTranslator, kind: PublishKind) {
  if (kind === "offer") return t("items.offer.unit");
  if (kind === "request") return t("items.request.unit");
  return t("items.project.unit");
}

function itemSectionTitle(t: PublishTranslator, kind: PublishKind, index: number) {
  if (kind === "offer") return t("items.offerSectionTitle");
  if (kind === "request") return t("items.requestSectionTitle");
  return t("items.sectionTitle", { unit: itemListUnit(t, kind), index: index + 1 });
}

function addItemLabel(t: PublishTranslator, kind: PublishKind) {
  if (kind === "offer") return t("items.offer.add");
  if (kind === "request") return t("items.request.add");
  return t("items.project.add");
}

function buildSingleItemPayload(
  t: PublishTranslator,
  kind: PublishKind,
  input: { title: string; description: string; draft: SingleItemDraft },
): PublishPostItemInput {
  const deliveryStandard = input.draft.deliveryStandard.trim();
  const acceptanceCriteria = input.draft.acceptanceCriteria.trim();
  // 中文注释：offer/request 的每条 item 独立命名，列表、选择和订单快照都读取这个名称。
  return {
    name: input.draft.taskName.trim(),
    description: input.description,
    deliveryStandard,
    acceptanceCriteria: [acceptanceCriteria],
    amount: Number(input.draft.amount.trim()),
    quantity: publishDraftQuantity(kind, input.draft),
    mode: kind === "offer" ? input.draft.fulfillmentMode : "reviewed_delivery",
  };
}

function publishDraftQuantity(kind: PublishKind, draft: SingleItemDraft) {
  if (kind === "offer" && draft.fulfillmentMode === "stock_fulfillment") {
    return parseDigitalInventoryPayloads(draft.digitalInventory).length;
  }
  return Number(draft.quantity.trim());
}

async function uploadInitialStockInventory(offerNo: string, drafts: SingleItemDraft[], actorAccountId: string) {
  const stockDrafts = drafts
    .map((draft, index) => ({
      index,
      payloads: draft.fulfillmentMode === "stock_fulfillment" ? parseDigitalInventoryPayloads(draft.digitalInventory) : [],
    }))
    .filter((entry) => entry.payloads.length > 0);
  if (stockDrafts.length === 0) {
    return;
  }

  const workspace = await getOfferWorkspace(offerNo);
  const usedItemIds = new Set<string>();
  await Promise.all(stockDrafts.map((entry) => {
    const draft = drafts[entry.index];
    const item = workspace.items.find((candidate) => {
      if (usedItemIds.has(candidate.id)) return false;
      return candidate.title === draft.taskName.trim()
        && candidate.deliverableSpec === draft.deliveryStandard.trim()
        && Number(candidate.priceAmount) === Number(draft.amount.trim());
    });
    const itemId = item?.id;
    if (!itemId) {
      throw new Error("Published item not found for inventory upload");
    }
    usedItemIds.add(itemId);
    return uploadDigitalInventory(itemId, {
      actorAccountId,
      payloads: entry.payloads,
    });
  }));
}

function buildProjectItemPayload(
  t: PublishTranslator,
  input: { draft: SingleItemDraft },
): PublishProjectItemInput {
  const taskName = input.draft.taskName.trim();
  const deliveryStandard = input.draft.deliveryStandard.trim();
  const acceptanceCriteria = input.draft.acceptanceCriteria.trim();
  return {
    name: taskName,
    description: input.draft.description.trim() || buildProjectItemDescription(t, taskName),
    deliveryStandard,
    acceptanceCriteria: [acceptanceCriteria],
    difficultyScore: Number(input.draft.difficultyScore.trim()),
  };
}

async function createInitialProjectProgress(projectNo: string, goal: string, drafts: SingleItemDraft[]) {
  const cleanGoal = goal.trim();
  if (!projectNo || !cleanGoal) {
    return;
  }
  const goalTitle = buildInitialGoalTitle(cleanGoal);
  // 中文注释：发布项目时把用户填写的初始目标写入进展协议，详情页直接承接同一条目标链。
  const launch = await createProjectValidationLaunch(projectNo, {
    title: goalTitle,
    hypothesis: cleanGoal,
    proofRequests: [{
      title: `${goalTitle} 的完成结果`,
      intent: cleanGoal,
      evidenceRequirements: [{
        kind: "result",
        description: "提交能证明目标完成的结果。",
      }],
      acceptanceSignals: [{ kind: "review", description: "验收人确认结果满足目标要求。" }],
      riskLevel: "normal",
      metadata: { createdFrom: "publish_project" },
    }],
    metadata: {
      createdFrom: "publish_project",
      scorePolicyVersion: "score-v1",
      curvePolicyVersion: "curve-v1",
    },
  });
  const linkedProofRequestId = launch.proofRequests[0]?.id;
  // 中文注释：初始任务同步落到目标下，发布页和详情页的任务列表使用同一套目标任务数据。
  await Promise.all(drafts.map((draft) => createProjectValidationTask(projectNo, launch.id, {
    title: draft.taskName.trim(),
    intent: draft.description.trim() || draft.taskName.trim(),
    linkedProofRequestIds: linkedProofRequestId ? [linkedProofRequestId] : [],
    deliverable: draft.deliveryStandard.trim(),
    acceptanceCriteria: splitCriteria(draft.acceptanceCriteria),
    suggestedEvidence: [{ kind: "result" }],
    rewardPreview: {
      source: "difficulty_score",
      difficultyScore: Number(draft.difficultyScore.trim()),
    },
    tags: ["initial_task"],
    metadata: {
      createdFrom: "publish_project",
      difficultyScore: Number(draft.difficultyScore.trim()),
    },
  })));
  await publishProjectValidationLaunch(projectNo, launch.id);
}

function splitCriteria(value: string) {
  return value.split(/\n+/).map((item) => item.trim()).filter(Boolean);
}

function buildInitialGoalTitle(value: string) {
  const firstLine = value.split(/\n+/).map((item) => item.trim()).find(Boolean) ?? value.trim();
  return firstLine.length > 80 ? `${firstLine.slice(0, 80)}...` : firstLine;
}

function buildProjectItemDescription(t: PublishTranslator, taskName: string) {
  return t("project.itemDescription", { taskName });
}

function buildProjectDescriptionFallback(t: PublishTranslator, title: string) {
  return t("project.descriptionFallback", { title });
}

function buildPostFieldErrors(t: PublishTranslator, input: { title: string; description: string }) {
  const errors: FieldError[] = [];
  if (!input.title) errors.push({ field: "title", message: t("errors.required", { label: t("fields.title") }) });
  if (!input.description) errors.push({ field: "description", message: t("errors.required", { label: t("fields.description") }) });
  return errors;
}

function buildProjectFieldErrors(t: PublishTranslator, input: { title: string; goal: string }) {
  const errors: FieldError[] = [];
  if (!input.title) errors.push({ field: "title", message: t("errors.required", { label: t("fields.companyTitle") }) });
  if (!input.goal) errors.push({ field: "goal", message: t("errors.required", { label: t("fields.initialGoal") }) });
  return errors;
}

function buildPaymentErrors(t: PublishTranslator, paymentRecipient: string) {
  return EVM_ADDRESS_PATTERN.test(paymentRecipient.trim()) ? [] : [{ field: "paymentRecipient", message: t("errors.sellerWallet") }];
}

function fieldErrorsByName(errors: FieldError[]) {
  return {
    title: errors.find((item) => item.field === "title")?.message,
    description: errors.find((item) => item.field === "description")?.message,
  };
}

function projectFieldErrorsByName(errors: FieldError[]) {
  return {
    title: errors.find((item) => item.field === "title")?.message,
    goal: errors.find((item) => item.field === "goal")?.message,
  };
}

function isPositiveInteger(value: string) {
  const parsed = Number(value.trim());
  return Number.isInteger(parsed) && parsed > 0;
}

function isMoneyAmount(value: string) {
  const normalized = value.trim();
  if (!/^\d+(?:\.\d{1,2})?$/.test(normalized)) return false;
  return Number(normalized) >= 0.01;
}

function isDifficultyScore(value: string) {
  const parsed = Number(value.trim());
  return Number.isFinite(parsed) && parsed >= 0.5 && parsed <= 8;
}

function sanitizeDifficultyInput(value: string) {
  const normalized = value.replace(/[^\d.]/g, "");
  const [integerPart = "", ...decimalParts] = normalized.split(".");
  if (decimalParts.length === 0) return integerPart.slice(0, 1);
  return `${integerPart.slice(0, 1)}.${decimalParts.join("").slice(0, 1)}`;
}

function fieldClassName(hasError: boolean, fieldType: "input" | "textarea" = "input") {
  return cn(
    "mf-control-field w-full px-3",
    fieldType === "input" && "h-11",
    fieldType === "textarea" && "min-h-[112px] max-h-[220px] resize-none overflow-y-auto py-3",
    hasError && "border-[rgba(245,98,98,0.55)] bg-[rgba(245,98,98,0.08)] focus:border-[rgba(245,98,98,0.75)] focus-visible:border-[rgba(245,98,98,0.75)]",
  );
}

const DIFFICULTY_PRESETS = [
  { label: "D1", value: "0.5" },
  { label: "D2", value: "1" },
  { label: "D3", value: "2" },
  { label: "D4", value: "4" },
  { label: "D5", value: "8" },
];

function DifficultyScoreField({ value, hasError, onChange }: { value: string; hasError: boolean; onChange: (value: string) => void }) {
  return (
    <div className="grid gap-2">
      <input
        className={fieldClassName(hasError)}
        value={value}
        onChange={(event) => onChange(sanitizeDifficultyInput(event.target.value))}
        onBlur={() => {
          if (!value) return;
          const parsed = Number(value);
          if (!Number.isFinite(parsed)) return onChange("");
          onChange(String(Math.min(8, Math.max(0.5, parsed))));
        }}
        placeholder="0.5-8"
        inputMode="decimal"
      />
      <div className="grid grid-cols-5 gap-1">
        {DIFFICULTY_PRESETS.map((preset) => (
          <button
            key={preset.label}
            type="button"
            className="rounded-[8px] border border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.045)] px-2 py-1 text-xs font-normal text-[var(--muted-foreground)] transition hover:border-[rgba(142,164,255,0.48)] hover:text-[var(--foreground)]"
            onClick={() => onChange(preset.value)}
          >
            {preset.label}
          </button>
        ))}
      </div>
    </div>
  );
}

function FieldHint({ hint, tone = "error" }: { hint?: string; tone?: "default" | "error" }) {
  if (!hint) return null;
  return (
    <p
      className={cn(
        "rounded-[8px] border px-3 py-2 text-sm font-normal",
        tone === "error"
          ? "border-[rgba(245,98,98,0.3)] bg-[rgba(245,98,98,0.08)] text-[rgb(255,170,170)]"
          : "border-[var(--border)] bg-[var(--surface-1)] text-[var(--muted-foreground)]",
      )}
    >
      {hint}
    </p>
  );
}
