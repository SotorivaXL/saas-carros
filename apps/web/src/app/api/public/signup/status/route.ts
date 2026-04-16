import { jsonFromPublicUpstream } from "@/app/api/_utils/upstreamAuth";

export async function GET(request: Request) {
    const url = new URL(request.url);
    const intentId = url.searchParams.get("intentId");
    const sessionId = url.searchParams.get("sessionId");
    const query = new URLSearchParams();

    if (intentId) query.set("intentId", intentId);
    if (sessionId) query.set("sessionId", sessionId);

    return jsonFromPublicUpstream(`/public/signup/status?${query.toString()}`, {}, "Falha ao consultar a liberacao do acesso.");
}
