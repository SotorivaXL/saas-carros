import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { ACCESS_COOKIE } from "@/core/auth/cookies";
import { getServerApiBase } from "@/core/http/getServerApiBase";

export async function GET() {
    const apiBase = getServerApiBase();
    const access = (await cookies()).get(ACCESS_COOKIE)?.value;
    if (!access) return NextResponse.json({ message: "Sem token" }, { status: 401 });

    const res = await fetch(`${apiBase}/roles`, {
        headers: { Authorization: `Bearer ${access}` },
        cache: "no-store",
    });

    if (!res.ok) {
        const data = await res.json().catch(() => ({ message: "Falha ao listar roles" }));
        return NextResponse.json({ message: data.message ?? "Falha ao listar roles" }, { status: res.status });
    }

    const data = await res.json();
    return NextResponse.json(data);
}
