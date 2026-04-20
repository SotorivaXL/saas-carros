import { notFound } from "next/navigation";
import { PublicInventoryCatalogView } from "@/modules/ioauto/components/PublicInventoryCatalog";
import { getPublicInventoryCatalog } from "@/modules/ioauto/publicCatalog.server";

export default async function EstoquePublicoPage({ params }: { params: Promise<{ companyId: string }> }) {
    const { companyId: companySlug } = await params;
    const data = await getPublicInventoryCatalog(companySlug);

    if (!data) {
        notFound();
    }

    return <PublicInventoryCatalogView data={data} />;
}
