import { NextResponse } from "next/server";
import { fetchWithAuthRetry } from "../_helpers";
import { getServerApiBase } from "@/core/http/getServerApiBase";
import { getServerAppBase } from "@/core/http/getServerAppBase";

export async function GET(request: Request) {
    const apiBase = getServerApiBase();
    const appBase = getServerAppBase(request);
    const { response } = await fetchWithAuthRetry(`${apiBase}/api/integrations/google/oauth/start`, {
        method: "GET",
        redirect: "manual",
    });

    if (response.status === 302 || response.status === 303) {
        const location = response.headers.get("location");
        if (location) return NextResponse.redirect(new URL(location));
    }

    const data = await response.json().catch(() => null);
    if (!response.ok) {
        return NextResponse.redirect(new URL(`/protected/agentes-ia?tab=providers&google_oauth=error&message=${encodeURIComponent(data?.message ?? "Falha ao iniciar OAuth Google")}`, appBase));
    }

    if (data?.redirectUrl) {
        return NextResponse.redirect(new URL(String(data.redirectUrl)));
    }

    return NextResponse.redirect(new URL("/protected/agentes-ia?tab=providers&google_oauth=error&message=Resposta%20invalida%20ao%20iniciar%20OAuth%20Google", appBase));
}
