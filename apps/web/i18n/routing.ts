import {defineRouting} from "next-intl/routing";

import {DEFAULT_LOCALE, LOCALE_COOKIE_NAME, LOCALES} from "@/i18n/locale-config";

export const routing = defineRouting({
    locales: LOCALES,
    defaultLocale: DEFAULT_LOCALE,
    localePrefix: "as-needed",
    localeDetection: false,
    localeCookie: {
        name: LOCALE_COOKIE_NAME,
        path: "/",
        sameSite: "lax",
    },
});
