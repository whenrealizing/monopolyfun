import {getTranslations} from "next-intl/server";

import {GlobalStatePage, MarketHomeButton} from "@/components/global-state-page";
import {PostItemWorkspacePanel} from "@/components/post-item-workspace-panel";
import {PostKindBadge, PostStatusBadge} from "@/components/status-badge";
import {PageContainer} from "@/components/ui/page-layout";
import {getOfferWorkspace} from "@/lib/api";
import {loadAccessiblePageData} from "@/lib/api/page-data";
import {offerHref} from "@/lib/business-routes";

export default async function OfferItemsPage({params}: { params: Promise<{ offerNo: string }> }) {
    const {offerNo} = await params;
    const t = await getTranslations("PostItems.manage");
    const stateT = await getTranslations("State.forbidden");
    const actionsT = await getTranslations("State.actions");
    const workspaceResult = await loadAccessiblePageData(getOfferWorkspace(offerNo), `/market/offers/${offerNo}/items`);

    if (workspaceResult.status === "forbidden") {
        return (
            <GlobalStatePage
                kind="forbidden"
                title={stateT("title")}
                description={stateT("description")}
                primaryAction={<MarketHomeButton label={actionsT("home")}/>}
            />
        );
    }

    const workspace = workspaceResult.data;
    const {offer, items} = workspace;
    const postTradable = offer.status === "open" && offer.visibility !== "participant_only";

    return (
        <PageContainer width="full" className="space-y-4 pb-16">
            <section className="space-y-4 bg-[var(--background)] p-5 sm:p-6">
                <div className="flex flex-wrap gap-2">
                    <PostKindBadge kind="offer"/>
                    <PostStatusBadge status={offer.status}/>
                </div>
                <div>
                    <h1 className="text-[32px] font-normal leading-tight text-[var(--foreground)]">{t("title")}</h1>
                    <p className="mt-2 max-w-2xl text-sm leading-6 text-[var(--muted-foreground)]">{t("description")}</p>
                </div>
            </section>

            <section className="bg-[var(--background)] p-5 sm:p-6">
                <PostItemWorkspacePanel
                    postKind="offer"
                    postId={offer.offerNo}
                    postStatus={postTradable ? offer.status : "closed"}
                    ownerHandle={offer.actorHandle}
                    returnTo={offerHref(offer)}
                    initialItems={items}
                    ownerOnly
                />
            </section>
        </PageContainer>
    );
}
