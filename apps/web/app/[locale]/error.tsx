"use client";

import {useTranslations} from "next-intl";

import {GlobalStatePage, MarketHomeButton, RetryButton} from "@/components/global-state-page";

export default function LocaleError({
                                        error,
                                        reset,
                                    }: {
    error: Error & { digest?: string };
    reset: () => void;
}) {
    const t = useTranslations("State.error");
    const actionsT = useTranslations("State.actions");
    console.error("Locale route error", error);

    return (
        <GlobalStatePage
            kind="error"
            title={t("title")}
            description={t("description")}
            primaryAction={<RetryButton onClick={reset} label={actionsT("retry")}/>}
            secondaryAction={<MarketHomeButton label={actionsT("home")}/>}
        />
    );
}
