import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { ACCESS_COOKIE } from "@/core/auth/cookies";

export async function GET() {
    const access = (await cookies()).get(ACCESS_COOKIE)?.value;
    if (!access) return NextResponse.json({ message: "Sem token" }, { status: 401 });
    return NextResponse.json({ accessToken: access });
}
