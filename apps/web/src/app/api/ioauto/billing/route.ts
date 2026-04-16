import { jsonFromAuthedUpstream } from "@/app/api/_utils/upstreamAuth";

export async function GET() {
    return jsonFromAuthedUpstream("/ioauto/billing", {}, "Falha ao carregar a assinatura.");
}
