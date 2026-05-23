"use client";

import { Link } from "@/i18n/navigation";
import { useRouter } from "@/i18n/navigation";
import { forwardRef, type ReactNode, useEffect, useRef, useState, useSyncExternalStore, useTransition } from "react";
import { ArrowRight, Banknote, CheckCircle2, ChevronDown, ChevronUp, Clock3, Coins, Edit3, Flag, HelpCircle, PackageCheck, Plus, Save, Smartphone, UploadCloud, XCircle } from "lucide-react";
import { useTranslations } from "next-intl";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/page-layout";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { useToast } from "@/components/ui/toast";
import { claimPostItemWithDeliveryInput, closePostItem, createPostItem, createProjectItem, formatDate, getDigitalInventorySummary, type DigitalInventorySummary, type PostItem, type PostItemFulfillmentMode, type PostKind, updatePostItem, uploadDigitalInventory } from "@/lib/api";
import { buildAuthModalHref } from "@/lib/auth-modal-route";
import { readStoredSession, subscribeSession } from "@/lib/client-preferences";
import { shouldOpenPaymentRequired } from "@/lib/order-routing";

type PostItemsTranslator = ReturnType<typeof useTranslations>;
type ProjectTaskType = "normal" | "bug" | "review" | "dispute";

const projectTaskTypeOptions: Array<{ value: ProjectTaskType; labelKey: string }> = [
  { value: "normal", labelKey: "taskTypes.normal" },
  { value: "bug", labelKey: "taskTypes.bug" },
  { value: "review", labelKey: "taskTypes.review" },
  { value: "dispute", labelKey: "taskTypes.dispute" },
];

export function PostItemWorkspacePanel({
  postKind,
  postId,
  postStatus,
  ownerHandle,
  returnTo,
  initialItems,
  ownerOnly = false,
}: {
  postKind: PostKind;
  postId: string;
  postStatus: string;
  ownerHandle?: string | null;
  returnTo: string;
  initialItems: PostItem[];
  ownerOnly?: boolean;
}) {
  const t = useTranslations("PostItems");
  const toast = useToast();
  const router = useRouter();
  const session = useSyncExternalStore(subscribeSession, readStoredSession, () => null);
  const [isPending, startTransition] = useTransition();
  const nameInputRef = useRef<HTMLInputElement>(null);
  const deliveryStandardInputRef = useRef<HTMLTextAreaElement>(null);
  const acceptanceCriteriaInputRef = useRef<HTMLTextAreaElement>(null);
  const digitalInventoryInputRef = useRef<HTMLTextAreaElement>(null);
  const amountInputRef = useRef<HTMLInputElement>(null);
  const quantityInputRef = useRef<HTMLInputElement>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [deliveryStandard, setDeliveryStandard] = useState("");
  const [acceptanceCriteria, setAcceptanceCriteria] = useState("");
  const [amount, setAmount] = useState("");
  const [difficultyScore, setDifficultyScore] = useState("1.0");
  const [projectTaskType, setProjectTaskType] = useState<ProjectTaskType>("normal");
  const [quantity, setQuantity] = useState("1");
  const [agentInstruction, setAgentInstruction] = useState("");
  const [mode, setMode] = useState<PostItemFulfillmentMode>("reviewed_delivery");
  const [digitalInventoryInput, setDigitalInventoryInput] = useState("");
  const [inventorySummaries, setInventorySummaries] = useState<Record<string, DigitalInventorySummary>>({});
  const [inventoryUploadingItemId, setInventoryUploadingItemId] = useState<string | null>(null);
  const [buyerNote, setBuyerNote] = useState("");
  const [directDeliveryPhone, setDirectDeliveryPhone] = useState("");
  const [editingItemId, setEditingItemId] = useState<string | null>(null);
  const [confirmingCloseItemId, setConfirmingCloseItemId] = useState<string | null>(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [claimingItemId, setClaimingItemId] = useState<string | null>(null);
  const [expandedItemId, setExpandedItemId] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editDeliveryStandard, setEditDeliveryStandard] = useState("");
  const [editAcceptanceCriteria, setEditAcceptanceCriteria] = useState("");
  const [editAmount, setEditAmount] = useState("");
  const [editDifficultyScore, setEditDifficultyScore] = useState("1.0");
  const [editProjectTaskType, setEditProjectTaskType] = useState<ProjectTaskType>("normal");
  const [editQuantity, setEditQuantity] = useState("1");
  const [editAgentInstruction, setEditAgentInstruction] = useState("");
  const [editMode, setEditMode] = useState<PostItemFulfillmentMode>("reviewed_delivery");
  const [editInventoryInput, setEditInventoryInput] = useState("");
  const hydratedNowMs = useHydratedNowMs();
  const normalizedOwnerHandle = ownerHandle?.replace(/^@+/, "").toLowerCase();
  const normalizedSessionHandle = session?.handle?.replace(/^@+/, "").toLowerCase();
  // 中文注释：项目任务创建入口先展示给所有可参与用户，真正创建时仍由登录态和后端权限兜底。
  const isOwner = !!normalizedOwnerHandle && normalizedOwnerHandle === normalizedSessionHandle;
  const postIsOpen = postKind === "project" ? postStatus === "active" : postStatus === "open";
  const workspaceTitle = t(postKind === "project" ? "workspace.projectTitle" : "workspace.tradeTitle");
  const canOpenCreateDialog = postIsOpen && (postKind === "project" || isOwner);

  useEffect(() => {
    function openComposer(event: Event) {
      const detail = (event as CustomEvent<{ kind?: string }>).detail;
      if (detail?.kind && detail.kind !== postKind) return;
      setShowCreateForm(true);
      requestAnimationFrame(() => nameInputRef.current?.focus());
    }
    window.addEventListener("mf:open-item-composer", openComposer);
    return () => window.removeEventListener("mf:open-item-composer", openComposer);
  }, [postKind]);

  useEffect(() => {
    let cancelled = false;
    async function loadInventorySummaries() {
      await Promise.resolve();
      if (!session?.accountId || !isOwner) {
        if (!cancelled) setInventorySummaries({});
        return;
      }
      const stockItems = initialItems.filter(isStockFulfillmentItem);
      if (stockItems.length === 0) {
        if (!cancelled) setInventorySummaries({});
        return;
      }
      try {
        const entries = await Promise.all(stockItems.map(async (item) => {
          const summary = await getDigitalInventorySummary(item.id, session.accountId);
          return [item.id, summary] as const;
        }));
        if (!cancelled) setInventorySummaries(Object.fromEntries(entries));
      } catch {
        if (!cancelled) setInventorySummaries({});
      }
    }
    void loadInventorySummaries();
    return () => {
      cancelled = true;
    };
  }, [initialItems, isOwner, session]);

  if (ownerOnly && !isOwner) {
    return null;
  }

  function openLogin() {
    router.push(buildAuthModalHref({ pathname: returnTo, mode: "login", returnTo }));
  }

  function openCreateDialog() {
    if (!session?.accountId) {
      openLogin();
      return;
    }
    setShowCreateForm(true);
    requestAnimationFrame(() => nameInputRef.current?.focus());
  }

  function updateDigitalInventoryDraft(value: string) {
    setDigitalInventoryInput(value);
    const inventoryCount = parseDigitalInventoryPayloads(value).length;
    if (mode === "stock_fulfillment" && inventoryCount > 0) {
      // 中文注释：库存托管的可售数量跟上传库存行数对齐，避免数量计数和真实库存分裂。
      setQuantity(String(inventoryCount));
    }
  }

  function changeCreateMode(nextMode: PostItemFulfillmentMode) {
    setMode(nextMode);
    const inventoryCount = parseDigitalInventoryPayloads(digitalInventoryInput).length;
    if (nextMode === "stock_fulfillment" && inventoryCount > 0) {
      setQuantity(String(inventoryCount));
    }
  }

  function submitItem() {
    if (!session?.accountId) {
      openLogin();
      return;
    }
    const initialInventoryPayloads = parseDigitalInventoryPayloads(digitalInventoryInput);
    const validation = validateNewItemDraft({
      t,
      postKind,
      name,
      deliveryStandard,
      acceptanceCriteria,
      amount,
      difficultyScore,
      quantity: postKind !== "project" && mode === "stock_fulfillment" ? String(initialInventoryPayloads.length) : quantity,
    });
    if (validation) {
      toast.notify({ tone: "error", title: validation.message });
      if (validation.field === "name") {
        nameInputRef.current?.focus();
      } else if (validation.field === "deliveryStandard") {
        deliveryStandardInputRef.current?.focus();
      } else if (validation.field === "acceptanceCriteria") {
        acceptanceCriteriaInputRef.current?.focus();
      } else if (validation.field === "quantity") {
        quantityInputRef.current?.focus();
      } else {
        amountInputRef.current?.focus();
      }
      return;
    }
    if (postKind !== "project" && mode === "stock_fulfillment" && initialInventoryPayloads.length === 0) {
      toast.notify({ tone: "error", title: t("digitalInventory.validation.required") });
      digitalInventoryInputRef.current?.focus();
      return;
    }
    startTransition(async () => {
      try {
        if (postKind === "project") {
          await createProjectItem(postId, {
            actorAccountId: session.accountId,
            name,
            description: description.trim() || undefined,
            deliveryStandard,
            acceptanceCriteria: parseAcceptanceCriteria(acceptanceCriteria),
            difficultyScore: Number(difficultyScore),
            itemType: projectTaskType,
          });
        } else {
          const item = await createPostItem(postId, {
            actorAccountId: session.accountId,
            name,
            description: description.trim() || undefined,
            deliveryStandard,
            acceptanceCriteria: parseAcceptanceCriteria(acceptanceCriteria),
            amount: Number(amount),
            quantity: mode === "stock_fulfillment" ? initialInventoryPayloads.length : Number(quantity),
            agentInstruction: agentInstruction.trim() || undefined,
            mode,
          });
          if (mode === "stock_fulfillment") {
            // 中文注释：库存托管商品在创建后立即写入平台库存，买家 claim 时才能锁定真实库存。
            await uploadDigitalInventory(item.id, {
              actorAccountId: session.accountId,
              payloads: initialInventoryPayloads,
            });
          }
        }
        setName("");
        setDescription("");
        setDeliveryStandard("");
        setAcceptanceCriteria("");
        setAmount("");
        setDifficultyScore("1.0");
        setProjectTaskType("normal");
        setQuantity("1");
        setAgentInstruction("");
        setDigitalInventoryInput("");
        setMode("reviewed_delivery");
        setShowCreateForm(false);
        toast.notify({ tone: "success", title: t(postKind === "offer" ? "toast.itemCreated" : "toast.taskCreated") });
        router.refresh();
      } catch (error) {
        toast.notifyError(error, "ui.order.command.failed");
      }
    });
  }

  function uploadInventoryForItem(itemId: string, rawPayloads: string, onUploaded?: () => void) {
    if (!session?.accountId) return;
    const payloads = parseDigitalInventoryPayloads(rawPayloads);
    if (payloads.length === 0) {
      toast.notify({ tone: "error", title: t("digitalInventory.validation.empty") });
      return;
    }
    setInventoryUploadingItemId(itemId);
    startTransition(async () => {
      try {
        const response = await uploadDigitalInventory(itemId, {
          actorAccountId: session.accountId,
          payloads,
        });
        setInventorySummaries((current) => ({ ...current, [itemId]: response.summary }));
        onUploaded?.();
        toast.notify({ tone: "success", title: t("digitalInventory.toast.uploaded", { count: response.uploaded }) });
        router.refresh();
      } catch (error) {
        toast.notifyError(error, "ui.order.command.failed");
      } finally {
        setInventoryUploadingItemId(null);
      }
    });
  }

  function claimItem(itemId: string) {
    if (!session?.accountId) {
      openLogin();
      return;
    }
    startTransition(async () => {
      try {
        const item = initialItems.find((candidate) => candidate.id === itemId);
        const deliveryInput = item?.deliveryMode === "instant_fulfillment"
          ? { phone: normalizePhoneInput(directDeliveryPhone), amount: item.priceAmount ?? item.budgetAmount ?? 0 }
          : undefined;
        // 中文注释：直接发货订单在领取时收集 provider 入参，付款成功后后端按同一快照执行发货。
        const receipt = await claimPostItemWithDeliveryInput(itemId, session.accountId, buyerNote, deliveryInput);
        const orderNo = typeof receipt.payload?.orderNo === "string" ? receipt.payload.orderNo : "";
        const paymentRequired = shouldOpenPaymentRequired(receipt, session.accountId, initialItems.find((item) => item.id === itemId)?.settlementType);
        router.push(`/orders/${encodeURIComponent(orderNo)}${paymentRequired ? "?payment=required" : ""}`);
        router.refresh();
      } catch (error) {
        toast.notifyError(error, "ui.order.command.failed");
      }
    });
  }

  function startEditItem(item: PostItem) {
    setEditingItemId(item.id);
    setConfirmingCloseItemId(null);
    setEditName(item.title);
    setEditDescription(item.summary ?? "");
    setEditDeliveryStandard(item.deliverableSpec);
    setEditAcceptanceCriteria(item.acceptanceCriteria.join("\n"));
    setEditAmount(String(item.priceAmount ?? item.budgetAmount ?? ""));
    setEditDifficultyScore(String(item.difficultyScore ?? 1));
    setEditProjectTaskType(normalizeProjectTaskType(item.itemKind));
    setEditQuantity(String(item.seatCount ?? 1));
    setEditAgentInstruction(item.agentInstruction ?? "");
    setEditMode(modeFromDeliveryMode(item.deliveryMode));
    setEditInventoryInput("");
  }

  function saveItem(itemId: string) {
    if (!session?.accountId) return;
    startTransition(async () => {
      try {
        const editedInventoryPayloads = parseDigitalInventoryPayloads(editInventoryInput);
        const currentItem = initialItems.find((item) => item.id === itemId);
        const existingStockCapacity = editMode === "stock_fulfillment"
          ? Math.max(currentItem?.seatCount ?? Number(editQuantity) ?? 0, 0)
          : Number(editQuantity);
        await updatePostItem(itemId, {
          actorAccountId: session.accountId,
          name: editName,
          description: editDescription.trim() || undefined,
          deliveryStandard: editDeliveryStandard,
          acceptanceCriteria: parseAcceptanceCriteria(editAcceptanceCriteria),
          amount: postKind === "project" ? undefined : Number(editAmount),
          difficultyScore: postKind === "project" ? Number(editDifficultyScore) : undefined,
          quantity: postKind === "project" ? 1 : editMode === "stock_fulfillment" ? existingStockCapacity + editedInventoryPayloads.length : Number(editQuantity),
          agentInstruction: postKind === "project" ? undefined : editAgentInstruction.trim() || undefined,
          itemType: postKind === "project" ? editProjectTaskType : undefined,
          mode: postKind === "project" ? "reviewed_delivery" : editMode,
        });
        if (postKind !== "project" && editMode === "stock_fulfillment" && editedInventoryPayloads.length > 0) {
          // 中文注释：编辑保存时同步追加库存，卖家可以一次完成改模式和补库存。
          const response = await uploadDigitalInventory(itemId, {
            actorAccountId: session.accountId,
            payloads: editedInventoryPayloads,
          });
          setInventorySummaries((current) => ({ ...current, [itemId]: response.summary }));
          setEditInventoryInput("");
        }
        setEditingItemId(null);
        toast.notify({ tone: "success", title: t("toast.itemSaved") });
        router.refresh();
      } catch (error) {
        toast.notifyError(error, "ui.listing.edit.failed");
      }
    });
  }

  function closeItem(itemId: string) {
    if (!session?.accountId) return;
    startTransition(async () => {
      try {
        await closePostItem(itemId, { actorAccountId: session.accountId, reason: "owner_closed" });
        if (editingItemId === itemId) setEditingItemId(null);
        setConfirmingCloseItemId(null);
        toast.notify({ tone: "success", title: t("toast.itemClosed") });
        router.refresh();
      } catch (error) {
        toast.notifyError(error, "ui.listing.state.failed");
      }
    });
  }

  return (
    <section id={`${postKind}-item-workspace`} className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <div className="text-sm font-black text-[var(--foreground)]">{workspaceTitle}</div>
        </div>
        {canOpenCreateDialog ? (
          <Button variant="outline" size="sm" onClick={openCreateDialog}>
            <Plus className="h-4 w-4" />
            {postKind === "offer" ? t("create.openOffer") : t("create.openTask")}
          </Button>
        ) : null}
      </div>

      <Dialog open={canOpenCreateDialog && showCreateForm} onOpenChange={(open) => {
        if (open) {
          openCreateDialog();
          return;
        }
        setShowCreateForm(false);
      }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{postKind === "offer" ? t("create.offerTitle") : t("create.taskTitle")}</DialogTitle>
            <DialogDescription>{postKind === "offer" ? t("create.offerDescription") : t("create.taskDescription")}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4">
            <FormField label={postKind === "offer" ? t("fields.itemName") : t("fields.taskName")}>
              <input ref={nameInputRef} className="mf-control-field w-full px-3" value={name} onChange={(event) => setName(event.target.value)} placeholder={postKind === "offer" ? t("fields.itemName") : t("fields.taskName")} />
            </FormField>
            <FormField label={t("fields.taskDescription")} optional>
              <input className="mf-control-field w-full px-3" value={description} onChange={(event) => setDescription(event.target.value)} placeholder={t("create.descriptionPlaceholder")} />
            </FormField>
            <FormField label={t("fields.deliveryStandard")}>
              <textarea ref={deliveryStandardInputRef} className="mf-control-field min-h-24 w-full resize-none px-3 py-3" value={deliveryStandard} onChange={(event) => setDeliveryStandard(event.target.value)} placeholder={t("create.deliveryStandardPlaceholder")} />
            </FormField>
            <FormField label={t("fields.acceptanceStandard")}>
              <textarea ref={acceptanceCriteriaInputRef} className="mf-control-field min-h-24 w-full resize-none px-3 py-3" value={acceptanceCriteria} onChange={(event) => setAcceptanceCriteria(event.target.value)} placeholder={t("create.acceptanceCriteriaPlaceholder")} />
            </FormField>
            {postKind === "project" ? (
              <FormField label={t("fields.taskType")}>
                <ProjectTaskTypeSelector value={projectTaskType} onChange={setProjectTaskType} />
              </FormField>
            ) : null}
            <div className="grid gap-3 md:grid-cols-2">
              {postKind === "project" ? (
                <FormField label={t("fields.difficulty")} meta={<DifficultyHelp />}>
                  <DifficultyScoreField ref={amountInputRef} value={difficultyScore} onChange={setDifficultyScore} />
                </FormField>
              ) : (
                <FormField label={amountLabel(t, postKind)}>
                  <input ref={amountInputRef} className="mf-control-field w-full px-3" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder={amountPlaceholder(t, postKind)} inputMode="numeric" />
                </FormField>
              )}
              {postKind === "project" ? null : (
                <FormField label={quantityLabel(t, postKind)}>
                  <input
                    ref={quantityInputRef}
                    className="mf-control-field w-full px-3 read-only:cursor-not-allowed read-only:opacity-70"
                    value={mode === "stock_fulfillment" ? String(parseDigitalInventoryPayloads(digitalInventoryInput).length) : quantity}
                    onChange={(event) => setQuantity(event.target.value)}
                    placeholder={quantityPlaceholder(t, postKind)}
                    inputMode="numeric"
                    readOnly={mode === "stock_fulfillment"}
                    aria-readonly={mode === "stock_fulfillment"}
                  />
                </FormField>
              )}
            </div>
            {postKind === "project" ? null : (
              <FormField label={t("fields.agentInstruction")} optional>
                <textarea className="mf-control-field min-h-24 w-full resize-none px-3 py-3" value={agentInstruction} onChange={(event) => setAgentInstruction(event.target.value)} placeholder={t("create.agentInstructionPlaceholder")} />
              </FormField>
            )}
            {postKind === "project" ? null : (
              <DeliveryModeSelector value={mode} onChange={changeCreateMode} />
            )}
            {postKind === "project" || mode !== "stock_fulfillment" ? null : (
              <DigitalInventoryUploadField
                ref={digitalInventoryInputRef}
                value={digitalInventoryInput}
                onChange={updateDigitalInventoryDraft}
                hint={t("digitalInventory.createHint")}
              />
            )}
          </div>
          <DialogFooter>
            <Button
              variant="primary"
              loading={isPending}
              disabled={isPending}
              onClick={submitItem}
            >
              {postKind === "offer" ? t("create.submitOffer") : t("create.submitTask")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <div className="grid gap-3">
        {initialItems.length > 0 ? initialItems.map((item) => {
          const claimedByMe = session?.accountId === item.claimedByAccountId;
          const canClaim = postIsOpen && (item.status === "open" || item.status === "released");
          const editingThisItem = editingItemId === item.id;
          const ownerCanCloseItem = isOwner && postIsOpen && item.status !== "closed" && item.status !== "archived";
          const ownerCanClaimProjectItem = isOwner && postKind === "project" && canClaim;
          const directDeliveryReady = item.deliveryMode !== "instant_fulfillment" || isValidDirectDeliveryPhone(directDeliveryPhone);
          const claimingThisItem = claimingItemId === item.id;
          const expandedThisItem = expandedItemId === item.id;
          const stockFulfillment = isStockFulfillmentItem(item);
          const inventorySummary = inventorySummaries[item.id];
          return (
            <article key={item.id} className="rounded-[12px] border border-[var(--border)] bg-[var(--background)] p-4 transition hover:border-[rgba(142,164,255,0.34)]">
              <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_220px]">
                <div className="min-w-0 space-y-3">
                  <div className="flex flex-wrap items-center gap-2.5">
                    <span className={statusPillClassName(item.status)}>{itemStatusLabel(item.status, t)}</span>
                    <span className="inline-flex items-center gap-2 rounded-full bg-[var(--surface-control)] px-2.5 py-1 text-xs font-semibold text-[var(--foreground)]">
                      <ItemSettlementText item={item} />
                      {postKind === "project" ? <DifficultyInline value={item.difficultyScore ?? 1} /> : null}
                    </span>
                    {postKind === "project" ? (
                      <span className="inline-flex items-center rounded-full bg-[var(--surface-control)] px-2.5 py-1 text-xs font-semibold text-[var(--muted-foreground)]">
                        {projectTaskTypeLabel(t, item.itemKind)}
                      </span>
                    ) : null}
                    {stockFulfillment ? (
                      <span className="inline-flex items-center gap-1.5 rounded-full border border-[rgba(72,230,174,0.3)] bg-[rgba(72,230,174,0.1)] px-2.5 py-1 text-xs font-semibold text-[var(--accent-green)]">
                        <PackageCheck className="h-3.5 w-3.5" />
                        {t("digitalInventory.badge", { count: inventorySummary?.available ?? 0, hasSummary: inventorySummary ? "yes" : "no" })}
                      </span>
                    ) : null}
                  </div>
                  <div className="text-base font-semibold text-[var(--foreground)]">{item.title}</div>
                  {item.summary ? <p className="max-w-3xl text-sm leading-6 text-[var(--muted-foreground)]">{item.summary}</p> : null}
                  <button
                    type="button"
                    className="inline-flex h-8 items-center gap-1.5 text-xs font-semibold text-[var(--muted-foreground)] transition hover:text-[var(--foreground)]"
                    onClick={() => setExpandedItemId(expandedThisItem ? null : item.id)}
                  >
                    {expandedThisItem ? t("actions.hideDetails") : t("actions.showDetails")}
                    {expandedThisItem ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
                  </button>
                </div>
                <div className="space-y-2 xl:justify-self-end xl:self-start">
                  {isOwner ? (
                    <div className="grid gap-2">
                      {ownerCanClaimProjectItem ? (
                        <Button variant="primary" className="w-full xl:w-[160px]" disabled={isPending} onClick={() => setClaimingItemId(item.id)}>
                          {t("claim.project")}
                        </Button>
                      ) : null}
                      <Button variant="outline" className="w-full xl:w-[160px]" disabled={isPending || !postIsOpen} onClick={() => startEditItem(item)}>
                        <Edit3 className="h-4 w-4" />
                        {t("actions.manage")}
                      </Button>
                    </div>
                  ) : canClaim ? (
                    <Button variant="primary" className="w-full xl:w-[160px]" disabled={isPending} onClick={() => setClaimingItemId(item.id)}>
                      {session ? (postKind === "offer" && item.settlementType === "money" ? t("claim.payFirst") : postKind === "offer" ? t("claim.buy") : t("claim.accept")) : t("claim.login")}
                    </Button>
                  ) : item.activeOrderNo && claimedByMe ? (
                    <Button asChild variant="outline" className="w-full xl:w-[160px]">
                      <Link href={`/orders/${encodeURIComponent(item.activeOrderNo)}`}>{t("orders.continueMine")}</Link>
                    </Button>
                  ) : null}
                  {item.nextProgressDueAt && !canClaim ? (
                    <div className={progressDueClassName(item.nextProgressDueAt, hydratedNowMs)}>
                      <Clock3 className="mr-1 inline h-3.5 w-3.5" />
                      {progressDueLabel(t, item.nextProgressDueAt, hydratedNowMs)}
                    </div>
                  ) : null}
                </div>
              </div>

              <Dialog open={claimingThisItem} onOpenChange={(open) => setClaimingItemId(open ? item.id : null)}>
                <DialogContent className="max-w-lg">
                  <DialogHeader>
                    <DialogTitle>{claimDialogTitle(t, postKind, item.settlementType)}</DialogTitle>
                    <DialogDescription className="line-clamp-1">{item.title}</DialogDescription>
                  </DialogHeader>
                  <FormField label={t("claim.noteLabel")}>
                    <textarea
                      className="mf-control-field min-h-[96px] max-h-[160px] w-full resize-none px-3 py-3 text-sm leading-6"
                      value={buyerNote}
                      onChange={(event) => setBuyerNote(event.target.value)}
                      placeholder={item.buyerNotePlaceholder || (postKind === "project" ? t("claim.projectNotePlaceholder") : t("claim.notePlaceholder"))}
                    />
                  </FormField>
                  {item.deliveryMode === "instant_fulfillment" ? (
                    <DirectDeliveryInput phone={directDeliveryPhone} onPhoneChange={setDirectDeliveryPhone} item={item} />
                  ) : null}
                  {stockFulfillment ? <StockDeliveryNotice /> : null}
                  <DialogFooter>
                    <Button variant="outline" disabled={isPending} onClick={() => setClaimingItemId(null)}>
                      {t("actions.cancel")}
                    </Button>
                    <Button variant="primary" loading={isPending} disabled={isPending || (isOwner && !ownerCanClaimProjectItem) || !directDeliveryReady} onClick={() => claimItem(item.id)}>
                      {claimButtonLabel(t, postKind, item.settlementType, Boolean(session))}
                      <ArrowRight className="h-4 w-4" />
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>

              <Dialog open={editingThisItem} onOpenChange={(open) => setEditingItemId(open ? item.id : null)}>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>{t("actions.editItem")} - {item.title}</DialogTitle>
                  </DialogHeader>
                  <div className="grid gap-4">
                    <FormField label={postKind === "offer" ? t("fields.itemName") : t("fields.taskName")}>
                      <input className="mf-control-field w-full px-3" value={editName} onChange={(event) => setEditName(event.target.value)} placeholder={postKind === "offer" ? t("fields.itemName") : t("fields.taskName")} />
                    </FormField>
                    <FormField label={t("fields.taskDescription")} optional>
                      <input className="mf-control-field w-full px-3" value={editDescription} onChange={(event) => setEditDescription(event.target.value)} placeholder={t("edit.descriptionPlaceholder")} />
                    </FormField>
                    <FormField label={t("fields.deliveryStandard")}>
                      <textarea className="mf-control-field min-h-24 w-full resize-none px-3 py-3" value={editDeliveryStandard} onChange={(event) => setEditDeliveryStandard(event.target.value)} placeholder={t("fields.deliveryStandard")} />
                    </FormField>
                    <FormField label={t("fields.acceptanceStandard")}>
                      <textarea className="mf-control-field min-h-24 w-full resize-none px-3 py-3" value={editAcceptanceCriteria} onChange={(event) => setEditAcceptanceCriteria(event.target.value)} placeholder={t("edit.acceptanceCriteriaPlaceholder")} />
                    </FormField>
                    {postKind === "project" ? (
                      <FormField label={t("fields.taskType")}>
                        <ProjectTaskTypeSelector value={editProjectTaskType} onChange={setEditProjectTaskType} />
                      </FormField>
                    ) : null}
                    <div className="grid gap-3 md:grid-cols-2">
                      {postKind === "project" ? (
                        <FormField label={t("fields.difficulty")} meta={<DifficultyHelp />}>
                          <DifficultyScoreField value={editDifficultyScore} onChange={setEditDifficultyScore} />
                        </FormField>
                      ) : (
                        <FormField label={amountLabel(t, postKind)}>
                          <input className="mf-control-field w-full px-3" value={editAmount} onChange={(event) => setEditAmount(event.target.value)} placeholder={amountPlaceholder(t, postKind)} inputMode="numeric" />
                        </FormField>
                      )}
                      {postKind === "project" ? null : (
                        <FormField label={quantityLabel(t, postKind)}>
                          <input
                            className="mf-control-field w-full px-3 read-only:cursor-not-allowed read-only:opacity-70"
                            value={editMode === "stock_fulfillment"
                              ? String((initialItems.find((candidate) => candidate.id === editingItemId)?.seatCount ?? Number(editQuantity) ?? 0) + parseDigitalInventoryPayloads(editInventoryInput).length)
                              : editQuantity}
                            onChange={(event) => setEditQuantity(event.target.value)}
                            placeholder={quantityPlaceholder(t, postKind)}
                            inputMode="numeric"
                            readOnly={editMode === "stock_fulfillment"}
                            aria-readonly={editMode === "stock_fulfillment"}
                          />
                        </FormField>
                      )}
                    </div>
                    {postKind === "project" ? null : (
                      <FormField label={t("fields.agentInstruction")} optional>
                        <textarea className="mf-control-field min-h-24 w-full resize-none px-3 py-3" value={editAgentInstruction} onChange={(event) => setEditAgentInstruction(event.target.value)} placeholder={t("fields.agentInstruction")} />
                      </FormField>
                    )}
                    {postKind === "project" ? null : (
                      <DeliveryModeSelector value={editMode} onChange={setEditMode} />
                    )}
                    {postKind !== "project" && editMode === "stock_fulfillment" ? (
                      <div className="grid gap-2">
                        <DigitalInventoryUploadField
                          value={editInventoryInput}
                          onChange={setEditInventoryInput}
                          hint={t("digitalInventory.editHint")}
                        />
                        <Button
                          type="button"
                          variant="outline"
                          className="justify-self-start"
                          loading={inventoryUploadingItemId === item.id}
                          disabled={isPending || inventoryUploadingItemId === item.id}
                          onClick={() => uploadInventoryForItem(item.id, editInventoryInput, () => setEditInventoryInput(""))}
                        >
                          <UploadCloud className="h-4 w-4" />
                          {t("digitalInventory.uploadAction")}
                        </Button>
                      </div>
                    ) : null}
                  </div>
                  <DialogFooter className="items-center justify-between">
                    {ownerCanCloseItem ? (
                      <Button variant="danger" disabled={isPending} onClick={() => setConfirmingCloseItemId(item.id)}>
                        <XCircle className="h-4 w-4" />
                        {t("actions.closeItem")}
                      </Button>
                    ) : <span />}
                    <Button
                      variant="primary"
                      loading={isPending}
                      disabled={isPending || editName.trim().length < 3 || editDeliveryStandard.trim().length < 8 || (postKind === "project" ? !isDifficultyScore(editDifficultyScore) : !isPositiveNumber(editAmount) || !isPositiveInteger(editQuantity))}
                      onClick={() => saveItem(item.id)}
                    >
                      <Save className="h-4 w-4" />
                      {t("actions.saveItem")}
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
              <Dialog open={confirmingCloseItemId === item.id} onOpenChange={(open) => !isPending && setConfirmingCloseItemId(open ? item.id : null)}>
                <DialogContent className="max-w-lg" showClose={false}>
                  <DialogHeader>
                    <DialogTitle>{t("actions.closeItem")}</DialogTitle>
                    <DialogDescription>{t("actions.closeConfirm")}</DialogDescription>
                  </DialogHeader>
                  <DialogFooter>
                    <Button variant="outline" disabled={isPending} onClick={() => setConfirmingCloseItemId(null)}>
                      {t("actions.cancelClose")}
                    </Button>
                    <Button variant="danger" loading={isPending} disabled={isPending} onClick={() => closeItem(item.id)}>
                      {t("actions.confirmClose")}
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>

              {expandedThisItem ? (
                <div className="mt-4 grid gap-4 border-t border-[var(--border)] pt-4">
                  <ItemSpec label={t("fields.deliveryStandard")} value={item.deliverableSpec} />
                  <ItemSpec label={t("fields.acceptanceStandard")} value={item.acceptanceCriteria.join("\n")} />
                  {postKind === "project" ? null : (
                    <ItemSpec label={t("fields.agentInstruction")} value={item.agentInstruction || t("defaults.agentInstruction")} />
                  )}
                  {item.deliveryMode === "instant_fulfillment" ? (
                    <ItemSpec icon={Smartphone} label={t("directDelivery.heading")} value={`${item.deliverySlaLabel || t("directDelivery.slaFallback")} · ${item.deliveryProvider || "phone_recharge"} · ${item.deliveryFailurePolicy || t("directDelivery.failurePolicyFallback")}`} />
                  ) : null}
                  {stockFulfillment ? (
                    <StockInventorySummaryPanel summary={inventorySummary} />
                  ) : null}
                </div>
              ) : null}
            </article>
          );
        }) : (
          <EmptyState
            title={t("empty.title")}
            description={t(isOwner ? "empty.ownerDescription" : "empty.viewerDescription")}
            action={canOpenCreateDialog ? (
              <Button variant="outline" size="sm" onClick={openCreateDialog}>
                <Plus className="h-4 w-4" />
                {postKind === "offer" ? t("create.openOffer") : t("create.openTask")}
              </Button>
            ) : null}
          />
        )}
      </div>

    </section>
  );
}

function ItemSettlementText({ item }: { item: PostItem }) {
  const t = useTranslations("PostItems");
  if (item.rewardPreviewShares) {
    return (
      <span className="inline-flex items-center gap-1.5">
        <Coins className="h-3.5 w-3.5" />
        {t("settlement.shares", { count: item.rewardPreviewShares.toLocaleString() })}
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1.5">
      <Banknote className="h-3.5 w-3.5" />
      {item.priceAmount ?? item.budgetAmount ?? t("settlement.pending")} {item.currency ?? ""}
    </span>
  );
}

function DeliveryModeSelector({
  value,
  onChange,
}: {
  value: PostItemFulfillmentMode;
  onChange: (value: PostItemFulfillmentMode) => void;
}) {
  const t = useTranslations("PostItems");
  const options: Array<{ value: PostItemFulfillmentMode; label: string; helper: string }> = [
    { value: "reviewed_delivery", label: t("deliveryMode.reviewed.label"), helper: t("deliveryMode.reviewed.helper") },
    { value: "instant_fulfillment", label: t("deliveryMode.instant.label"), helper: t("deliveryMode.instant.helper") },
    { value: "stock_fulfillment", label: t("deliveryMode.stock.label"), helper: t("deliveryMode.stock.helper") },
  ];
  return (
    <div className="grid gap-2">
      <div className="text-[11px] font-bold uppercase text-[var(--muted-foreground)]">{t("deliveryMode.label")}</div>
      <div className="grid gap-2 md:grid-cols-3">
        {options.map((option) => (
          <button
            key={option.value}
            type="button"
            className={`rounded-[8px] border px-3 py-2 text-left transition ${value === option.value ? "border-[rgba(142,164,255,0.72)] bg-[rgba(142,164,255,0.14)]" : "border-[var(--border)] bg-[var(--surface-2)] hover:border-[rgba(142,164,255,0.42)]"}`}
            onClick={() => onChange(option.value)}
          >
            <div className="text-sm font-black text-[var(--foreground)]">{option.label}</div>
            <div className="mt-1 text-xs leading-5 text-[var(--muted-foreground)]">{option.helper}</div>
          </button>
        ))}
      </div>
    </div>
  );
}

function ProjectTaskTypeSelector({
  value,
  onChange,
}: {
  value: ProjectTaskType;
  onChange: (value: ProjectTaskType) => void;
}) {
  const t = useTranslations("PostItems");
  return (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
      {projectTaskTypeOptions.map((option) => (
        <button
          key={option.value}
          type="button"
          className={`min-h-10 rounded-[8px] border px-3 text-sm font-semibold transition ${value === option.value ? "border-[rgba(142,164,255,0.72)] bg-[rgba(142,164,255,0.14)] text-[var(--foreground)]" : "border-[var(--border)] bg-[var(--surface-2)] text-[var(--muted-foreground)] hover:border-[rgba(142,164,255,0.42)] hover:text-[var(--foreground)]"}`}
          onClick={() => onChange(option.value)}
        >
          {t(option.labelKey)}
        </button>
      ))}
    </div>
  );
}

const DigitalInventoryUploadField = forwardRef<HTMLTextAreaElement, {
  value: string;
  onChange: (value: string) => void;
  hint: string;
}>(function DigitalInventoryUploadField({ value, onChange, hint }, ref) {
  const t = useTranslations("PostItems");
  const count = parseDigitalInventoryPayloads(value).length;
  return (
    <FormField label={t("digitalInventory.fieldLabel")} hint={`${hint}${count > 0 ? t("digitalInventory.currentCount", { count }) : ""}`}>
      <textarea
        ref={ref}
        className="mf-control-field min-h-28 w-full resize-none px-3 py-3 font-mono text-sm leading-6"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={t("digitalInventory.placeholder")}
      />
    </FormField>
  );
});

function StockDeliveryNotice() {
  const t = useTranslations("PostItems");
  return (
    <div className="grid gap-2 rounded-[8px] border border-[rgba(72,230,174,0.24)] bg-[rgba(72,230,174,0.08)] p-3">
      <div className="flex items-center gap-2 text-sm font-black text-[var(--foreground)]">
        <PackageCheck className="h-4 w-4 text-[var(--accent-green)]" />
        {t("digitalInventory.heading")}
      </div>
      <div className="text-xs leading-5 text-[var(--muted-foreground)]">
        {t("digitalInventory.notice")}
      </div>
    </div>
  );
}

function StockInventorySummaryPanel({ summary }: { summary?: DigitalInventorySummary }) {
  const t = useTranslations("PostItems");
  const cells = summary
    ? [
        { label: t("digitalInventory.summary.available"), value: summary.available },
        { label: t("digitalInventory.summary.reserved"), value: summary.reserved },
        { label: t("digitalInventory.summary.delivered"), value: summary.delivered },
        { label: t("digitalInventory.summary.total"), value: summary.total },
      ]
    : [
        { label: t("digitalInventory.summary.inventory"), value: t("digitalInventory.summary.loading") },
      ];
  return (
    <div className="grid gap-3 rounded-[8px] border border-[rgba(72,230,174,0.22)] bg-[rgba(72,230,174,0.06)] p-3">
      <div className="flex items-center gap-2 text-sm font-black text-[var(--foreground)]">
        <PackageCheck className="h-4 w-4 text-[var(--accent-green)]" />
        {t("digitalInventory.heading")}
      </div>
      <div className="grid grid-cols-2 gap-2 md:grid-cols-4">
        {cells.map((cell) => (
          <div key={cell.label} className="rounded-[8px] bg-[var(--surface-2)] px-3 py-2">
            <div className="text-[11px] font-bold uppercase text-[var(--muted-foreground)]">{cell.label}</div>
            <div className="mt-1 text-sm font-black text-[var(--foreground)]">{cell.value}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

function DirectDeliveryInput({
  phone,
  onPhoneChange,
  item,
}: {
  phone: string;
  onPhoneChange: (value: string) => void;
  item: PostItem;
}) {
  const t = useTranslations("PostItems");
  return (
    <div className="grid gap-2 rounded-[8px] border border-[rgba(72,230,174,0.24)] bg-[rgba(72,230,174,0.08)] p-3">
      <div className="flex items-center gap-2 text-sm font-black text-[var(--foreground)]">
        <Smartphone className="h-4 w-4 text-[var(--accent-green)]" />
        {t("directDelivery.heading")}
      </div>
      <input
        className="mf-control-field w-full px-3"
        value={phone}
        onChange={(event) => onPhoneChange(event.target.value)}
        placeholder={t("directDelivery.phonePlaceholder")}
        inputMode="tel"
      />
      <div className="text-xs leading-5 text-[var(--muted-foreground)]">
        {item.deliverySlaLabel || t("directDelivery.slaFallback")} {t("directDelivery.retryHint")}
      </div>
    </div>
  );
}

function ItemSpec({
  icon: Icon,
  label,
  value,
}: {
  icon?: typeof Flag;
  label: string;
  value: string;
}) {
  const RenderIcon = Icon ?? CheckCircle2;
  return (
    <div className="flex gap-3">
      <RenderIcon className="mt-0.5 h-4 w-4 shrink-0 text-[var(--muted-foreground)]" />
      <div>
        <div className="text-xs font-semibold text-[var(--muted-foreground)]">{label}</div>
        <div className="mt-1 text-sm leading-6 text-[var(--foreground)]/88">{value}</div>
      </div>
    </div>
  );
}

function modeFromDeliveryMode(value?: string): PostItemFulfillmentMode {
  if (value === "instant_fulfillment") return "instant_fulfillment";
  if (value === "stock_fulfillment") return "stock_fulfillment";
  return "reviewed_delivery";
}

function normalizePhoneInput(value: string) {
  return value.replace(/\D/g, "");
}

function isValidDirectDeliveryPhone(value: string) {
  return /^1[3-9]\d{9}$/.test(normalizePhoneInput(value));
}

function isStockFulfillmentItem(item: PostItem) {
  return item.deliveryMode === "stock_fulfillment" || item.fulfillmentMode === "stock_fulfillment";
}

function parseDigitalInventoryPayloads(value: string) {
  return Array.from(new Set(value
    .split("\n")
    .map((item) => item.trim())
    .filter(Boolean)));
}

function normalizeProjectTaskType(value?: string): ProjectTaskType {
  const normalized = (value ?? "").toLowerCase();
  if (normalized === "bug" || normalized === "review" || normalized === "dispute") return normalized;
  return "normal";
}

function projectTaskTypeLabel(t: PostItemsTranslator, value?: string) {
  const type = normalizeProjectTaskType(value);
  return t(projectTaskTypeOptions.find((option) => option.value === type)?.labelKey ?? "taskTypes.normal");
}

function itemStatusLabel(status: string, t: PostItemsTranslator) {
  const key = `status.${status}`;
  return t.has(key) ? t(key) : status.replace(/[_-]+/g, " ");
}

function statusPillClassName(status: string) {
  const normalized = status.toLowerCase();
  const base = "inline-flex h-7 items-center rounded-full border px-2.5 text-xs font-semibold";
  if (normalized === "open" || normalized === "released") {
    return `${base} border-[rgba(69,196,155,0.34)] bg-[rgba(69,196,155,0.13)] text-[rgb(85,220,176)]`;
  }
  if (normalized === "locked" || normalized === "in_progress" || normalized === "in_review") {
    return `${base} border-[rgba(240,180,95,0.34)] bg-[rgba(240,180,95,0.13)] text-[rgb(245,190,112)]`;
  }
  if (normalized === "completed" || normalized === "accepted") {
    return `${base} border-[rgba(72,108,230,0.36)] bg-[rgba(72,108,230,0.16)] text-[rgb(142,164,255)]`;
  }
  if (normalized === "closed" || normalized === "archived" || normalized === "cancelled") {
    return `${base} border-[var(--border)] bg-[var(--surface-control)] text-[var(--muted-foreground)]`;
  }
  return `${base} border-[var(--border)] bg-[var(--surface-control)] text-[var(--muted-foreground)]`;
}

function amountPlaceholder(t: PostItemsTranslator, postKind: PostKind) {
  if (postKind === "offer") return t("placeholders.offerAmount");
  if (postKind === "request") return t("placeholders.requestAmount");
  return "";
}

function quantityPlaceholder(t: PostItemsTranslator, postKind: PostKind) {
  if (postKind === "offer") return t("placeholders.offerQuantity");
  if (postKind === "request") return t("placeholders.requestQuantity");
  return "1";
}

const hydratedNowMs = Date.now();
const subscribeHydratedNow = () => () => {};
const getHydratedNowSnapshot = () => hydratedNowMs;
const getHydratedNowServerSnapshot = () => null;

function useHydratedNowMs() {
  // 中文注释：到期态只在客户端挂载后计算，避免 SSR 时间戳和客户端时间戳互相打架。
  return useSyncExternalStore(subscribeHydratedNow, getHydratedNowSnapshot, getHydratedNowServerSnapshot);
}

function progressDueClassName(value: string, nowMs: number | null) {
  const overdue = nowMs == null ? false : Date.parse(value) <= nowMs;
  return [
    "rounded-[8px] border px-3 py-2 text-xs leading-5",
    overdue
      ? "border-[rgba(245,98,98,0.28)] bg-[rgba(245,98,98,0.08)] text-[rgb(255,170,170)]"
      : "border-[var(--border)] bg-[var(--surface-2)] text-[var(--muted-foreground)]",
  ].join(" ");
}

function progressDueLabel(t: PostItemsTranslator, value: string, nowMs: number | null) {
  return nowMs != null && Date.parse(value) <= nowMs
    ? t("progressOverdue", { date: formatDate(value) })
    : t("progressDue", { date: formatDate(value) });
}

function amountLabel(t: PostItemsTranslator, postKind: PostKind) {
  if (postKind === "offer") return t("fields.price");
  if (postKind === "request") return t("fields.budget");
  return t("fields.amount");
}

function quantityLabel(t: PostItemsTranslator, postKind: PostKind) {
  if (postKind === "offer") return t("fields.inventory");
  if (postKind === "request") return t("fields.requestSlots");
  return t("fields.quantity");
}

function claimDialogTitle(t: PostItemsTranslator, postKind: PostKind, settlementType: string) {
  if (postKind === "project") return t("claim.projectTitle");
  if (postKind === "offer" && settlementType === "money") return t("claim.purchaseTitle");
  if (postKind === "offer") return t("claim.buyTitle");
  return t("claim.acceptTitle");
}

function claimButtonLabel(t: PostItemsTranslator, postKind: PostKind, settlementType: string, signedIn: boolean) {
  if (!signedIn) return t("claim.login");
  if (postKind === "project") return t("claim.project");
  if (postKind === "offer" && settlementType === "money") return t("claim.payFirst");
  if (postKind === "offer") return t("claim.buy");
  return t("claim.accept");
}

function FormField({
  label,
  hint,
  meta,
  optional,
  children,
}: {
  label: string;
  hint?: string;
  meta?: ReactNode;
  optional?: boolean;
  children: ReactNode;
}) {
  const t = useTranslations("PostItems");
  return (
    <div className="grid gap-2">
      <span className="flex items-center justify-between gap-3 text-sm font-normal text-[var(--foreground)]">
        <span className="inline-flex items-center gap-1.5">
          {label}
          {optional ? <span className="text-xs font-normal text-[var(--placeholder)]">{t("fields.optional")}</span> : null}
          {meta}
        </span>
      </span>
      {children}
      {hint ? <span className="text-xs leading-5 text-[var(--muted-foreground)]">{hint}</span> : null}
    </div>
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
        <TooltipContent side="top" align="start" className="w-[280px] p-3 text-left">
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

function isPositiveNumber(value: string) {
  const parsed = Number(value.trim());
  return Number.isFinite(parsed) && parsed > 0;
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

function isPositiveInteger(value: string) {
  const parsed = Number(value.trim());
  return Number.isInteger(parsed) && parsed > 0;
}

const DIFFICULTY_PRESETS = [
  { label: "D1", value: "0.5" },
  { label: "D2", value: "1" },
  { label: "D3", value: "2" },
  { label: "D4", value: "4" },
  { label: "D5", value: "8" },
];

const DifficultyScoreField = forwardRef<HTMLInputElement, { value: string; onChange: (value: string) => void }>(function DifficultyScoreField({ value, onChange }, ref) {
  const t = useTranslations("PostItems");
  return (
    <div className="grid gap-2">
      <input
        ref={ref}
        className="mf-control-field w-full px-3"
        value={value}
        onChange={(event) => onChange(sanitizeDifficultyInput(event.target.value))}
        onBlur={() => {
          if (!value) return;
          const parsed = Number(value);
          if (!Number.isFinite(parsed)) return onChange("");
          onChange(String(Math.min(8, Math.max(0.5, parsed))));
        }}
        placeholder={t("placeholders.difficultyScore")}
        inputMode="decimal"
      />
      <div className="grid grid-cols-5 gap-1">
        {DIFFICULTY_PRESETS.map((preset) => (
          <button
            key={preset.label}
            type="button"
            className="rounded-[8px] border border-[var(--border)] bg-[var(--surface-2)] px-2 py-1 text-xs font-black text-[var(--muted-foreground)] transition hover:border-[rgba(142,164,255,0.48)] hover:text-[var(--foreground)]"
            onClick={() => onChange(preset.value)}
          >
            {preset.label}
          </button>
        ))}
      </div>
    </div>
  );
});

function difficultyLabel(value: number) {
  if (value <= 0.5) return "D1";
  if (value <= 1) return "D2";
  if (value <= 2) return "D3";
  if (value <= 4) return "D4";
  return "D5";
}

function DifficultyInline({ value }: { value: number }) {
  const normalized = Number.isFinite(value) ? value : 1;
  const tone = normalized <= 1
    ? "text-[rgb(85,220,176)]"
    : normalized <= 2
      ? "text-[rgb(142,164,255)]"
      : normalized <= 4
        ? "text-[rgb(245,190,112)]"
        : "text-[rgb(255,142,132)]";
  return <span className={`border-l border-[var(--border)] pl-2 ${tone}`}>{difficultyLabel(normalized)}</span>;
}

function parseAcceptanceCriteria(value: string) {
  return value
    .split("\n")
    .map((item) => item.trim())
    .filter(Boolean);
}

type DraftValidationField = "name" | "deliveryStandard" | "acceptanceCriteria" | "amount" | "quantity";

function validateNewItemDraft({
  t,
  postKind,
  name,
  deliveryStandard,
  acceptanceCriteria,
  amount,
  difficultyScore,
  quantity,
}: {
  t: PostItemsTranslator;
  postKind: PostKind;
  name: string;
  deliveryStandard: string;
  acceptanceCriteria: string;
  amount: string;
  difficultyScore: string;
  quantity: string;
}): { field: DraftValidationField; message: string } | null {
  // 中文注释：提交按钮保持可点击，缺字段时直接聚焦问题项，避免用户误以为创建动作失效。
  if (name.trim().length < 3) {
    return { field: "name", message: t("validation.name") };
  }
  if (deliveryStandard.trim().length < 8) {
    return { field: "deliveryStandard", message: t("validation.deliveryStandard") };
  }
  if (parseAcceptanceCriteria(acceptanceCriteria).length === 0) {
    return { field: "acceptanceCriteria", message: t("validation.acceptanceCriteria") };
  }
  if (postKind === "project") {
    return isDifficultyScore(difficultyScore) ? null : { field: "amount", message: t("validation.difficultyScore") };
  }
  if (!isPositiveNumber(amount)) {
    return { field: "amount", message: t("validation.amount") };
  }
  if (!isPositiveInteger(quantity)) {
    return { field: "quantity", message: t("validation.quantity") };
  }
  return null;
}
