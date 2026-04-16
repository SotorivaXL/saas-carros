import { jsonFromAuthedUpstream } from "@/app/api/_utils/upstreamAuth";

export async function GET() {
    return jsonFromAuthedUpstream("/ioauto/vehicles", {}, "Falha ao listar os veiculos.");
}

export async function POST(request: Request) {
    const body = await request.text();
    return jsonFromAuthedUpstream("/ioauto/vehicles", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body,
    }, "Falha ao criar o veiculo.");
}
