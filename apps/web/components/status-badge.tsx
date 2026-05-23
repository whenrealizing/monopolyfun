import { useTranslations } from "next-intl";

import { Badge } from "@/components/ui/badge";
import type {
  InventoryPolicy,
  OrderStatus,
  PostKind,
  ProjectPost,
  RequestPost,
  OfferPost,
  SettlementType,
} from "@/lib/api";

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const t = useTranslations("Common.orderStatus");
  const variant = status === "final_closed"
    ? "outline"
    : status === "final_accepted"
    ? "success"
    : status === "accepted_open"
      ? "warning"
    : status === "disputed"
      ? "danger"
      : status === "delivered"
        ? "warning"
        : "default";

  return <Badge variant={variant}>{t(status)}</Badge>;
}

export function SettlementBadge({ type }: { type: SettlementType }) {
  const t = useTranslations("Common.settlement");
  const variant = type === "money" ? "warning" : "outline";

  return <Badge variant={variant}>{t(type)}</Badge>;
}

export function PostKindBadge({
  kind,
}: {
  kind: PostKind;
}) {
  const t = useTranslations("Common.postKind");
  const variant = kind === "offer"
    ? "success"
    : kind === "request" || kind === "review"
      ? "warning"
      : "default";
  return <Badge variant={variant}>{t(kind)}</Badge>;
}

export function InventoryPolicyBadge({ policy }: { policy: InventoryPolicy }) {
  const t = useTranslations("Common.inventory");
  const variant = policy === "single" ? "outline" : policy === "limited" ? "warning" : "success";
  return <Badge variant={variant}>{t(policy)}</Badge>;
}

export function PostStatusBadge({
  status,
}: {
  status: OfferPost["status"] | RequestPost["status"] | ProjectPost["status"];
}) {
  const t = useTranslations("Common.postStatus");
  const variant =
    status === "open" || status === "active" ? "success"
      : status === "archived" ? "outline"
        : "default";
  return <Badge variant={variant}>{t.has(status) ? t(status) : status}</Badge>;
}
