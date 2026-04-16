import { cookies } from "next/headers";
import { redirect, unstable_rethrow } from "next/navigation";
import { ACCESS_COOKIE } from "@/core/auth/cookies";
import { getServerApiBase } from "@/core/http/getServerApiBase";
import { fetchUpstream } from "@/core/http/upstream";
import { AuthSessionWatcher } from "@/modules/auth/components/AuthSessionWatcher";
import { ProtectedSidebar } from "@/modules/protected/components/ProtectedSidebar";
import { ProtectedNotificationsRail } from "@/modules/protected/components/ProtectedNotificationsRail";

type MeResponse = {
    userId: string;
    companyId: string;
    email: string;
    fullName: string;
    profileImageUrl?: string | null;
    permissionPreset?: string | null;
    modulePermissions?: string[] | null;
    roles: string[];
};

async function getCurrentUser() {
    try {
        const token = (await cookies()).get(ACCESS_COOKIE)?.value;
        if (!token) return null;

        const apiBase = getServerApiBase();

        const res = await fetchUpstream(`${apiBase}/me`, {
            headers: { Authorization: `Bearer ${token}` },
            cache: "no-store",
        });

        if (res.status === 401) return "unauthenticated" as const;
        if (!res.ok) return null;

        const data = (await res.json()) as MeResponse;
        return data;
    } catch (error) {
        unstable_rethrow(error);
        console.error("[protected/layout] Unable to load the current user from the backend.", error);
        return null;
    }
}

export default async function ProtectedLayout({ children }: { children: React.ReactNode }) {
    const me = await getCurrentUser();

    if (me === "unauthenticated") {
        redirect("/api/auth/logout");
    }

    return (
        <div className="min-h-screen bg-[#f3f3f3] md:h-screen md:overflow-hidden">
            <AuthSessionWatcher />
            <div className="pointer-events-none fixed inset-0 overflow-hidden">
                <div className="absolute -left-24 -top-24 h-80 w-80 rounded-full bg-black/10 blur-3xl" />
                <div className="absolute bottom-0 right-0 h-96 w-96 rounded-full bg-white blur-3xl" />
            </div>

            <div className="relative flex min-h-screen flex-col md:h-screen md:min-h-0 md:flex-row md:overflow-hidden">
                <ProtectedSidebar user={me} />
                <main className="min-h-0 min-w-0 flex-1 p-4 md:h-screen md:overflow-y-auto md:p-6">{children}</main>
                <ProtectedNotificationsRail />
            </div>
        </div>
    );
}
