import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { ACCESS_COOKIE, REFRESH_COOKIE, setAuthCookies } from "@/core/auth/cookies";
import { getServerApiBase } from "@/core/http/getServerApiBase";
import { fetchUpstream, readJsonSafely } from "@/core/http/upstream";

export async function POST() {
    try {
        const apiBase = getServerApiBase();
        const cookieStore = await cookies();
        const refresh = cookieStore.get(REFRESH_COOKIE)?.value;

        if (!refresh) {
            return NextResponse.json({ message: "Sem refresh token" }, { status: 401 });
        }

        const res = await fetchUpstream(`${apiBase}/auth/refresh`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken: refresh }),
        });

        const data = await readJsonSafely<{ accessToken?: string; refreshToken?: string; message?: string }>(res);
        if (!res.ok || !data?.accessToken || !data?.refreshToken) {
            cookieStore.set(ACCESS_COOKIE, "", { path: "/", maxAge: 0 });
            cookieStore.set(REFRESH_COOKIE, "", { path: "/", maxAge: 0 });
            return NextResponse.json({ message: data?.message ?? "Sessao expirada" }, { status: res.status });
        }

        await setAuthCookies(data.accessToken, data.refreshToken);
        return NextResponse.json(data);
    } catch (error) {
        console.error("[auth/refresh] Unable to reach authentication backend.", error);
        return NextResponse.json({ message: "Servidor de autenticacao indisponivel no momento." }, { status: 503 });
    }
}
