import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { ACCESS_COOKIE } from "@/core/auth/cookies";
import { getServerApiBase } from "@/core/http/getServerApiBase";

type CreateUserBody = {
    email?: string;
    fullName?: string;
    profileImageUrl?: string;
    jobTitle?: string;
    birthDate?: string;
    password?: string;
    permissionPreset?: string;
    modulePermissions?: string[];
    teamId?: string;
    roles?: string[];
};

async function getAccessToken() {
    return (await cookies()).get(ACCESS_COOKIE)?.value;
}

export async function GET() {
    const apiBase = getServerApiBase();
    const access = await getAccessToken();
    if (!access) return NextResponse.json({ message: "Sem token" }, { status: 401 });

    const res = await fetch(`${apiBase}/users`, {
        headers: { Authorization: `Bearer ${access}` },
        cache: "no-store",
    });

    const data = await res.json().catch(() => null);
    if (!res.ok) return NextResponse.json({ message: data?.message ?? "Falha ao listar colaboradores" }, { status: res.status });
    return NextResponse.json(data);
}

export async function POST(req: Request) {
    const apiBase = getServerApiBase();
    const access = await getAccessToken();
    if (!access) return NextResponse.json({ message: "Sem token" }, { status: 401 });

    const body = (await req.json().catch(() => null)) as CreateUserBody | null;
    if (
        !body?.email ||
        !body?.fullName ||
        !body?.jobTitle ||
        !body?.birthDate ||
        !body?.password ||
        !body?.permissionPreset ||
        !body?.teamId ||
        !Array.isArray(body.roles) ||
        body.roles.length === 0
    ) {
        return NextResponse.json({ message: "Dados inválidos" }, { status: 400 });
    }

    const meRes = await fetch(`${apiBase}/me`, {
        headers: { Authorization: `Bearer ${access}` },
        cache: "no-store",
    });
    if (!meRes.ok) return NextResponse.json({ message: "Não autorizado" }, { status: 401 });
    const me = (await meRes.json()) as { companyId: string };

    const payload = {
        companyId: me.companyId,
        email: body.email,
        fullName: body.fullName,
        profileImageUrl: body.profileImageUrl ?? "",
        jobTitle: body.jobTitle,
        birthDate: body.birthDate,
        password: body.password,
        permissionPreset: body.permissionPreset,
        modulePermissions: Array.isArray(body.modulePermissions) ? body.modulePermissions : [],
        teamId: body.teamId,
        roles: body.roles,
    };

    const res = await fetch(`${apiBase}/users`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${access}`,
        },
        body: JSON.stringify(payload),
    });

    if (!res.ok) {
        const data = await res.json().catch(() => ({ message: "Falha ao criar colaborador" }));
        return NextResponse.json({ message: data.message ?? "Falha ao criar colaborador" }, { status: res.status });
    }

    return NextResponse.json({ ok: true });
}
