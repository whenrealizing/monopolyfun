export const LOCALES = ["zh-CN", "en"] as const;
export type AppLocale = (typeof LOCALES)[number];
export const LOCALE_COOKIE_NAME = "monopolyfun-locale";

export const DEFAULT_LOCALE = resolveDefaultLocale();

export function isValidLocale(value: string | undefined): value is AppLocale {
    return LOCALES.some((locale) => locale === value);
}

function resolveDefaultLocale(): AppLocale {
    const candidate = process.env.MONOPOLYFUN_DEFAULT_LOCALE;
    return isValidLocale(candidate) ? candidate : "zh-CN";
}
