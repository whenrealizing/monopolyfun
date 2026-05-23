import {getRequestConfig} from "next-intl/server";

import {DEFAULT_LOCALE, isValidLocale} from "@/i18n/locale-config";
import {loadMessages} from "@/messages/load";

export default getRequestConfig(async ({requestLocale}) => {
    const candidate = await requestLocale;
    const locale = isValidLocale(candidate) ? candidate : DEFAULT_LOCALE;

    return {
        locale,
        messages: await loadMessages(locale),
        timeZone: "Asia/Shanghai",
    };
});
