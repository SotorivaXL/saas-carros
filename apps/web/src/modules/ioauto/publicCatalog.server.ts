import { getServerApiBase } from "@/core/http/getServerApiBase";
import { fetchUpstream, readJsonSafely } from "@/core/http/upstream";
import type { PublicInventoryCatalog, PublicVehicleDetail } from "@/modules/ioauto/types";

type ApiError = {
    message?: string;
};

async function fetchPublicResource<T>(path: string) {
    const apiBase = getServerApiBase();
    const response = await fetchUpstream(`${apiBase}${path}`, { cache: "no-store" });
    const payload = await readJsonSafely<T | ApiError>(response);

    if (response.status === 404) {
        return null;
    }

    if (!response.ok) {
        throw new Error((payload as ApiError | null)?.message ?? "Falha ao carregar o catalogo publico.");
    }

    return payload as T;
}

export async function getPublicInventoryCatalog(companyId: string) {
    return fetchPublicResource<PublicInventoryCatalog>(`/public/stock/${companyId}`);
}

export async function getPublicVehicleDetail(companyId: string, vehicleId: string) {
    return fetchPublicResource<PublicVehicleDetail>(`/public/stock/${companyId}/vehicles/${vehicleId}`);
}
