import {getTranslations} from "next-intl/server";

import {GlobalStatePage, MarketHomeButton} from "@/components/global-state-page";

export default async function LocaleNotFoundPage() {
    const t = await getTranslations("State.notFound");
    const actionsT = await getTranslations("State.actions");

    return (
        <GlobalStatePage
            kind="notFound"
            title={t("title")}
            description={t("description")}
            primaryAction={<MarketHomeButton label={actionsT("home")}/>}
        />
    );
}
