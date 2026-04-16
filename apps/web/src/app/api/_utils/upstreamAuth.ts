import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { ACCESS_COOKIE, REFRESH_COOKIE, setAuthCookies } from "@/core/auth/cookies";
import { getServerApiBase } from "@/core/http/getServerApiBase";
import { fetchUpstream, readJsonSafely } from "@/core/http/upstream";

type JsonRecord = Record<string, unknown>;

async function getAccessToken() {
    return (await cookies()).get(ACCESS_COOKIE)?.value;
}

async function refreshAccessToken(apiBase: string) {
    const store = await cookies();
    const refresh = store.get(REFRESH_COOKIE)?.value;
    if (!refresh) return null;

    const refreshResponse = await fetchUpstream(`${apiBase}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: refresh }),
        cache: "no-store",
    });

    if (!refreshResponse.ok) {
        store.set(ACCESS_COOKIE, "", { path: "/", maxAge: 0 });
        store.set(REFRESH_COOKIE, "", { path: "/", maxAge: 0 });
        return null;
    }

    const data = await readJsonSafely<{ accessToken: string; refreshToken: string }>(refreshResponse);
    if (!data?.accessToken || !data.refreshToken) return null;

    await setAuthCookies(data.accessToken, data.refreshToken);
    return data.accessToken;
}

export async function fetchAuthedUpstream(path: string, init: RequestInit = {}) {
    const apiBase = getServerApiBase();
    let access = await getAccessToken();
    if (!access) {
        return {
            upstream: null,
            response: NextResponse.json({ message: "Sessao expirada" }, { status: 401 }),
        };
    }

    const headers = new Headers(init.headers);
    if (!headers.has("Content-Type") && init.body) {
        headers.set("Content-Type", "application/json");
    }
    headers.set("Authorization", `Bearer ${access}`);

    let upstream = await fetchUpstream(`${apiBase}${path}`, {
        ...init,
        headers,
        cache: "no-store",
    });

    if (upstream.status === 401) {
        const refreshed = await refreshAccessToken(apiBase);
        if (!refreshed) {
            return {
                upstream: null,
                response: NextResponse.json({ message: "Sessao expirada" }, { status: 401 }),
            };
        }
        headers.set("Authorization", `Bearer ${refreshed}`);
        upstream = await fetchUpstream(`${apiBase}${path}`, {
            ...init,
            headers,
            cache: "no-store",
        });
    }

    return { upstream, response: null };
}

export async function jsonFromAuthedUpstream(path: string, init: RequestInit = {}, fallbackMessage = "Falha ao processar a requisicao.") {
    const result = await fetchAuthedUpstream(path, init);
    if (result.response) return result.response;

    const payload = await readJsonSafely<JsonRecord | JsonRecord[] | unknown[]>(result.upstream!);
    if (!result.upstream!.ok) {
        const message = typeof payload === "object" && payload && "message" in (payload as JsonRecord)
            ? String((payload as JsonRecord).message ?? fallbackMessage)
            : fallbackMessage;
        return NextResponse.json({ message }, { status: result.upstream!.status });
    }

    return NextResponse.json(payload);
}

export async function jsonFromPublicUpstream(path: string, init: RequestInit = {}, fallbackMessage = "Falha ao processar a requisicao.") {
    const apiBase = getServerApiBase();
    const upstream = await fetchUpstream(`${apiBase}${path}`, {
        ...init,
        cache: "no-store",
    });

    const payload = await readJsonSafely<JsonRecord | JsonRecord[] | unknown[]>(upstream);
    if (!upstream.ok) {
        const message = typeof payload === "object" && payload && "message" in (payload as JsonRecord)
            ? String((payload as JsonRecord).message ?? fallbackMessage)
            : fallbackMessage;
        return NextResponse.json({ message }, { status: upstream.status });
    }

    return NextResponse.json(payload);
}
