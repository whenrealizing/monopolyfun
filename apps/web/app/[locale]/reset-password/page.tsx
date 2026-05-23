import {Suspense} from "react";

import {AuthSkeleton} from "@/components/page-skeletons";
import {ResetPasswordPageClient} from "@/components/route-clients/reset-password-page-client";

export default function ResetPasswordPage() {
    return (
        <Suspense fallback={<ResetPasswordShellFallback/>}>
            <ResetPasswordPageClient/>
        </Suspense>
    );
}

function ResetPasswordShellFallback() {
    return (
        <main
            className="relative flex min-h-screen items-center justify-center overflow-hidden bg-[var(--background)] px-4 py-10">
            <div className="relative w-full max-w-[520px]">
                <AuthSkeleton/>
            </div>
        </main>
    );
}
