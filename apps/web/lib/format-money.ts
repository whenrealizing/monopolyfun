type SupportedLocale = "zh-CN" | "en" | string;

function normalizedCurrency(currency: string | null | undefined) {
    return (currency ?? "USD").trim().toUpperCase() || "USD";
}

export function formatMajorMoney(amount: number, currency?: string | null, locale: SupportedLocale = "en") {
    const resolvedCurrency = normalizedCurrency(currency);
    try {
        return new Intl.NumberFormat(locale, {
            style: "currency",
            currency: resolvedCurrency,
            currencyDisplay: "narrowSymbol",
        }).format(amount);
    } catch {
        const value = Number.isInteger(amount) ? amount.toFixed(0) : amount.toFixed(2);
        return `${value} ${resolvedCurrency}`;
    }
}

export function formatMinorMoney(amountMinor: number, currency?: string | null, locale: SupportedLocale = "en") {
    return formatMajorMoney(amountMinor / 100, currency, locale);
}
