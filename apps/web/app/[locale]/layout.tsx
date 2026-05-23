import {NextIntlClientProvider} from "next-intl";
import {setRequestLocale} from "next-intl/server";
import {notFound} from "next/navigation";
import {type ReactNode, Suspense} from "react";

import {AppShell} from "@/components/app-shell";
import {AppProviders} from "@/components/app-providers";
import {ClientErrorBoundary} from "@/components/client-error-boundary";
import {type AppLocale, isValidLocale, LOCALES} from "@/i18n/locale-config";

export function generateStaticParams() {
    return LOCALES.map((locale) => ({locale}));
}

export default async function LocaleLayout({
                                               children,
                                               params,
                                           }: {
    children: ReactNode;
    params: Promise<{ locale: string }>;
}) {
    const {locale: candidate} = await params;
    if (!isValidLocale(candidate)) notFound();

    const locale: AppLocale = candidate;
    setRequestLocale(locale);

    return (
        <NextIntlClientProvider>
            <AppProviders>
                <ClientErrorBoundary>
                    <Suspense fallback={children}>
                        <AppShell>{children}</AppShell>
                    </Suspense>
                </ClientErrorBoundary>
            </AppProviders>
        </NextIntlClientProvider>
    );
}
