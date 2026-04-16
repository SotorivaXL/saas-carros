import { jsonFromPublicUpstream } from "@/app/api/_utils/upstreamAuth";

export async function POST(request: Request) {
    const body = await request.text();
    return jsonFromPublicUpstream("/public/signup/checkout", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body,
    }, "Falha ao iniciar o checkout.");
}
