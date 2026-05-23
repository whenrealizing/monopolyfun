import {
  PageContainer,
  PageSection,
  SkeletonAvatar,
  SkeletonBadge,
  SkeletonBlock,
  SkeletonField,
  SkeletonListRow,
  SkeletonPanel,
  SkeletonText,
} from "@/components/ui/page-layout";
import { cn } from "@/lib/utils";

export function FallbackPageSkeleton() {
  return (
    <PageContainer>
      <IntroSkeleton />
      <SkeletonPanel className="space-y-4">
        <SkeletonText lines={3} />
        <div className="grid gap-3 sm:grid-cols-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <SkeletonPanel key={index} className="space-y-3 bg-[var(--surface-1)]">
              <SkeletonBlock className="h-4 w-1/2" />
              <SkeletonText lines={2} />
            </SkeletonPanel>
          ))}
        </div>
      </SkeletonPanel>
    </PageContainer>
  );
}

export function HomeSkeleton() {
  return (
    <PageContainer width="full" className="space-y-8">
      <section className="space-y-3">
        <SkeletonBlock className="h-7 w-40" />
        <div className="-mx-2 overflow-hidden px-2 pb-2 pt-1">
          <div className="flex gap-2.5 pr-10">
            {Array.from({ length: 3 }).map((_, index) => (
              <div key={index} className="w-[min(82vw,360px)] shrink-0">
                <OpportunityCardSkeleton featured />
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="space-y-3">
        <SkeletonBlock className="h-7 w-36" />
        <div className="flex gap-2 overflow-hidden px-1 py-1">
          {Array.from({ length: 6 }).map((_, index) => (
            <SkeletonBadge key={index} className={index === 3 ? "ml-2 w-1" : "w-24"} />
          ))}
        </div>
        <OpportunityGridSkeleton />
      </section>
    </PageContainer>
  );
}

export function OpportunityGridSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div className="grid grid-cols-[repeat(auto-fill,minmax(min(100%,240px),1fr))] gap-x-3 gap-y-5">
      {Array.from({ length: count }).map((_, index) => (
        <OpportunityCardSkeleton key={index} />
      ))}
    </div>
  );
}

function OpportunityCardSkeleton({ featured = false }: { featured?: boolean }) {
  return (
    <div className={cn(
      "mf-skeleton-card relative isolate flex flex-col overflow-hidden rounded-[10px] border border-[var(--border-strong)] bg-[var(--background)] p-3.5",
      featured ? "min-h-[212px]" : "h-[224px]",
    )}>
      <div className="relative z-10 flex items-center justify-between gap-3">
        <SkeletonBadge className="h-6 w-11 rounded-[7px]" />
        <SkeletonBlock className="h-3 w-10" />
      </div>
      <div className={cn("relative z-10 min-w-0", featured ? "mt-5" : "mt-4")}>
        <SkeletonBlock className={cn("h-[21px]", featured ? "w-4/5" : "w-3/4")} />
        <SkeletonBlock className={cn("mt-3 h-5", featured ? "w-24" : "w-20")} />
        <div className="mt-2 grid gap-2">
          <SkeletonBlock className="h-3 w-full rounded-[5px]" />
          <SkeletonBlock className="h-3 w-4/5 rounded-[5px]" />
        </div>
      </div>
      <div className="relative z-10 mt-3 flex min-w-0 items-center gap-2">
        <SkeletonBlock className="h-3 w-20 rounded-[5px]" />
        <SkeletonBlock className="h-1.5 w-1.5 rounded-full" />
        <SkeletonBlock className="h-3 w-12 rounded-[5px]" />
      </div>
      <div className="relative z-10 mt-auto flex items-center gap-2 pt-3">
        <SkeletonAvatar className="h-4 w-4" />
        <SkeletonBlock className="h-3.5 w-24 rounded-[5px]" />
      </div>
    </div>
  );
}

export function MarketDetailSkeleton({ project = false, request = false }: { project?: boolean; request?: boolean }) {
  return (
    <PageContainer width="full" className="space-y-4 pb-12">
      <section className="space-y-5 bg-[var(--background)] px-1 py-3 sm:px-0">
        <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
          <div className="min-w-0 space-y-4">
            <div className="flex flex-wrap gap-2">
              <SkeletonBadge className="w-20" />
              <SkeletonBadge className="w-16" />
            </div>
            <div className="space-y-3">
              {request ? <TradeAmountSkeleton /> : null}
              <SkeletonText lines={2} className="max-w-4xl [&>div]:h-7" />
              <SkeletonText lines={2} className="max-w-3xl" />
              {!request && !project ? <TradeAmountSkeleton /> : null}
            </div>
          </div>
          <OwnerPanelSkeleton />
        </div>
        {project ? (
          <div className="grid gap-2 sm:grid-cols-4">
            {Array.from({ length: 4 }).map((_, index) => (
              <SkeletonPanel key={index} className="space-y-2 px-3 py-2.5">
                <SkeletonBlock className="h-3 w-24" />
                <SkeletonBlock className="h-5 w-16" />
              </SkeletonPanel>
            ))}
          </div>
        ) : request ? (
          <SkeletonPanel className="space-y-2 bg-[rgba(72,108,230,0.08)]">
            <SkeletonBlock className="h-4 w-52" />
            <SkeletonText lines={2} className="max-w-2xl" />
          </SkeletonPanel>
        ) : null}
        <div className="flex justify-end">
          <SkeletonBadge className="h-9 w-28 rounded-[10px]" />
        </div>
      </section>
      <DetailTabsSkeleton />
      <ItemWorkspaceSkeleton />
    </PageContainer>
  );
}

export function PostItemsSkeleton() {
  return (
    <PageContainer width="full" className="space-y-4 pb-16">
      <DetailTabsSkeleton />
      <ItemWorkspaceSkeleton />
    </PageContainer>
  );
}

export function WorkbenchSkeleton() {
  return (
    <PageContainer width="full">
      <section className="min-w-0 bg-[var(--background)]">
        <SkeletonBlock className="mx-1 mb-4 h-7 w-32" />
        <SkeletonBlock className="mx-1 mb-2 h-4 w-24" />
        <div className="flex gap-2 overflow-hidden px-1 py-3">
          {Array.from({ length: 6 }).map((_, index) => (
            <SkeletonBadge key={index} className={index === 4 ? "ml-3 w-24" : "w-24"} />
          ))}
        </div>
        <div className="space-y-2 pt-3">
          {Array.from({ length: 5 }).map((_, index) => (
            <SkeletonListRow key={index} className="lg:grid-cols-[minmax(0,1fr)_160px] lg:items-center" />
          ))}
        </div>
      </section>
    </PageContainer>
  );
}

export function OrderDetailSkeleton() {
  return (
    <PageContainer width="full" className="pb-10">
      <PageSection tone="subtle" size="flush" className="space-y-5 rounded-[6px] border-0 bg-[var(--background)] shadow-none">
        <div className="min-w-0">
          <SkeletonBlock className="h-4 w-28" />
          <SkeletonText lines={2} className="mt-2 max-w-3xl [&>div]:h-6" />
          <SkeletonBlock className="mt-2 h-4 w-2/3" />
        </div>
        <StatusFlowSkeleton />
        <SkeletonPanel className="space-y-2">
          <SkeletonBlock className="h-4 w-40" />
          <SkeletonText lines={2} />
        </SkeletonPanel>
      </PageSection>
      <OrderActionSkeleton />
      <div className="min-w-0 space-y-5">
        <PageSection tone="default" size="flush" className="rounded-[6px] border-0 bg-[var(--background)] shadow-none">
          <div className="grid gap-8 lg:grid-cols-2">
            <SkeletonPanel className="space-y-3">
              <SkeletonBlock className="h-5 w-28" />
              <SkeletonText lines={4} />
            </SkeletonPanel>
            <SkeletonPanel className="space-y-3">
              <SkeletonBlock className="h-5 w-32" />
              <div className="grid gap-2">
                {Array.from({ length: 4 }).map((_, index) => (
                  <SkeletonBlock key={index} className="h-8 w-full rounded-[8px]" />
                ))}
              </div>
            </SkeletonPanel>
          </div>
        </PageSection>
        <TimelineSkeleton />
      </div>
    </PageContainer>
  );
}

export function DisputeSkeleton() {
  return (
    <PageContainer width="full" className="pb-10">
      <PageSection tone="subtle" size="flush" className="rounded-[6px] border-0 bg-[var(--background)] shadow-none">
        <SkeletonPanel className="space-y-3 bg-[rgba(72,108,230,0.08)]">
          <div className="flex flex-wrap gap-2">
            <SkeletonBlock className="h-6 w-52" />
            <SkeletonBadge className="w-20" />
          </div>
          <SkeletonText lines={2} className="max-w-3xl" />
        </SkeletonPanel>
      </PageSection>
      <SkeletonPanel className="space-y-4">
        <SkeletonBlock className="h-5 w-32" />
        <SkeletonText lines={3} />
      </SkeletonPanel>
      <SkeletonPanel className="space-y-4">
        <SkeletonBlock className="h-5 w-32" />
        <div className="grid gap-5 lg:grid-cols-2">
          <EvidenceColumnSkeleton />
          <EvidenceColumnSkeleton />
        </div>
      </SkeletonPanel>
      <OrderActionSkeleton />
      <TimelineSkeleton />
    </PageContainer>
  );
}

export function PublishSkeleton() {
  return (
    <PageContainer width="full">
      <div className="grid gap-x-5 gap-y-3 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="flex min-w-0 flex-col gap-5">
          <SkeletonBlock className="h-7 w-48" />
          <div className="grid gap-2 sm:grid-cols-2">
            {Array.from({ length: 2 }).map((_, index) => (
              <SkeletonPanel key={index} className="min-h-[96px] space-y-3 rounded-[6px]">
                <SkeletonBlock className="h-4 w-28" />
                <SkeletonText lines={2} />
              </SkeletonPanel>
            ))}
          </div>
          <ComposerPanelSkeleton />
          <ComposerPanelSkeleton />
          <ComposerPanelSkeleton rows={4} />
          <SkeletonBadge className="h-10 w-28 rounded-[10px]" />
        </div>
        <aside className="min-w-0 lg:sticky lg:top-3 lg:self-start">
          <SkeletonBlock className="mb-3 h-7 w-24" />
          <OpportunityCardSkeleton />
          <SkeletonPanel className="mt-4 h-14" />
        </aside>
      </div>
    </PageContainer>
  );
}

export function IdentitySkeleton() {
  return (
    <PageContainer className="space-y-0">
      <div className="max-w-5xl space-y-4">
        <ProfileHeroSkeleton actions stats={4} />
        <div className="flex overflow-hidden border-b border-[var(--border)]">
          <div className="flex min-w-max">
            {Array.from({ length: 4 }).map((_, index) => (
              <div key={index} className="relative inline-flex min-h-11 items-center justify-center px-5">
                <SkeletonBlock className="h-4 w-16" />
                {index === 0 ? <span className="absolute bottom-0 left-0 right-0 h-[2px] rounded-full bg-[var(--surface-control-hover)]" /> : null}
              </div>
            ))}
          </div>
        </div>
        <div className="flex overflow-hidden">
          <div className="flex min-w-max gap-2">
            {Array.from({ length: 3 }).map((_, index) => (
              <SkeletonBadge key={index} className="h-8 w-24 rounded-[10px]" />
            ))}
          </div>
        </div>
        <div className="grid gap-2 sm:grid-cols-2">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="flex min-h-[88px] items-center gap-3 rounded-[8px] border border-[var(--border)] bg-[var(--surface-1)] px-4 py-3">
              <SkeletonBlock className="h-10 w-10 shrink-0 rounded-[10px]" />
              <div className="min-w-0 flex-1 space-y-2">
                <SkeletonBlock className="h-4 w-36" />
                <SkeletonBlock className="h-3 w-full" />
              </div>
            </div>
          ))}
        </div>
      </div>
    </PageContainer>
  );
}

export function PublicProfileSkeleton() {
  return (
    <PageContainer width="full" className="space-y-4 pb-16">
      <ProfileHeroSkeleton stats={3} />
      <section className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
        <div className="scroll-mt-6 space-y-3 rounded-[6px] bg-[var(--background)] p-5">
          <SkeletonBlock className="h-4 w-28" />
          <div className="divide-y divide-[var(--border)]">
            {Array.from({ length: 5 }).map((_, index) => (
              <div key={index} className="grid gap-3 rounded-[10px] px-3 py-3 sm:grid-cols-[1fr_auto]">
                <div className="min-w-0 space-y-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <SkeletonBadge className="h-6 w-16" />
                    <SkeletonBlock className="h-4 w-44" />
                  </div>
                  <SkeletonText lines={2} />
                </div>
                <SkeletonBlock className="h-3 w-20 self-center" />
              </div>
            ))}
          </div>
        </div>
        <aside className="space-y-3 rounded-[6px] bg-[var(--background)] p-5">
          <SkeletonBlock className="h-4 w-24" />
          {Array.from({ length: 5 }).map((_, index) => (
            <div key={index} className="flex items-start gap-3 rounded-[8px] py-2">
              <SkeletonBlock className="h-8 w-8 shrink-0 rounded-[8px]" />
              <div className="min-w-0 flex-1 space-y-2">
                <SkeletonBlock className="h-3 w-28" />
                <SkeletonBlock className="h-4 w-full" />
              </div>
            </div>
          ))}
        </aside>
      </section>
    </PageContainer>
  );
}

function ProfileHeroSkeleton({ actions = false, stats }: { actions?: boolean; stats: number }) {
  return (
    <section className="bg-[var(--background)]">
      <div className="space-y-5">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex min-w-0 flex-col gap-4 sm:flex-row sm:items-start">
            <SkeletonAvatar className="h-20 w-20 border-2 border-[var(--border)] sm:h-24 sm:w-24" />
            <div className="min-w-0 space-y-2 pt-0.5">
              <SkeletonBlock className="h-7 w-48" />
              <div className="flex flex-wrap items-center gap-2">
                <SkeletonBadge className="h-6 w-24" />
                <SkeletonBadge className="h-6 w-28" />
              </div>
              <SkeletonBlock className="h-4 w-32" />
              <SkeletonText lines={2} className="max-w-3xl" />
            </div>
          </div>
          {actions ? (
            <div className="flex shrink-0 flex-wrap gap-2 sm:justify-end">
              <SkeletonBadge className="h-9 w-20 rounded-[12px]" />
            </div>
          ) : null}
        </div>
        <div className="flex flex-wrap justify-start gap-x-10 gap-y-3 pt-1">
          {Array.from({ length: stats }).map((_, index) => (
            <div key={index} className="min-w-16 space-y-2 text-center">
              <SkeletonBlock className="mx-auto h-7 w-10" />
              <SkeletonBlock className="mx-auto h-3 w-16" />
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

export function BackofficeSkeleton() {
  return (
    <PageContainer width="full">
      <div className="w-full space-y-4">
        <section className="bg-[var(--background)]">
          <SkeletonBlock className="mb-2 h-7 w-44" />
          <SkeletonText lines={2} className="mb-3 max-w-2xl" />
          <div className="flex gap-2 overflow-hidden rounded-[12px] bg-[var(--background)] p-1">
            {Array.from({ length: 5 }).map((_, index) => (
              <SkeletonBadge key={index} className="h-10 min-w-[92px] rounded-[10px]" />
            ))}
          </div>
        </section>
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <SkeletonPanel key={index} className="space-y-3">
              <SkeletonBlock className="mx-auto h-4 w-24" />
              <SkeletonBlock className="mx-auto h-7 w-16" />
            </SkeletonPanel>
          ))}
        </div>
        <SkeletonPanel className="space-y-3">
          <SkeletonBlock className="h-5 w-32" />
          {Array.from({ length: 5 }).map((_, index) => (
            <SkeletonListRow key={index} />
          ))}
        </SkeletonPanel>
      </div>
    </PageContainer>
  );
}

export function AccountListSkeleton() {
  return (
    <PageContainer>
      <IntroSkeleton />
      <PageSection tone="default" size="flush" className="rounded-[6px] border-0 bg-[var(--background)] shadow-none">
        <div className="divide-y divide-[var(--border)]">
          {Array.from({ length: 6 }).map((_, index) => (
            <div key={index} className="grid gap-3 px-4 py-4 sm:grid-cols-[1fr_auto]">
              <div className="min-w-0 space-y-2">
                <div className="flex flex-wrap gap-2">
                  <SkeletonBlock className="h-4 w-36" />
                  <SkeletonBadge className="w-24" />
                </div>
                <SkeletonText lines={2} />
              </div>
              <SkeletonBlock className="h-4 w-4 self-center" />
            </div>
          ))}
        </div>
      </PageSection>
    </PageContainer>
  );
}

export function SharesSkeleton() {
  return (
    <PageContainer>
      <IntroSkeleton />
      <SkeletonPanel className="space-y-3">
        <SkeletonBlock className="h-5 w-36" />
        <ShareApprovalPanelSkeleton />
      </SkeletonPanel>
      <SkeletonPanel className="space-y-4">
        <div className="grid gap-3 md:grid-cols-[1fr_1fr_auto]">
          <SkeletonField />
          <SkeletonField />
          <SkeletonBlock className="h-10 w-24 self-end rounded-[10px]" />
        </div>
        <div className="divide-y divide-[var(--border)]">
          {Array.from({ length: 4 }).map((_, index) => (
            <SkeletonListRow key={index} className="md:grid-cols-[1fr_auto_auto]" />
          ))}
        </div>
      </SkeletonPanel>
    </PageContainer>
  );
}

export function ShareApprovalPanelSkeleton() {
  return (
    <div className="grid gap-2">
      {Array.from({ length: 2 }).map((_, index) => (
        <div key={index} className="grid gap-3 bg-[var(--background)] px-3 py-3 sm:grid-cols-[minmax(0,1fr)_auto]">
          <div className="min-w-0 space-y-2">
            <div className="flex flex-wrap gap-2">
              <SkeletonBlock className="h-4 w-24" />
              <SkeletonBadge className="w-20" />
              <SkeletonBadge className="w-20" />
            </div>
            <SkeletonBlock className="h-3 w-2/3" />
            <SkeletonBlock className="h-3 w-1/2" />
          </div>
          <SkeletonBadge className="h-9 w-24 rounded-[10px]" />
        </div>
      ))}
    </div>
  );
}

export function AuthSkeleton() {
  return (
    <div className="mx-auto w-full max-w-[420px] overflow-hidden rounded-[6px] bg-[var(--panel)]">
      <div className="px-6 py-5">
        <div className="grid justify-items-center gap-3 pb-5">
          <SkeletonBlock className="h-12 w-12 rounded-[6px]" />
          <SkeletonBlock className="h-8 w-[143px]" />
        </div>
        <div className="grid grid-cols-2 gap-2 border-b border-[rgba(97,97,97,0.72)] pb-3">
          <SkeletonBlock className="h-5 w-20 justify-self-center" />
          <SkeletonBlock className="h-5 w-20 justify-self-center" />
        </div>
        <div className="mt-5 grid gap-4">
          <SkeletonField />
          <SkeletonField />
          <SkeletonBlock className="h-10 w-full rounded-[10px]" />
        </div>
      </div>
    </div>
  );
}

export function SupportContentSkeleton() {
  return (
    <PageContainer width="reading" className="pb-10">
      <PageSection tone="subtle" size="lg" className="space-y-5">
        <IntroSkeleton bare />
        <div className="space-y-5">
          {Array.from({ length: 5 }).map((_, index) => (
            <div key={index} className="space-y-3">
              <SkeletonBlock className="h-5 w-48" />
              <SkeletonText lines={index === 0 ? 4 : 3} />
            </div>
          ))}
        </div>
      </PageSection>
    </PageContainer>
  );
}

export function AgentSkeleton() {
  return (
    <PageContainer width="full">
      <PageSection tone="default" size="flush">
        <div className="grid min-h-[560px] gap-4 lg:grid-cols-[280px_minmax(0,1fr)]">
          <SkeletonPanel className="space-y-3">
            <SkeletonBlock className="h-5 w-28" />
            {Array.from({ length: 6 }).map((_, index) => (
              <SkeletonBadge key={index} className="h-10 w-full rounded-[10px]" />
            ))}
          </SkeletonPanel>
          <SkeletonPanel className="space-y-4">
            <SkeletonBlock className="h-6 w-48" />
            <SkeletonText lines={4} />
            <div className="mt-auto">
              <SkeletonBlock className="h-24 w-full rounded-[12px]" />
            </div>
          </SkeletonPanel>
        </div>
      </PageSection>
    </PageContainer>
  );
}

function IntroSkeleton({ bare = false }: { bare?: boolean }) {
  const content = (
    <div className="space-y-3">
      <SkeletonBlock className="h-6 w-48" />
      <SkeletonText lines={2} className="max-w-3xl" />
    </div>
  );
  if (bare) return content;
  return (
    <PageSection tone="subtle" size="lg" className="rounded-[6px] border-0 bg-[var(--background)] shadow-none">
      {content}
    </PageSection>
  );
}

function TradeAmountSkeleton() {
  return (
    <div className="space-y-2.5">
      <div className="flex flex-wrap gap-2">
        <SkeletonBadge className="h-8 w-28 rounded-[10px]" />
        <SkeletonBadge className="h-8 w-28 rounded-[10px]" />
      </div>
      <SkeletonBlock className="h-10 w-44" />
    </div>
  );
}

function OwnerPanelSkeleton() {
  return (
    <aside className="space-y-4 rounded-[12px] bg-[var(--background)] p-4">
      <SkeletonBlock className="h-3 w-24" />
      <div className="flex items-center gap-3">
        <SkeletonAvatar className="h-12 w-12" />
        <div className="min-w-0 flex-1 space-y-2">
          <SkeletonBlock className="h-4 w-32" />
          <SkeletonBlock className="h-3 w-24" />
        </div>
      </div>
    </aside>
  );
}

function DetailTabsSkeleton() {
  return (
    <div className="space-y-3 rounded-[6px] bg-[var(--background)] p-4">
      <div className="flex gap-2 overflow-hidden">
        {Array.from({ length: 4 }).map((_, index) => (
          <SkeletonBadge key={index} className="h-9 w-24 rounded-[10px]" />
        ))}
      </div>
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {Array.from({ length: 3 }).map((_, index) => (
          <SkeletonListRow key={index} />
        ))}
      </div>
    </div>
  );
}

function ItemWorkspaceSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <SkeletonPanel className="space-y-3 rounded-[6px]">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <SkeletonBlock className="h-5 w-40" />
        <SkeletonBadge className="h-9 w-28 rounded-[10px]" />
      </div>
      <div className="grid gap-2">
        {Array.from({ length: rows }).map((_, index) => (
          <SkeletonListRow key={index} />
        ))}
      </div>
    </SkeletonPanel>
  );
}

function StatusFlowSkeleton() {
  return (
    <div className="grid gap-2 sm:grid-cols-5">
      {Array.from({ length: 5 }).map((_, index) => (
        <SkeletonPanel key={index} className="space-y-2 px-3 py-3">
          <SkeletonBlock className="h-4 w-4" />
          <SkeletonBlock className="h-3 w-20" />
        </SkeletonPanel>
      ))}
    </div>
  );
}

function OrderActionSkeleton() {
  return (
    <SkeletonPanel className="space-y-3">
      <SkeletonBlock className="h-5 w-36" />
      <div className="flex flex-wrap gap-2">
        {Array.from({ length: 3 }).map((_, index) => (
          <SkeletonBadge key={index} className="h-10 w-32 rounded-[10px]" />
        ))}
      </div>
    </SkeletonPanel>
  );
}

function TimelineSkeleton() {
  return (
    <SkeletonPanel className="space-y-3">
      <SkeletonBlock className="h-5 w-32" />
      {Array.from({ length: 3 }).map((_, index) => (
        <div key={index} className="space-y-2 py-2">
          <SkeletonBlock className="h-4 w-44" />
          <SkeletonBlock className="h-3 w-32" />
        </div>
      ))}
    </SkeletonPanel>
  );
}

function EvidenceColumnSkeleton() {
  return (
    <section className="min-w-0">
      <div className="mb-3 flex items-center gap-2">
        <SkeletonBlock className="h-2 w-2 rounded-full" />
        <SkeletonBlock className="h-4 w-28" />
      </div>
      <div className="border-t border-[var(--border)] pt-3">
        <SkeletonBlock className="h-3 w-24" />
        <SkeletonText lines={3} className="mt-2" />
      </div>
    </section>
  );
}

function ComposerPanelSkeleton({ rows = 2 }: { rows?: number }) {
  return (
    <SkeletonPanel className="space-y-4 border border-[var(--border)] p-5">
      {Array.from({ length: rows }).map((_, index) => (
        <SkeletonField key={index} />
      ))}
    </SkeletonPanel>
  );
}
