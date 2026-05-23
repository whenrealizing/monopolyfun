import {getTranslations} from "next-intl/server";
import {
    SupportContentBulletList,
    SupportContentPage,
    SupportContentSection
} from "@/components/support-content/support-content-page";

export default async function TermsPage() {
    const t = await getTranslations("Terms");
    const sections = t.raw("sections") as Array<{ heading: string; items: string[] }>;
    return (
        <SupportContentPage
            title={t("title")}
            description={t("description")}
            navItems={sections.map((section, index) => ({id: `terms-section-${index + 1}`, label: section.heading}))}
        >
            {sections.map((section, index) => (
                <SupportContentSection key={section.heading} id={`terms-section-${index + 1}`} title={section.heading}>
                    <SupportContentBulletList items={section.items}/>
                </SupportContentSection>
            ))}
            <SupportContentSection id="terms-note" title={t("note.title")}>
                <p className="text-[14px] leading-5 text-[var(--muted-foreground)]">
                    {t("note.description")}
                </p>
            </SupportContentSection>
        </SupportContentPage>
    );
}
