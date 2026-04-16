import { NextResponse } from "next/server";

export async function GET() {
    return NextResponse.json({ message: "Modulo de supervisores de IA removido do IOAuto." }, { status: 410 });
}

export async function PUT() {
    return NextResponse.json({ message: "Modulo de supervisores de IA removido do IOAuto." }, { status: 410 });
}
