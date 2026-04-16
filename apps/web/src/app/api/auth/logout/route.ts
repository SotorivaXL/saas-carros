import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { ACCESS_COOKIE, REFRESH_COOKIE, clearAuthCookies, clearAuthCookiesFromResponse } from "@/core/auth/cookies";
import { getServerApiBase } from "@/core/http/getServerApiBase";

async function notifyBackendLogout() {
    const apiBase = getServerApiBase();
    const cookieStore = await cookies();
    const access = cookieStore.get(ACCESS_COOKIE)?.value;
    const refresh = cookieStore.get(REFRESH_COOKIE)?.value;

    await fetch(`${apiBase}/auth/logout`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            ...(access ? { Authorization: `Bearer ${access}` } : {}),
        },
        body: JSON.stringify({ refreshToken: refresh ?? "" }),
    }).catch(() => {});
}

function buildLogoutRedirect() {
    return clearAuthCookiesFromResponse(
        new NextResponse(null, {
            status: 303,
            headers: {
                Location: "/login",
            },
        }),
    );
}

export async function GET() {
    await notifyBackendLogout();
    return buildLogoutRedirect();
}

export async function POST(request: Request) {
    await notifyBackendLogout();

    const acceptsHtml = request.headers.get("accept")?.includes("text/html");
    if (acceptsHtml) {
        return buildLogoutRedirect();
    }

    await clearAuthCookies();
    return NextResponse.json({ ok: true });
}
