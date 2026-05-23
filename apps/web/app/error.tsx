"use client";

import {GlobalStatePage, MarketHomeButton, RetryButton} from "@/components/global-state-page";

export default function GlobalErrorPage({
                                            error,
                                            reset,
                                        }: {
    error: Error & { digest?: string };
    reset: () => void;
}) {
    console.error("Global route error", error);

    return (
        <GlobalStatePage
            kind="error"
            title="页面加载失败"
            description="系统读取当前页面数据时失败。请重新加载，或回到 Market 继续浏览。"
            primaryAction={<RetryButton onClick={reset}/>}
            secondaryAction={<MarketHomeButton/>}
        />
    );
}
