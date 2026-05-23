import {Suspense} from "react";

import {OAuthCallbackClient} from "@/components/oauth-callback-client";

export default function OAuthCallbackPage() {
    return (
        <Suspense
            fallback={
                <main
                    className="flex min-h-screen items-center justify-center bg-[var(--background)] px-4 text-[var(--foreground)]">
                    <div className="rounded-[6px] bg-[var(--panel)] px-6 py-5 text-sm text-[var(--muted-foreground)]">
                        正在准备登录...
                    </div>
                </main>
            }
        >
            <OAuthCallbackClient/>
        </Suspense>
    );
}
