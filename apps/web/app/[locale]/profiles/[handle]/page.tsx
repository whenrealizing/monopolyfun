import {notFound} from "next/navigation";
import {getLocale, getTranslations} from "next-intl/server";
import {BadgeCheck, BriefcaseBusiness, Github, type LucideIcon, PackageCheck, ShieldCheck} from "lucide-react";

import {Badge} from "@/components/ui/badge";
import {EmptyState, PageContainer} from "@/components/ui/page-layout";
import {ProfileIdentityHero} from "@/components/profile-identity-hero";
import {Link} from "@/i18n/navigation";
import {
    formatDate,
    getPublicProfile,
    type OfferPost,
    type ProjectPost,
    type PublicProfileIdentity,
    type RequestPost,
} from "@/lib/api";
import {offerHref, projectHref, requestHref} from "@/lib/business-routes";

type PublicProfileTranslator = Awaited<ReturnType<typeof getTranslations>>;
type PublicPost =
    | ({ kind: "offer" } & OfferPost)
    | ({ kind: "request" } & RequestPost)
    | ({ kind: "project" } & ProjectPost);

export default async function PublicProfilePage({params}: { params: Promise<{ handle: string }> }) {
    const {handle} = await params;
    const locale = await getLocale();
    const t = await getTranslations("Identity.publicProfile");
    const profile = await getPublicProfile(handle).catch(() => null);

    if (!profile) notFound();

    const account = profile.profile.account;
    const posts = publicProfilePosts(profile.activity.offers, profile.activity.requests, profile.activity.projects);
    const identity = publicIdentity(profile.profile);
    const stats = {
        total: posts.length,
    };
    const trustSignals = [
        {
            icon: BadgeCheck,
            label: t("trust.verifiedIdentity"),
            value: identity.verified ? t("verified") : t("trust.notVerified"),
        },
        {
            icon: ShieldCheck,
            label: t("trust.verificationRecords"),
            value: t("trust.factCount", {count: profile.profile.verifiedFactCount}),
        },
        {
            icon: Github,
            label: t("trust.externalAccounts"),
            value: t("trust.externalCount", {count: profile.profile.linkedAccounts.length}),
        },
        {
            icon: PackageCheck,
            label: t("trust.publicActivity"),
            value: t("trust.postCount", {count: stats.total}),
        },
    ];

    return (
        <PageContainer width="full" className="space-y-4 pb-16">
            <ProfileIdentityHero
                compact
                displayName={identity.displayName}
                handle={identity.handle}
                avatarUrl={identity.avatarUrl}
                summary={account.agentSummary}
                emptySummary={t("emptyBio")}
                stats={[
                    {label: t("metrics.posts"), value: formatCount(stats.total, locale)},
                    {label: t("metrics.verifiedFacts"), value: formatCount(profile.profile.verifiedFactCount, locale)},
                    {
                        label: t("metrics.externalAccounts"),
                        value: formatCount(profile.profile.linkedAccounts.length, locale)
                    },
                ]}
                badges={(
                    <>
                        {identity.verified ?
                            <Badge variant="success"><BadgeCheck className="h-3.5 w-3.5"/>{t("verified")}
                            </Badge> : null}
                        <Badge variant="outline">{identity.sourceLabel}</Badge>
                    </>
                )}
            />

            <section className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
                <div id="public-posts" className="scroll-mt-6 space-y-3 rounded-[6px] bg-[var(--background)] p-5">
                    <div>
                        <h2 className="text-sm font-black text-[var(--foreground)]">{t("publicPosts.title")}</h2>
                    </div>
                    {posts.length > 0 ? (
                        <div className="divide-y divide-[var(--border)]">
                            {posts.slice(0, 8).map((post) => (
                                <Link key={`${post.kind}:${postId(post)}`} href={postHref(post)}
                                      className="grid gap-3 rounded-[10px] px-3 py-3 transition hover:bg-[var(--surface-control-hover)] sm:grid-cols-[1fr_auto]">
                                    <div className="min-w-0">
                                        <div className="flex flex-wrap items-center gap-2">
                                            <Badge variant="outline">{postKindLabel(post.kind, t)}</Badge>
                                            <span
                                                className="truncate text-sm font-black text-[var(--foreground)]">{post.title}</span>
                                        </div>
                                        <p className="mt-1 line-clamp-2 text-sm leading-6 text-[var(--muted-foreground)]">{postSummary(post) || t("publicPosts.emptySummary")}</p>
                                    </div>
                                    <div
                                        className="self-center text-xs font-semibold text-[var(--muted-foreground)]">{formatDate(post.updatedAt)}</div>
                                </Link>
                            ))}
                        </div>
                    ) : (
                        <EmptyState
                            compact
                            title={t("publicPosts.empty")}
                            description={t("publicPosts.emptyDescription")}
                            action={(
                                <Link href="/market"
                                      className="inline-flex h-9 items-center bg-[var(--surface-control)] px-3 text-sm font-bold text-[var(--foreground)] transition hover:bg-[var(--surface-control-hover)]">
                                    {t("publicPosts.exploreMarket")}
                                </Link>
                            )}
                        />
                    )}
                </div>

                <aside className="space-y-3 rounded-[6px] bg-[var(--background)] p-5">
                    <div>
                        <h2 className="text-sm font-black text-[var(--foreground)]">{t("trust.title")}</h2>
                    </div>
                    <IdentityFact icon={BriefcaseBusiness} label={t("identity.summary")}
                                  value={account.agentSummary || t("professionEmpty")}/>
                    {trustSignals.map((signal) => (
                        <IdentityFact key={signal.label} icon={signal.icon} label={signal.label} value={signal.value}/>
                    ))}
                </aside>
            </section>
        </PageContainer>
    );
}

function normalizeHandle(value: string | undefined | null) {
    return String(value ?? "").replace(/^@+/, "").trim().toLowerCase();
}

function publicIdentity(profile: PublicProfileIdentity) {
    const displaySkin = profile.displaySkin;
    const account = profile.account;
    const handle = normalizeHandle(displaySkin?.displayHandle || account.handle);
    return {
        displayName: displaySkin?.displayName ?? account.displayName,
        handle,
        avatarUrl: displaySkin?.avatarUrl ?? null,
        verified: profile.verified === true || displaySkin?.verified === true,
        sourceLabel: displaySkin?.provider === "github" ? "GitHub" : "monopolyfun",
    };
}

function publicProfilePosts(offers: OfferPost[], requests: RequestPost[], projects: ProjectPost[]): PublicPost[] {
    return [
        ...offers.map((offer) => ({...offer, kind: "offer" as const})),
        ...requests.map((request) => ({...request, kind: "request" as const})),
        ...projects.map((project) => ({...project, kind: "project" as const})),
    ].sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt));
}

function postId(post: PublicPost) {
    if (post.kind === "offer") return post.offerNo;
    if (post.kind === "request") return post.requestNo;
    return post.projectNo;
}

function postHref(post: PublicPost) {
    if (post.kind === "offer") return offerHref(post);
    if (post.kind === "request") return requestHref(post);
    return projectHref(post);
}

function postSummary(post: PublicPost) {
    if (post.kind === "project") return post.description ?? post.summary ?? post.oneSentence;
    return post.description;
}

function postKindLabel(kind: PublicPost["kind"], t: PublicProfileTranslator) {
    return t(`kind.${kind}`);
}

function formatCount(value: number, locale: string) {
    return new Intl.NumberFormat(locale).format(value);
}

function IdentityFact({icon: Icon, label, value}: { icon: LucideIcon; label: string; value: string }) {
    return (
        <div className="flex min-w-0 gap-3 py-2">
            <Icon className="mt-0.5 h-4 w-4 shrink-0 text-[var(--accent-blue)]"/>
            <div className="min-w-0">
                <div
                    className="text-[11px] font-bold uppercase tracking-[0.12em] text-[var(--muted-foreground)]">{label}</div>
                <div className="mt-1 break-words text-sm font-semibold leading-6 text-[var(--foreground)]">{value}</div>
            </div>
        </div>
    );
}
