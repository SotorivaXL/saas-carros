import { SignupSuccessPanel } from "@/modules/ioauto/components/SignupSuccessPanel";

type SuccessPageProps = {
    searchParams: Promise<{ intent?: string; session_id?: string }>;
};

export default async function AssinaturaSucessoPage({ searchParams }: SuccessPageProps) {
    const params = await searchParams;
    const intentId = params.intent ?? "";
    const sessionId = params.session_id ?? "";

    return (
        <main className="min-h-screen bg-[radial-gradient(circle_at_top_left,rgba(107,0,227,0.12),transparent_28%),linear-gradient(180deg,#f6f1ff_0%,#f4f4f6_60%,#f7f3ff_100%)] px-6 py-10">
            <div className="mx-auto max-w-3xl">
                <SignupSuccessPanel intentId={intentId} sessionId={sessionId} />
            </div>
        </main>
    );
}
