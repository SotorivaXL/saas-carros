import { cookies } from "next/headers";
import type { NextResponse } from "next/server";

export const ACCESS_COOKIE = "io_access";
export const REFRESH_COOKIE = "io_refresh";

function resolveSecureCookieFlag() {
    const configured = process.env.AUTH_COOKIE_SECURE?.trim().toLowerCase();
    if (configured === "true" || configured === "1" || configured === "yes" || configured === "on") return true;
    if (configured === "false" || configured === "0" || configured === "no" || configured === "off") return false;

    const appUrl = process.env.NEXT_PUBLIC_APP_URL?.trim().toLowerCase();
    if (appUrl?.startsWith("https://")) return true;

    return process.env.NODE_ENV === "production";
}

const secure = resolveSecureCookieFlag();

const authCookieOptions = {
    httpOnly: true,
    sameSite: "lax" as const,
    secure,
    path: "/",
};

const expiredAuthCookieOptions = {
    ...authCookieOptions,
    maxAge: 0,
};

export async function setAuthCookies(access: string, refresh: string) {
    const c = await cookies();

    c.set(ACCESS_COOKIE, access, {
        ...authCookieOptions,
    });

    c.set(REFRESH_COOKIE, refresh, {
        ...authCookieOptions,
    });
}

export async function clearAuthCookies() {
    const c = await cookies();
    c.set(ACCESS_COOKIE, "", expiredAuthCookieOptions);
    c.set(REFRESH_COOKIE, "", expiredAuthCookieOptions);
}

export function clearAuthCookiesFromResponse(response: NextResponse) {
    response.cookies.set(ACCESS_COOKIE, "", expiredAuthCookieOptions);
    response.cookies.set(REFRESH_COOKIE, "", expiredAuthCookieOptions);
    return response;
}
