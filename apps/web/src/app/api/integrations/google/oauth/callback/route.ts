import { NextResponse } from "next/server";
import { getServerApiBase } from "@/core/http/getServerApiBase";
import { getServerAppBase } from "@/core/http/getServerAppBase";

export async function GET(request: Request) {
    const apiBase = getServerApiBase();
    const url = new URL(request.url);
    const code = url.searchParams.get("code") ?? "";
    const state = url.searchParams.get("state") ?? "";
    const error = url.searchParams.get("error") ?? "";

    const backendUrl = new URL(`${apiBase}/api/integrations/google/oauth/callback`);
    if (code) backendUrl.searchParams.set("code", code);
    if (state) backendUrl.searchParams.set("state", state);
    if (error) backendUrl.searchParams.set("error", error);

    const response = await fetch(backendUrl.toString(), {
        method: "GET",
        cache: "no-store",
    });
    const data = await response.json().catch(() => null);
    const appBase = getServerAppBase(request);

    if (!response.ok) {
        const message = data?.message ?? "Falha ao concluir a conexão com o Google";
        return NextResponse.redirect(new URL(`/protected/agentes-ia?tab=providers&google_oauth=error&message=${encodeURIComponent(message)}`, appBase));
    }

    const successUrl = new URL("/protected/agentes-ia", appBase);
    successUrl.searchParams.set("tab", "providers");
    successUrl.searchParams.set("google_oauth", "connected");
    if (typeof data?.googleUserEmail === "string" && data.googleUserEmail.trim()) {
        successUrl.searchParams.set("google_email", data.googleUserEmail.trim());
    }
    return NextResponse.redirect(successUrl);
}
