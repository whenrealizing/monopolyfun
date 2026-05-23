import {Link} from "@/i18n/navigation";
import {getTranslations} from "next-intl/server";

import {Button} from "@/components/ui/button";
import {SupportContentPage, SupportContentSection} from "@/components/support-content/support-content-page";

export default async function AboutPage() {
    const t = await getTranslations("About");
    const capabilities = t.raw("capabilities") as string[];
    return (
        <SupportContentPage
            eyebrow="monopolyfun"
            title={t("title")}
            description={t("description")}
            navItems={[
                {id: "version", label: t("nav.version")},
                {id: "direction", label: t("nav.direction")},
            ]}
        >
            <SupportContentSection
                id="version"
                title={t("version.title")}
                description={t("version.description")}
            >
                <div className="grid gap-3">
                    {capabilities.map((item) => (
                        <div key={item}
                             className="rounded-[6px] bg-[var(--surface-panel)] px-4 py-3 text-[14px] leading-5 text-[var(--muted-foreground)]">
                            {item}
                        </div>
                    ))}
                </div>
            </SupportContentSection>

            <SupportContentSection
                id="direction"
                title={t("direction.title")}
                description={t("direction.description")}
            >
                <div className="rounded-[6px] bg-[var(--surface-panel)] px-4 py-5 sm:px-5">
                    <div className="flex flex-wrap items-center gap-2">
                        <div
                            className="mr-auto text-[12px] font-semibold leading-4 text-[var(--primary)]">{t("direction.badge")}</div>
                        <Button asChild variant="primary">
                            <Link href="/">{t("direction.marketCta")}</Link>
                        </Button>
                        <Button asChild variant="outline">
                            <Link href="/publish?type=trade">{t("direction.publishCta")}</Link>
                        </Button>
                    </div>
                </div>
            </SupportContentSection>
        </SupportContentPage>
    );
}
