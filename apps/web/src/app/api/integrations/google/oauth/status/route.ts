import { NextResponse } from "next/server";
import { fetchWithAuthRetry } from "../_helpers";
import { getServerApiBase } from "@/core/http/getServerApiBase";

export async function GET() {
    const apiBase = getServerApiBase();
    const { response } = await fetchWithAuthRetry(`${apiBase}/api/integrations/google/oauth/status`);
    const data = await response.json().catch(() => null);
    if (!response.ok) {
        return NextResponse.json({ message: data?.message ?? "Falha ao carregar status da integração Google" }, { status: response.status });
    }
    return NextResponse.json(data);
}
