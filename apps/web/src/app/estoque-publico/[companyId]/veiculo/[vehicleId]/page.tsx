import { notFound } from "next/navigation";
import { PublicVehicleDetailView } from "@/modules/ioauto/components/PublicVehicleDetail";
import { getPublicVehicleDetail } from "@/modules/ioauto/publicCatalog.server";

export default async function EstoquePublicoVeiculoPage({
    params,
}: {
    params: Promise<{ companyId: string; vehicleId: string }>;
}) {
    const { companyId, vehicleId } = await params;
    const data = await getPublicVehicleDetail(companyId, vehicleId);

    if (!data) {
        notFound();
    }

    return <PublicVehicleDetailView data={data} />;
}
