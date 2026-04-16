import { jsonFromAuthedUpstream } from "@/app/api/_utils/upstreamAuth";

export async function GET(request: Request) {
    const url = new URL(request.url);
    const query = url.searchParams.toString();
    return jsonFromAuthedUpstream(`/ioauto/dashboard${query ? `?${query}` : ""}`, {}, "Falha ao carregar o dashboard.");
}
