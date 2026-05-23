import type {OfferPost, Order, ProjectPost, RequestPost} from "@/lib/api";

type OrderLike = Pick<Order, "orderNo">;
type OfferLike = Pick<OfferPost, "offerNo">;
type RequestLike = Pick<RequestPost, "requestNo">;
type ProjectLike = Pick<ProjectPost, "projectLevel" | "projectNo">;

function encodeBusinessNo(value: string) {
    if (!value || !value.trim()) {
        throw new Error("Business number is required for public route");
    }
    return encodeURIComponent(value.trim());
}

export function orderHref(order: OrderLike) {
    return `/orders/${encodeBusinessNo(order.orderNo)}`;
}

export function offerHref(offer: OfferLike) {
    return `/market/offers/${encodeBusinessNo(offer.offerNo)}`;
}

export function requestHref(request: RequestLike) {
    return `/market/requests/${encodeBusinessNo(request.requestNo)}`;
}

export function projectHref(project: ProjectLike) {
    if (project.projectLevel === "root") {
        // 中文注释：Root Project 的公开页面固定收口到产品名 URL。
        return "/market/projects/monopolyfun";
    }
    return `/market/projects/${encodeBusinessNo(project.projectNo)}`;
}

export function profileHref(handle: string) {
    const normalized = handle.trim().replace(/^@+/, "");
    if (!normalized) {
        throw new Error("Handle is required for public profile route");
    }
    return `/profiles/${encodeBusinessNo(normalized)}`;
}

export function postHref(kind: "offer" | "request" | "project", post: OfferLike | RequestLike | ProjectLike) {
    if (kind === "offer") return offerHref(post as OfferLike);
    if (kind === "request") return requestHref(post as RequestLike);
    return projectHref(post as ProjectLike);
}
