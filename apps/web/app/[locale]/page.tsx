import {getTranslations} from "next-intl/server";

import {
    type FeedItem,
    type FeedKind,
    type FeedSort,
    filterItems,
    OpportunityGrid,
    OpportunityRail,
    sortItems,
    SurfaceFilterChips,
    SurfaceFilterDivider,
    SurfaceSortChips,
    toFeedItems,
} from "@/components/market-surface";
import {PageContainer} from "@/components/ui/page-layout";
import {getHomeFeed} from "@/lib/api";
import {requirePageData} from "@/lib/api/page-data";

function readKind(value: string | string[] | undefined): FeedKind {
    const normalized = typeof value === "string" ? value : Array.isArray(value) ? value[0] : undefined;
    return normalized === "offer" || normalized === "request" || normalized === "project" ? normalized : "all";
}

function readSort(value: string | string[] | undefined): FeedSort {
    const normalized = typeof value === "string" ? value : Array.isArray(value) ? value[0] : undefined;
    return normalized === "oldest" || normalized === "title" ? normalized : "recent";
}

export default async function HomePage({
                                           searchParams,
                                       }: {
    searchParams?: Promise<Record<string, string | string[] | undefined>>;
}) {
    const params = searchParams ? await searchParams : undefined;
    const t = await getTranslations("Home");
    const selectedKind = readKind(params?.kind);
    const selectedSort = readSort(params?.sort);
    const query = typeof params?.q === "string" ? params.q.trim() : "";
    const usesMarketQuery = selectedKind !== "all" || selectedSort !== "recent" || query.length > 0;
    const feed = await requirePageData(getHomeFeed());
    const accountsById = feed.accountsById;
    const {offers, requests, rootProject, projects} = feed;
    const baseItems = toFeedItems(offers, requests, rootProject ? [rootProject, ...projects] : projects);
    const featuredItems = pinRootProjectFirst(sortItems(baseItems, "recent")).slice(0, 6);
    const sortedItems = sortItems(baseItems, selectedSort);
    const allItems = selectedSort === "recent" ? pinRootProjectFirst(sortedItems) : sortedItems;
    const visibleItems = filterItems(searchItems(allItems, query), selectedKind);
    const browseItems = visibleItems.slice(0, usesMarketQuery ? 24 : 12);

    return (
        <PageContainer width="full" className="space-y-8">
            <section className="space-y-3">
                <h2 className="text-xl font-medium text-[var(--foreground)]">
                    {query ? t("searchTitle") : t("featuredTitle")}
                </h2>
                <OpportunityRail items={featuredItems} accountsById={accountsById}/>
            </section>

            <section className="space-y-3">
                <h2 className="text-xl font-medium text-[var(--foreground)]">{t("browseTitle")}</h2>
                <div className="flex items-center gap-3 overflow-visible pb-1">
                    <SurfaceFilterChips
                        activeKind={selectedKind}
                        hrefBuilder={(kind) => buildHomeHref({kind, sort: selectedSort, query})}
                    />
                    <SurfaceFilterDivider/>
                    <SurfaceSortChips
                        activeSort={selectedSort}
                        hrefBuilder={(sort) => buildHomeHref({kind: selectedKind, sort, query})}
                    />
                </div>
                <OpportunityGrid items={browseItems} accountsById={accountsById}/>
            </section>
        </PageContainer>
    );
}

function buildHomeHref({
                           kind,
                           sort,
                           query,
                       }: {
    kind: "all" | "offer" | "request" | "project";
    sort: FeedSort;
    query: string;
}) {
    const params = new URLSearchParams();
    if (kind !== "all") params.set("kind", kind);
    if (sort !== "recent") params.set("sort", sort);
    if (query) params.set("q", query);
    const serialized = params.toString();
    return serialized ? `/?${serialized}` : "/";
}

function searchItems(items: FeedItem[], query: string) {
    if (!query) return items;
    const normalized = query.toLowerCase();
    return items.filter((item) => [item.title, item.kind === "project" ? item.summary : item.description]
        .some((value) => String(value ?? "").toLowerCase().includes(normalized)));
}

function pinRootProjectFirst(items: FeedItem[]) {
    const rootIndex = items.findIndex((item) => item.kind === "project" && item.projectLevel === "root");
    if (rootIndex <= 0) return items;
    // 中文注释：首页把 MonopolyFun Root Project 固定放在第一张卡，作为系统 Project 的自然入口。
    return [items[rootIndex], ...items.slice(0, rootIndex), ...items.slice(rootIndex + 1)];
}
