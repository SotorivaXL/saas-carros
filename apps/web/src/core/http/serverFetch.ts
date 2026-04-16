import { cookies } from "next/headers";
import { ACCESS_COOKIE } from "@/core/auth/cookies";
import { getServerApiBase } from "@/core/http/getServerApiBase";

export async function apiFetch(path: string, init: RequestInit = {}) {
    const base = getServerApiBase();
    const token = (await cookies()).get(ACCESS_COOKIE)?.value;

    const headers = new Headers(init.headers);
    headers.set("Content-Type", "application/json");
    if (token) headers.set("Authorization", `Bearer ${token}`);

    const res = await fetch(`${base}${path}`, { ...init, headers, cache: "no-store" });
    return res;
}
