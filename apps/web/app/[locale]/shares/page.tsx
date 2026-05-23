import {Suspense} from "react";

import {SharesSkeleton} from "@/components/page-skeletons";
import {SharesPageClient} from "@/components/route-clients/shares-page-client";

export default function SharesPage() {
    return (
        <Suspense fallback={<SharesSkeleton/>}>
            <SharesPageClient/>
        </Suspense>
    );
}
