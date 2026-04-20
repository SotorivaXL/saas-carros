import { notFound } from "next/navigation";
import { PublicInventoryCatalogView } from "@/modules/ioauto/components/PublicInventoryCatalog";
import { getPublicInventoryCatalog } from "@/modules/ioauto/publicCatalog.server";

export default async function EstoquePublicoPage({ params }: { params: Promise<{ companyId: string }> }) {
    const { companyId } = await params;
    const data = await getPublicInventoryCatalog(companyId);

    if (!data) {
        notFound();
    }

    return <PublicInventoryCatalogView data={data} />;
}
