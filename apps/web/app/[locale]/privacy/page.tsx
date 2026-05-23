import {getTranslations} from "next-intl/server";
import {
    SupportContentBulletList,
    SupportContentPage,
    SupportContentSection
} from "@/components/support-content/support-content-page";

export default async function PrivacyPage() {
    const t = await getTranslations("Privacy");
    const sections = t.raw("sections") as Array<{ heading: string; items: string[] }>;
    return (
        <SupportContentPage
            title={t("title")}
            description={t("description")}
            navItems={sections.map((section, index) => ({id: `privacy-section-${index + 1}`, label: section.heading}))}
        >
            {sections.map((section, index) => (
                <SupportContentSection key={section.heading} id={`privacy-section-${index + 1}`}
                                       title={section.heading}>
                    <SupportContentBulletList items={section.items}/>
                </SupportContentSection>
            ))}
            <SupportContentSection id="privacy-note" title={t("note.title")}>
                <p className="text-[14px] leading-5 text-[var(--muted-foreground)]">
                    {t("note.description")}
                </p>
            </SupportContentSection>
        </SupportContentPage>
    );
}
