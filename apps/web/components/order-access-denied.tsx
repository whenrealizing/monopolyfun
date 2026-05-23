import Link from "next/link";

import { GlobalStatePage } from "@/components/global-state-page";
import { Button } from "@/components/ui/button";

export function OrderAccessDenied({
  orderNo,
  returnHref,
  returnLabel,
  heading = "订单访问受限",
  description = `订单 ${orderNo} 只对买方、交付方、评审员和项目处理人开放。`,
  note = "可读取角色范围包括买方、交付方、评审员和项目处理人。请从自己的订单列表或项目工作台进入对应订单。",
}: {
  orderNo: string;
  returnHref: string;
  returnLabel: string;
  heading?: string;
  description?: string;
  note?: string;
}) {
  return (
    <GlobalStatePage
      kind="forbidden"
      title={heading}
      description={description}
      note={note}
      primaryAction={(
        <Button asChild variant="outline" size="sm">
          <Link href={returnHref}>{returnLabel}</Link>
        </Button>
      )}
    />
  );
}
