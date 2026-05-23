"use client";

import {usePathname} from "next/navigation";

import {GlobalStatePage, MarketHomeButton} from "@/components/global-state-page";

export default function NotFoundPage() {
    const pathname = usePathname();
    const isEnglish = pathname?.startsWith("/en/");

    return (
        <GlobalStatePage
            kind="notFound"
            title={isEnglish ? "Page not found" : "页面不存在"}
            description={isEnglish ? "This entry is closed, archived, or uses an invalid identifier." : "这个入口对应的公开内容已经关闭、归档或编号无效。"}
            primaryAction={<MarketHomeButton label={isEnglish ? "Back home" : "返回首页"}/>}
        />
    );
}
