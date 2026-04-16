import { redirect } from "next/navigation";
import { apiFetch } from "@/core/http/serverFetch";

type CurrentUser = {
    permissionPreset?: string | null;
    modulePermissions?: string[] | null;
    roles?: string[] | null;
};

function hasAdminRole(roles?: string[] | null) {
    if (!roles?.length) return false;
    const normalized = roles.map((role) => role.toUpperCase());
    return normalized.some((role) => role === "ADMIN" || role === "SUPERADMIN");
}

function hasModule(user: CurrentUser | null, moduleKey: string) {
    const modules = user?.modulePermissions ?? [];
    return modules.some((module) => module === moduleKey);
}

function canManageSupervisors(user: CurrentUser | null) {
    if (!user) return false;
    const preset = String(user.permissionPreset ?? "").toLowerCase();
    if (preset === "admin") return true;
    if (preset === "custom" && hasModule(user, "manageCollaborators")) return true;
    return hasAdminRole(user.roles);
}

export async function ensureAiSupervisorAccess() {
    const res = await apiFetch("/me");
    if (!res.ok) redirect("/protected/dashboard");

    const user = (await res.json().catch(() => null)) as CurrentUser | null;
    if (!canManageSupervisors(user)) redirect("/protected/dashboard");
}
