import type {Metadata} from "next";
import {headers} from "next/headers";
import type {ReactNode} from "react";

import {DEFAULT_LOCALE, isValidLocale} from "@/i18n/locale-config";
import "./globals.css";

export const metadata: Metadata = {
    title: "monopolyfun",
    description: "OpenCompany 任务、执行、结果、验收和售后工作台。",
};

export default async function RootLayout({children}: Readonly<{ children: ReactNode }>) {
    const requestHeaders = await headers();
    const candidate = requestHeaders.get("X-NEXT-INTL-LOCALE") ?? undefined;
    const locale = isValidLocale(candidate) ? candidate : DEFAULT_LOCALE;

    return (
        // 中文注释：浏览器翻译扩展会在 hydrate 前给 html 注入属性，根节点属性差异交给 React 静默处理。
        <html lang={locale} data-theme="dark" suppressHydrationWarning>
        <body>{children}</body>
        </html>
    );
}
