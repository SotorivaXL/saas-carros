import { SignupSuccessPanel } from "@/modules/ioauto/components/SignupSuccessPanel";

type SuccessPageProps = {
    searchParams: Promise<{ intent?: string; session_id?: string }>;
};

export default async function AssinaturaSucessoPage({ searchParams }: SuccessPageProps) {
    const params = await searchParams;
    const intentId = params.intent ?? "";
    const sessionId = params.session_id ?? "";

    return (
        <main className="min-h-screen bg-[#f3f3f3] px-6 py-10">
            <div className="mx-auto max-w-3xl">
                <SignupSuccessPanel intentId={intentId} sessionId={sessionId} />
            </div>
        </main>
    );
}
