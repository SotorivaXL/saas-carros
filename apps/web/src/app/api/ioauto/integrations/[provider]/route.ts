import { jsonFromAuthedUpstream } from "@/app/api/_utils/upstreamAuth";

export async function PUT(request: Request, context: { params: Promise<{ provider: string }> }) {
    const { provider } = await context.params;
    const body = await request.text();
    return jsonFromAuthedUpstream(`/ioauto/integrations/${provider}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body,
    }, "Falha ao atualizar a integracao.");
}
