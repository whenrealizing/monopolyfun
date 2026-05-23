import {ArrowRight, CheckCircle2, Github, GitBranch, ShieldCheck, UsersRound} from "lucide-react";
import {getTranslations} from "next-intl/server";
import type {ReactNode} from "react";

import {Button} from "@/components/ui/button";
import {Link} from "@/i18n/navigation";

type FlowStep = {
    title: string;
    description: string;
};

type TeamMember = {
    name: string;
    role: string;
};

export default async function AboutPage() {
    const t = await getTranslations("About");
    const painItems = t.raw("pain.items") as string[];
    const flowSteps = t.raw("flow.steps") as FlowStep[];
    const mechanismItems = t.raw("mechanism.items") as string[];
    const openSourceItems = t.raw("openSource.items") as string[];
    const team = t.raw("team.members") as TeamMember[];

    return (
        <div className="bg-[var(--background)]">
            <article className="mx-auto max-w-6xl">
                <section className="grid gap-6 border-b border-[var(--border)] pb-8 lg:grid-cols-[minmax(0,1fr)_360px]">
                    <div className="max-w-4xl">
                        <div className="flex items-center gap-0.5">
                            {/* eslint-disable-next-line @next/next/no-img-element */}
                            <img src="/brand/openmonopoly-mark.png" alt="" aria-hidden="true" className="h-8 w-8 shrink-0" />
                            {/* eslint-disable-next-line @next/next/no-img-element */}
                            <img src="/brand/monopoly-fun-wordmark.svg?v=20260523e" alt="monopoly.fun" className="h-auto w-[148px] object-contain" />
                        </div>
                        <h1 className="mt-3 text-[30px] font-bold leading-[1.12] tracking-[0] text-[var(--foreground)] sm:text-[34px]">
                            {t("hero.title")}
                        </h1>
                        <div className="mt-6 flex flex-wrap gap-3">
                            <Button asChild variant="primary" className="gap-2">
                                <Link href="/publish?type=project">
                                    {t("hero.primaryCta")}
                                    <ArrowRight className="h-4 w-4" aria-hidden="true" />
                                </Link>
                            </Button>
                            <Button asChild variant="outline" className="gap-2">
                                <a href="https://github.com/whenrealizing/monopolyfun" target="_blank" rel="noreferrer">
                                    <Github className="h-4 w-4" aria-hidden="true" />
                                    {t("hero.githubCta")}
                                </a>
                            </Button>
                        </div>
                    </div>

                    <div className="rounded-[8px] border border-[var(--border)] bg-[var(--surface-panel)] p-4">
                        <div className="flex items-center gap-2 text-[13px] font-semibold leading-5 text-[var(--foreground)]">
                            <GitBranch className="h-4 w-4 text-[var(--primary)]" aria-hidden="true" />
                            {t("flow.cardTitle")}
                        </div>
                        <div className="mt-4 grid gap-2">
                            {flowSteps.map((step, index) => (
                                <div key={step.title} className="grid grid-cols-[28px_minmax(0,1fr)] gap-3 rounded-[6px] bg-[var(--background)] px-3 py-3">
                                    <div className="flex h-7 w-7 items-center justify-center rounded-full border border-[var(--primary-border)] bg-[var(--primary-soft)] text-[12px] font-semibold text-[var(--primary)]">
                                        {index + 1}
                                    </div>
                                    <div className="min-w-0">
                                        <div className="text-[13px] font-semibold leading-5 text-[var(--foreground)]">{step.title}</div>
                                        <div className="mt-0.5 text-[12px] leading-4 text-[var(--muted-foreground)]">{step.description}</div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                </section>

                <section className="grid gap-5 border-b border-[var(--border)] py-8 lg:grid-cols-[280px_minmax(0,1fr)]">
                    <SectionHeading icon={<UsersRound className="h-5 w-5" />} title={t("pain.title")} />
                    <div className="grid gap-3 sm:grid-cols-3">
                        {painItems.map((item) => (
                            <div key={item} className="rounded-[8px] bg-[var(--surface-panel)] px-4 py-4 text-[14px] leading-6 text-[var(--muted-foreground)]">
                                {item}
                            </div>
                        ))}
                    </div>
                </section>

                <section className="grid gap-5 border-b border-[var(--border)] py-8 lg:grid-cols-[280px_minmax(0,1fr)]">
                    <SectionHeading icon={<CheckCircle2 className="h-5 w-5" />} title={t("mechanism.title")} />
                    <div className="grid gap-3">
                        {mechanismItems.map((item) => (
                            <div key={item} className="flex gap-3 rounded-[8px] bg-[var(--surface-panel)] px-4 py-3 text-[14px] leading-6 text-[var(--muted-foreground)]">
                                <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-[var(--success)]" aria-hidden="true" />
                                <span>{item}</span>
                            </div>
                        ))}
                    </div>
                </section>

                <section className="grid gap-5 border-b border-[var(--border)] py-8 lg:grid-cols-[280px_minmax(0,1fr)]">
                    <SectionHeading icon={<ShieldCheck className="h-5 w-5" />} title={t("openSource.title")} />
                    <div className="rounded-[8px] bg-[var(--surface-panel)] px-4 py-5">
                        <div className="grid gap-3">
                            {openSourceItems.map((item) => (
                                <div key={item} className="flex gap-3 text-[14px] leading-6 text-[var(--muted-foreground)]">
                                    <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-[var(--primary)]" aria-hidden="true" />
                                    <span>{item}</span>
                                </div>
                            ))}
                        </div>
                    </div>
                </section>

                <section className="grid gap-5 py-8 lg:grid-cols-[280px_minmax(0,1fr)]">
                    <SectionHeading icon={<UsersRound className="h-5 w-5" />} title={t("team.title")} />
                    <div className="grid gap-3 sm:grid-cols-3">
                        {team.map((member) => (
                            <div key={member.name} className="rounded-[8px] bg-[var(--surface-panel)] px-4 py-4">
                                <div className="text-[16px] font-semibold leading-5 text-[var(--foreground)]">{member.name}</div>
                                <div className="mt-2 text-[12px] font-semibold leading-4 text-[var(--primary)]">{member.role}</div>
                            </div>
                        ))}
                    </div>
                </section>
            </article>
        </div>
    );
}

function SectionHeading({
    icon,
    title,
}: {
    icon: ReactNode;
    title: string;
}) {
    return (
        <div className="max-w-xl">
            <div className="flex h-9 w-9 items-center justify-center rounded-[8px] border border-[var(--primary-border)] bg-[var(--primary-soft)] text-[var(--primary)]">
                {icon}
            </div>
            <h2 className="mt-3 text-[22px] font-bold leading-7 tracking-[0] text-[var(--foreground)]">{title}</h2>
        </div>
    );
}
