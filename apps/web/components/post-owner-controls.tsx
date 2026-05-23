"use client";

import { Link, useRouter } from "@/i18n/navigation";
import { useState, useSyncExternalStore, useTransition } from "react";
import { Edit3, Save, XCircle } from "lucide-react";
import { useTranslations } from "next-intl";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useToast } from "@/components/ui/toast";
import {
  closeOfferPost,
  closeRequestPost,
  type OfferPost,
  type PostKind,
  type ProjectPost,
  type RequestPost,
  updateOfferPost,
  updateProjectPost,
  updateRequestPost,
} from "@/lib/api";
import { readStoredSession, subscribeSession } from "@/lib/client-preferences";

type PostOwnerTranslator = ReturnType<typeof useTranslations>;

type EditablePost =
  | ({ kind: "offer" } & OfferPost)
  | ({ kind: "request" } & RequestPost)
  | ({ kind: "project" } & ProjectPost);

export function PostOwnerControls({ post }: { post: EditablePost }) {
  const t = useTranslations("PostOwnerControls");
  const toast = useToast();
  const router = useRouter();
  const session = useSyncExternalStore(subscribeSession, readStoredSession, () => null);
  const [isPending, startTransition] = useTransition();
  const [editing, setEditing] = useState(false);
  const [confirmingClose, setConfirmingClose] = useState(false);
  const [title, setTitle] = useState(post.title);
  const [description, setDescription] = useState(descriptionOf(post));
  const [goal, setGoal] = useState(post.kind === "project" ? post.goal ?? "" : "");
  const ownerHandle = post.kind === "project" ? post.ownerHandle : post.actorHandle;
  // 中文注释：公开页面的 owner 判断只对齐 handle，命令提交仍使用当前登录会话的内部账号 id。
  const isOwner = Boolean(session?.handle && ownerHandle && normalizeHandle(session.handle) === normalizeHandle(ownerHandle));
  const postNo = post.kind === "offer" ? post.offerNo : post.kind === "request" ? post.requestNo : post.projectNo;

  if (!isOwner) return null;

  function savePost() {
    if (!session?.accountId) return;
    startTransition(async () => {
      try {
        if (post.kind === "offer") {
          await updateOfferPost(postNo, {
            actorAccountId: session.accountId,
            title,
            description,
            deliveryStandard: post.deliveryStandard,
            currency: post.currency,
            paymentMethod: post.paymentMethod,
            paymentProfile: post.paymentProfile,
            paymentNetwork: post.paymentNetwork,
            paymentAsset: post.paymentAsset,
            paymentRecipient: post.paymentRecipient,
          });
        }
        if (post.kind === "request") {
          await updateRequestPost(postNo, {
            actorAccountId: session.accountId,
            title,
            description,
            deliveryStandard: post.deliveryStandard,
            currency: post.currency,
            paymentMethod: post.paymentMethod,
            paymentProfile: post.paymentProfile,
            paymentNetwork: post.paymentNetwork,
            paymentAsset: post.paymentAsset,
            paymentRecipient: post.paymentRecipient,
            deadlineAt: post.deadlineAt,
          });
        }
        if (post.kind === "project") {
          await updateProjectPost(postNo, {
            actorAccountId: session.accountId,
            title,
            description,
            goal,
          });
        }
        setEditing(false);
        toast.notify({ tone: "success", title: t("toast.saved") });
        router.refresh();
      } catch (error) {
        toast.notifyError(error, "ui.listing.edit.failed");
      }
    });
  }

  function resetDraft() {
    setTitle(post.title);
    setDescription(descriptionOf(post));
    setGoal(post.kind === "project" ? post.goal ?? "" : "");
  }

  function closeEditDialog() {
    resetDraft();
    setEditing(false);
  }

  function closePost() {
    if (!session?.accountId || post.kind === "project") return;
    startTransition(async () => {
      try {
        if (post.kind === "offer") {
          await closeOfferPost(postNo, { actorAccountId: session.accountId, reason: "owner_closed" });
        }
        if (post.kind === "request") {
          await closeRequestPost(postNo, { actorAccountId: session.accountId, reason: "owner_closed" });
        }
        setConfirmingClose(false);
        toast.notify({ tone: "success", title: t(`toast.${post.kind}Closed`) });
        router.refresh();
      } catch (error) {
        toast.notifyError(error, "ui.listing.state.failed");
      }
    });
  }

  const canClose = post.kind !== "project" && post.status === "open";
  const saveDisabled = isPending || title.trim().length < 2 || description.trim().length < 8;

  return (
    <section className="bg-[var(--background)] px-1 py-2 sm:px-0">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-sm font-black text-[var(--foreground)]">{t("title")}</div>
          <div className="mt-1 text-xs font-semibold text-[var(--muted-foreground)]">{ownerLabel(t, post.kind)}</div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="outline" onClick={() => setEditing(true)}>
            <Edit3 className="h-4 w-4" />
            {t("actions.edit")}
          </Button>
          {post.kind === "offer" ? (
            <Button asChild size="sm" variant="outline">
              <Link href={`/market/offers/${encodeURIComponent(postNo)}/items`}>{t("actions.manageItems")}</Link>
            </Button>
          ) : null}
          {canClose ? (
            <Button size="sm" variant="danger" loading={isPending} onClick={() => setConfirmingClose(true)}>
              <XCircle className="h-4 w-4" />
              {t("actions.close")}
            </Button>
          ) : null}
        </div>
      </div>

      <Dialog open={confirmingClose} onOpenChange={(open) => !isPending && setConfirmingClose(open)}>
        <DialogContent className="max-w-lg" showClose={false}>
          <DialogHeader>
            <DialogTitle>{t("closeConfirm.title")}</DialogTitle>
            <DialogDescription>{t(`closeConfirm.${post.kind}`)}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button size="sm" variant="outline" disabled={isPending} onClick={() => setConfirmingClose(false)}>
              {t("closeConfirm.cancel")}
            </Button>
            <Button size="sm" variant="danger" loading={isPending} onClick={closePost}>
              {t("closeConfirm.confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={editing} onOpenChange={(open) => {
        if (open) {
          setEditing(true);
          return;
        }
        if (!isPending) closeEditDialog();
      }}>
        <DialogContent className="max-w-xl" showClose={false}>
          <DialogHeader>
            <DialogTitle>{t("actions.edit")}</DialogTitle>
            <DialogDescription>{ownerLabel(t, post.kind)}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-3">
            <input className="mf-control-field h-11 w-full px-3" value={title} onChange={(event) => setTitle(event.target.value)} placeholder={t("fields.title")} />
            <textarea className="mf-control-field min-h-28 max-h-[220px] w-full resize-none overflow-y-auto px-3 py-3" value={description} onChange={(event) => setDescription(event.target.value)} placeholder={t("fields.description")} />
            {post.kind === "project" ? (
              <textarea className="mf-control-field min-h-24 max-h-[180px] w-full resize-none overflow-y-auto px-3 py-3" value={goal} onChange={(event) => setGoal(event.target.value)} placeholder={t("fields.companyGoal")} />
            ) : null}
          </div>
          <DialogFooter>
            <Button size="sm" variant="outline" disabled={isPending} onClick={closeEditDialog}>
              {t("actions.cancel")}
            </Button>
            <Button size="sm" variant="primary" loading={isPending} disabled={saveDisabled} onClick={savePost}>
              <Save className="h-4 w-4" />
              {t("actions.save")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

    </section>
  );
}

function descriptionOf(post: EditablePost) {
  if (post.kind === "project") return post.description ?? post.summary ?? post.oneSentence;
  return post.description;
}

function ownerLabel(t: PostOwnerTranslator, kind: PostKind) {
  if (kind === "project") return t("labels.project");
  if (kind === "offer") return t("labels.offer");
  return t("labels.request");
}

function normalizeHandle(value: string) {
  return value.replace(/^@+/, "").trim().toLowerCase();
}
