"use client";

import Link from "next/link";
import { BellRing, LogOut } from "lucide-react";

export function ProtectedNotificationsRail() {
    return (
        <aside className="hidden w-[70px] shrink-0 border-l border-black/10 bg-white/75 backdrop-blur-xl xl:flex xl:h-screen xl:flex-col xl:items-center xl:justify-between xl:px-3 xl:py-6">
            <button
                type="button"
                aria-label="Notificações"
                className="inline-flex h-12 w-12 items-center justify-center rounded-2xl border border-black/10 bg-white text-io-dark shadow-[0_12px_24px_rgba(0,0,0,0.08)] transition hover:border-black/20 hover:bg-black hover:text-white"
            >
                <BellRing className="h-5 w-5" strokeWidth={2} />
            </button>

            <Link
                href="/api/auth/logout"
                aria-label="Sair do sistema"
                className="inline-flex h-12 w-12 items-center justify-center rounded-2xl border border-black/10 bg-white text-io-dark shadow-[0_12px_24px_rgba(0,0,0,0.08)] transition hover:border-red-200 hover:bg-red-500 hover:text-white"
            >
                <LogOut className="h-5 w-5" strokeWidth={2} />
            </Link>
        </aside>
    );
}
