import {Suspense} from "react";

import {PublishSkeleton} from "@/components/page-skeletons";
import {PublishPageClient} from "@/components/route-clients/publish-page-client";

export default function PublishPage() {
    return (
        <Suspense fallback={<PublishSkeleton/>}>
            <PublishPageClient/>
        </Suspense>
    );
}
