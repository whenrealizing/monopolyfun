import {BackofficePageClient} from "@/components/route-clients/backoffice-page-client";

export default async function BackofficeRiskAccountPage({params}: { params: Promise<{ accountId: string }> }) {
    const {accountId} = await params;
    return <BackofficePageClient kind="risk-account" accountId={accountId}/>;
}
