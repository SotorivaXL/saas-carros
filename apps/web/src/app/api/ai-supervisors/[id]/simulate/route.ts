import { NextResponse } from "next/server";

export async function POST() {
    return NextResponse.json({ message: "Modulo de supervisores de IA removido do IOAuto." }, { status: 410 });
}
