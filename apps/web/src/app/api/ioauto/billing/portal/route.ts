import { jsonFromAuthedUpstream } from "@/app/api/_utils/upstreamAuth";

export async function POST() {
    return jsonFromAuthedUpstream("/ioauto/billing/portal", { method: "POST" }, "Falha ao abrir o portal da assinatura.");
}
