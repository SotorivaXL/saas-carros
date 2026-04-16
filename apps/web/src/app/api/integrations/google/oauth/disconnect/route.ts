import { NextResponse } from "next/server";
import { fetchWithAuthRetry } from "../_helpers";
import { getServerApiBase } from "@/core/http/getServerApiBase";

export async function POST() {
    const apiBase = getServerApiBase();
    const { response } = await fetchWithAuthRetry(`${apiBase}/api/integrations/google/oauth/disconnect`, {
        method: "POST",
    });
    if (!response.ok) {
        const data = await response.json().catch(() => null);
        return NextResponse.json({ message: data?.message ?? "Falha ao desconectar integração Google" }, { status: response.status });
    }
    return new NextResponse(null, { status: 204 });
}
