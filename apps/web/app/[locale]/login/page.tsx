import {Suspense} from "react";

import {AuthSkeleton} from "@/components/page-skeletons";
import {LoginPageRouteClient} from "@/components/route-clients/login-page-route-client";

export default function LoginPage() {
    return (
        <Suspense fallback={<LoginShellFallback/>}>
            <LoginPageRouteClient/>
        </Suspense>
    );
}

function LoginShellFallback() {
    return (
        <main
            className="relative flex min-h-screen items-center justify-center overflow-hidden bg-[var(--background)] px-4 py-10">
            <div className="relative w-full max-w-[460px]">
                <AuthSkeleton/>
            </div>
        </main>
    );
}
