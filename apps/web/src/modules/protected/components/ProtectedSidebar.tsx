"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import {
    Cable,
    CarFront,
    ChevronLeft,
    ChevronRight,
    LayoutDashboard,
    Link2,
    MessageSquareText,
    Settings2,
    Users2,
    Workflow,
} from "lucide-react";
import { BrandMark } from "@/modules/ioauto/components/BrandMark";

const SIDEBAR_COLLAPSED_STORAGE_KEY = "ioauto.sidebar.collapsed";

type CurrentUser = {
    fullName?: string | null;
    email?: string | null;
    profileImageUrl?: string | null;
    permissionPreset?: string | null;
    modulePermissions?: string[] | null;
    roles?: string[] | null;
};

type NavItem = {
    label: string;
    href: string;
    icon: "dashboard" | "conversas" | "crm" | "estoque" | "links" | "publicacoes" | "integracoes" | "equipe";
};

function getInitials(fullName?: string | null, email?: string | null) {
    const source = (fullName?.trim() || email?.trim() || "IOAuto").split(/\s+/).filter(Boolean);
    const first = source[0]?.[0] ?? "I";
    const second = source[1]?.[0] ?? "O";
    return `${first}${second}`.toUpperCase();
}

function isActive(pathname: string | null, href: string) {
    if (!pathname) return false;
    return pathname === href || pathname.startsWith(`${href}/`);
}

function hasAdminRole(roles?: string[] | null) {
    return (roles ?? []).some((role) => {
        const normalized = role.toUpperCase();
        return normalized === "ADMIN" || normalized === "SUPERADMIN";
    });
}

function NavIcon({ icon }: { icon: NavItem["icon"] }) {
    if (icon === "dashboard") return <LayoutDashboard className="h-5 w-5" strokeWidth={2} />;
    if (icon === "conversas") return <MessageSquareText className="h-5 w-5" strokeWidth={2} />;
    if (icon === "crm") return <Users2 className="h-5 w-5" strokeWidth={2} />;
    if (icon === "estoque") return <CarFront className="h-5 w-5" strokeWidth={2} />;
    if (icon === "links") return <Link2 className="h-5 w-5" strokeWidth={2} />;
    if (icon === "publicacoes") return <Workflow className="h-5 w-5" strokeWidth={2} />;
    if (icon === "integracoes") return <Cable className="h-5 w-5" strokeWidth={2} />;
    return <Users2 className="h-5 w-5" strokeWidth={2} />;
}

export function ProtectedSidebar({ user }: { user: CurrentUser | null }) {
    const pathname = usePathname();
    const [collapsed, setCollapsed] = useState(false);

    useEffect(() => {
        const storedValue = window.localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY);
        setCollapsed(storedValue === "true");
    }, []);

    useEffect(() => {
        window.localStorage.setItem(SIDEBAR_COLLAPSED_STORAGE_KEY, String(collapsed));
    }, [collapsed]);

    const items: NavItem[] = [
        { label: "Dashboard", href: "/protected/dashboard", icon: "dashboard" },
        { label: "Leads", href: "/protected/conversas", icon: "conversas" },
        { label: "CRM", href: "/protected/crm", icon: "crm" },
        { label: "Estoque", href: "/protected/estoque", icon: "estoque" },
        { label: "Links", href: "/protected/links-publicos", icon: "links" },
        { label: "Publicações", href: "/protected/publicacoes", icon: "publicacoes" },
        { label: "Integrações", href: "/protected/integracoes", icon: "integracoes" },
    ];

    if (hasAdminRole(user?.roles)) {
        items.push({ label: "Equipe", href: "/protected/configuracoes", icon: "equipe" });
    }

    return (
        <aside className={`border-b border-black/10 bg-white/80 backdrop-blur-xl md:h-screen md:border-b-0 md:border-r ${collapsed ? "md:w-[96px]" : "md:w-[304px]"}`}>
            <div className="flex items-center justify-between px-5 py-5">
                <BrandMark href="/protected/dashboard" compact={collapsed} />
                <button
                    type="button"
                    onClick={() => setCollapsed((value) => !value)}
                    className="hidden h-9 w-9 items-center justify-center rounded-full border border-black/10 bg-white text-io-dark transition hover:border-black/20 hover:bg-black hover:text-white md:inline-flex"
                    aria-label={collapsed ? "Expandir menu" : "Recolher menu"}
                >
                    {collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
                </button>
            </div>

            <div className={`relative mx-4 rounded-[28px] border border-black/10 bg-io-dark px-4 py-4 text-white shadow-[0_20px_45px_rgba(0,0,0,0.18)] ${collapsed ? "grid place-items-center" : ""}`}>
                <Link
                    href="/protected/perfil"
                    className="absolute right-3 top-3 inline-flex h-9 w-9 items-center justify-center rounded-full border border-white/15 bg-white/10 text-white/75 transition hover:border-white/30 hover:bg-white/20 hover:text-white"
                    aria-label="Abrir perfil"
                    title="Perfil"
                >
                    <Settings2 className="h-4 w-4" strokeWidth={2} />
                </Link>

                {user?.profileImageUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={user.profileImageUrl} alt={user.fullName ?? "Usuário"} className="h-12 w-12 rounded-2xl object-cover" />
                ) : (
                    <div className="grid h-12 w-12 place-items-center rounded-2xl bg-white text-sm font-bold text-io-dark">
                        {getInitials(user?.fullName, user?.email)}
                    </div>
                )}

                {!collapsed ? (
                    <div className="mt-3 min-w-0">
                        <p className="truncate text-sm font-semibold">{user?.fullName ?? "Operação IOAuto"}</p>
                        <p className="truncate text-xs text-white/60">{user?.email ?? "sem-email@local"}</p>
                    </div>
                ) : null}
            </div>

            <nav className={`mt-5 grid gap-2 px-3 pb-6 ${collapsed ? "justify-items-center" : ""}`}>
                {items.map((item) => {
                    const active = isActive(pathname, item.href);
                    return (
                        <Link
                            key={item.href}
                            href={item.href}
                            className={`group flex items-center rounded-2xl px-3 py-3 text-sm font-medium transition ${
                                active
                                    ? "bg-black text-white shadow-[0_12px_24px_rgba(0,0,0,0.18)]"
                                    : "text-black/65 hover:bg-black/5 hover:text-black"
                            } ${collapsed ? "h-12 w-12 justify-center px-0" : "gap-3"}`}
                            title={item.label}
                        >
                            <NavIcon icon={item.icon} />
                            {!collapsed ? <span>{item.label}</span> : <span className="sr-only">{item.label}</span>}
                        </Link>
                    );
                })}
            </nav>
        </aside>
    );
}
