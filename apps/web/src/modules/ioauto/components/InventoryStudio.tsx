"use client";

import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import {
    CalendarDays,
    CarFront,
    Check,
    Copy,
    Gauge,
    Globe2,
    LoaderCircle,
    MapPin,
    PencilLine,
    Plus,
    Save,
    Search,
    Sparkles,
    X,
} from "lucide-react";
import type { IntegrationRecord, VehiclePublication, VehicleRecord } from "@/modules/ioauto/types";
import { formatDateTime, formatMoney, platformLabel, statusLabel } from "@/modules/ioauto/formatters";

type VehicleFormState = {
    id?: string;
    stockNumber: string;
    title: string;
    brand: string;
    model: string;
    version: string;
    modelYear: string;
    manufactureYear: string;
    priceCents: string;
    mileage: string;
    transmission: string;
    fuelType: string;
    bodyType: string;
    color: string;
    plateFinal: string;
    city: string;
    state: string;
    featured: boolean;
    status: string;
    description: string;
    coverImageUrl: string;
    galleryInput: string;
    optionalsInput: string;
    targetIntegrations: string[];
};

function emptyForm(): VehicleFormState {
    return {
        stockNumber: "",
        title: "",
        brand: "",
        model: "",
        version: "",
        modelYear: "",
        manufactureYear: "",
        priceCents: "",
        mileage: "",
        transmission: "",
        fuelType: "",
        bodyType: "",
        color: "",
        plateFinal: "",
        city: "",
        state: "",
        featured: false,
        status: "DRAFT",
        description: "",
        coverImageUrl: "",
        galleryInput: "",
        optionalsInput: "",
        targetIntegrations: [],
    };
}

function vehicleToForm(vehicle: VehicleRecord): VehicleFormState {
    return {
        id: vehicle.id,
        stockNumber: vehicle.stockNumber ?? "",
        title: vehicle.title,
        brand: vehicle.brand,
        model: vehicle.model,
        version: vehicle.version ?? "",
        modelYear: vehicle.modelYear ? String(vehicle.modelYear) : "",
        manufactureYear: vehicle.manufactureYear ? String(vehicle.manufactureYear) : "",
        priceCents: vehicle.priceCents ? String(vehicle.priceCents) : "",
        mileage: vehicle.mileage ? String(vehicle.mileage) : "",
        transmission: vehicle.transmission ?? "",
        fuelType: vehicle.fuelType ?? "",
        bodyType: vehicle.bodyType ?? "",
        color: vehicle.color ?? "",
        plateFinal: vehicle.plateFinal ?? "",
        city: vehicle.city ?? "",
        state: vehicle.state ?? "",
        featured: vehicle.featured,
        status: vehicle.status,
        description: vehicle.description ?? "",
        coverImageUrl: vehicle.coverImageUrl ?? "",
        galleryInput: vehicle.gallery.join("\n"),
        optionalsInput: vehicle.optionals.join("\n"),
        targetIntegrations: vehicle.publications.map((publication) => publication.providerKey),
    };
}

function parseLineList(value: string) {
    return value
        .split(/\r?\n|,/)
        .map((item) => item.trim())
        .filter(Boolean);
}

function formatMileage(value?: number | null) {
    if (value == null || Number.isNaN(Number(value))) return "Quilometragem não informada";
    return `${new Intl.NumberFormat("pt-BR").format(value)} km`;
}

function formatVehicleYears(vehicle: VehicleRecord) {
    if (vehicle.modelYear && vehicle.manufactureYear) return `${vehicle.manufactureYear}/${vehicle.modelYear}`;
    if (vehicle.modelYear) return String(vehicle.modelYear);
    if (vehicle.manufactureYear) return String(vehicle.manufactureYear);
    return "Ano não informado";
}

function formatVehicleLocation(vehicle: VehicleRecord) {
    const parts = [vehicle.city, vehicle.state].filter(Boolean);
    return parts.length ? parts.join(" / ") : "Localização não informada";
}

function buildVehicleSubtitle(vehicle: VehicleRecord) {
    const parts = [vehicle.version, vehicle.fuelType, vehicle.transmission].filter(Boolean);
    return parts.length ? parts.join(" • ") : "Veículo pronto para publicação multicanal";
}

function getVehicleImage(vehicle: VehicleRecord) {
    const candidates = [vehicle.coverImageUrl, ...vehicle.gallery].filter(Boolean);
    return candidates[0] ?? null;
}

function getPublicationBadgeConfig(publication: VehiclePublication) {
    const normalized = publication.providerKey.trim().toUpperCase();
    if (normalized === "WEBMOTORS") return { shortLabel: "WM", label: "Webmotors", className: "border-transparent bg-[#e52629] text-white" };
    if (normalized === "ICARROS") return { shortLabel: "IC", label: "iCarros", className: "border-transparent bg-[#171717] text-white" };
    if (normalized === "OLX" || normalized === "OLX_AUTOS") return { shortLabel: "OLX", label: "OLX Autos", className: "border-transparent bg-[#f57c00] text-white" };
    if (normalized === "MERCADOLIVRE" || normalized === "MERCADO_LIVRE") {
        return { shortLabel: "ML", label: "Mercado Livre", className: "border-[#d5c228] bg-[#ffe84e] text-[#2f2a05]" };
    }
    return {
        shortLabel: platformLabel(publication.providerName || publication.providerKey).replace(/[^A-Za-z0-9]/g, "").slice(0, 2).toUpperCase(),
        label: platformLabel(publication.providerName || publication.providerKey),
        className: "border-black/10 bg-white text-black/70",
    };
}

export function InventoryStudio() {
    const [vehicles, setVehicles] = useState<VehicleRecord[]>([]);
    const [integrations, setIntegrations] = useState<IntegrationRecord[]>([]);
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [form, setForm] = useState<VehicleFormState>(emptyForm());
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState("");
    const [isEditorOpen, setIsEditorOpen] = useState(false);
    const [publicCatalogLink, setPublicCatalogLink] = useState<string | null>(null);
    const [copiedPublicCatalogLink, setCopiedPublicCatalogLink] = useState(false);

    const selectedVehicle = useMemo(() => vehicles.find((vehicle) => vehicle.id === selectedId) ?? null, [vehicles, selectedId]);
    const connectedIntegrations = useMemo(
        () => integrations.filter((integration) => integration.status === "CONNECTED" || integration.status === "ACTIVE"),
        [integrations]
    );
    const publicationIntegrations = useMemo(() => integrations.filter((integration) => integration.supportsPublication), [integrations]);

    const visibleVehicles = useMemo(() => {
        const query = search.trim().toLowerCase();
        if (!query) return vehicles;
        return vehicles.filter((vehicle) =>
            [
                vehicle.title,
                vehicle.brand,
                vehicle.model,
                vehicle.version,
                vehicle.stockNumber,
                vehicle.city,
                vehicle.state,
                vehicle.plateFinal,
                vehicle.publications.map((publication) => publication.providerName).join(" "),
                vehicle.publications.map((publication) => publication.providerKey).join(" "),
            ]
                .filter(Boolean)
                .join(" ")
                .toLowerCase()
                .includes(query)
        );
    }, [search, vehicles]);

    useEffect(() => {
        void loadInventory();
    }, []);

    useEffect(() => {
        if (!selectedVehicle) {
            setForm(emptyForm());
            return;
        }
        setForm(vehicleToForm(selectedVehicle));
    }, [selectedVehicle]);

    function updateField<K extends keyof VehicleFormState>(key: K, value: VehicleFormState[K]) {
        setForm((current) => ({ ...current, [key]: value }));
    }

    async function loadInventory() {
        setLoading(true);
        try {
            const [vehiclesResponse, integrationsResponse, meResponse] = await Promise.all([
                fetch("/api/ioauto/vehicles", { cache: "no-store" }),
                fetch("/api/ioauto/integrations", { cache: "no-store" }),
                fetch("/api/auth/me", { cache: "no-store" }),
            ]);

            if (!vehiclesResponse.ok) throw new Error("Falha ao listar os veículos.");
            if (!integrationsResponse.ok) throw new Error("Falha ao listar as integrações.");

            const [vehiclePayload, integrationPayload, mePayload] = await Promise.all([
                vehiclesResponse.json(),
                integrationsResponse.json(),
                meResponse.ok ? meResponse.json() as Promise<{ companyId?: string }> : Promise.resolve(null),
            ]);
            const vehicleList = vehiclePayload as VehicleRecord[];
            setVehicles(vehicleList);
            setIntegrations(integrationPayload as IntegrationRecord[]);
            setSelectedId((current) => (current && vehicleList.some((vehicle) => vehicle.id === current) ? current : vehicleList[0]?.id ?? null));
            setPublicCatalogLink(mePayload?.companyId && typeof window !== "undefined" ? `${window.location.origin}/estoque-publico/${mePayload.companyId}` : null);
            setError(null);
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "Falha ao carregar o estoque.");
        } finally {
            setLoading(false);
        }
    }

    async function handleCopyPublicCatalogLink() {
        if (!publicCatalogLink) return;

        try {
            await navigator.clipboard.writeText(publicCatalogLink);
            setCopiedPublicCatalogLink(true);
            window.setTimeout(() => setCopiedPublicCatalogLink(false), 2200);
        } catch {
            setError("Nao foi possivel copiar o link publico do estoque.");
        }
    }

    function openCreateEditor() {
        setSelectedId(null);
        setForm(emptyForm());
        setError(null);
        setIsEditorOpen(true);
    }

    function openEditEditor(vehicle: VehicleRecord) {
        setSelectedId(vehicle.id);
        setForm(vehicleToForm(vehicle));
        setError(null);
        setIsEditorOpen(true);
    }

    function closeEditor() {
        setIsEditorOpen(false);
        setError(null);
        setForm(selectedVehicle ? vehicleToForm(selectedVehicle) : emptyForm());
    }

    async function handleSaveVehicle(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setSaving(true);
        setError(null);

        const payload = {
            stockNumber: form.stockNumber,
            title: form.title,
            brand: form.brand,
            model: form.model,
            version: form.version,
            modelYear: form.modelYear ? Number(form.modelYear) : null,
            manufactureYear: form.manufactureYear ? Number(form.manufactureYear) : null,
            priceCents: form.priceCents ? Number(form.priceCents) : null,
            mileage: form.mileage ? Number(form.mileage) : null,
            transmission: form.transmission,
            fuelType: form.fuelType,
            bodyType: form.bodyType,
            color: form.color,
            plateFinal: form.plateFinal,
            city: form.city,
            state: form.state,
            featured: form.featured,
            status: form.status,
            description: form.description,
            coverImageUrl: form.coverImageUrl,
            gallery: parseLineList(form.galleryInput),
            optionals: parseLineList(form.optionalsInput),
            targetIntegrations: form.targetIntegrations,
        };

        const response = await fetch(form.id ? `/api/ioauto/vehicles/${form.id}` : "/api/ioauto/vehicles", {
            method: form.id ? "PUT" : "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        });

        if (!response.ok) {
            setError("Falha ao salvar o veículo.");
            setSaving(false);
            return;
        }

        const savedVehicle = (await response.json()) as VehicleRecord;
        await loadInventory();
        setSelectedId(savedVehicle.id);
        setSaving(false);
        setIsEditorOpen(false);
    }

    const publishedVehicles = vehicles.filter((vehicle) => vehicle.publications.length > 0).length;
    return (
        <>
            <div className="grid gap-6">
                <section className="overflow-hidden rounded-[34px] border border-black/10 bg-[radial-gradient(circle_at_top_left,_rgba(255,255,255,0.96),_rgba(246,244,240,0.98)_45%,_rgba(239,236,230,0.96)_100%)] p-5 shadow-[0_18px_45px_rgba(0,0,0,0.06)] md:p-6">
                    <div className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
                        <div className="max-w-3xl">
                            <p className="inline-flex items-center gap-2 rounded-full bg-white/90 px-3 py-2 text-xs font-semibold uppercase tracking-[0.22em] text-black/55 shadow-sm">
                                <Sparkles className="h-4 w-4" />
                                Estoque de veículos
                            </p>
                            <h1 className="mt-4 font-display text-3xl font-bold tracking-tight text-io-dark md:text-4xl">
                                Estoque de veículos cadastrados
                            </h1>
                        </div>

                        <div className="flex w-full flex-col gap-3 xl:max-w-[560px] xl:flex-row xl:items-center">
                            <label className="flex h-14 flex-1 items-center gap-3 rounded-full border border-black/10 bg-white px-5 shadow-[0_12px_24px_rgba(15,23,42,0.06)]">
                                <Search className="h-5 w-5 text-black/40" />
                                <input
                                    value={search}
                                    onChange={(event) => setSearch(event.target.value)}
                                    placeholder="Pesquisar por marca, modelo, estoque, cidade ou plataforma"
                                    className="w-full bg-transparent text-sm text-io-dark outline-none placeholder:text-black/35"
                                />
                            </label>

                            <button
                                type="button"
                                onClick={handleCopyPublicCatalogLink}
                                disabled={!publicCatalogLink}
                                className="inline-flex h-14 items-center justify-center gap-2 rounded-full border border-black/12 bg-white px-5 text-sm font-semibold text-black/70 transition hover:border-black/20 hover:text-black disabled:cursor-not-allowed disabled:opacity-55"
                            >
                                {copiedPublicCatalogLink ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
                                {copiedPublicCatalogLink ? "Link copiado" : "Copiar link publico do estoque"}
                            </button>

                            <button
                                type="button"
                                onClick={openCreateEditor}
                                className="inline-flex h-14 items-center justify-center gap-2 rounded-full bg-black px-5 text-sm font-semibold text-white transition hover:bg-black/85"
                            >
                                <Plus className="h-4 w-4" />
                                Novo veículo
                            </button>
                        </div>
                    </div>

                    <div className="mt-5 grid gap-3 md:grid-cols-2">
                        <MetricCard label="Veículos cadastrados" value={String(vehicles.length)} detail={`${visibleVehicles.length} visíveis na busca`} />
                        <MetricCard label="Com publicação ativa" value={String(publishedVehicles)} detail={`${connectedIntegrations.length} plataformas conectadas`} />
                    </div>
                </section>

                {error ? <p className="rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p> : null}

                {loading ? (
                    <section className="flex min-h-[280px] items-center justify-center rounded-[34px] border border-black/10 bg-white shadow-[0_18px_45px_rgba(0,0,0,0.06)]">
                        <div className="flex items-center gap-3 text-black/45">
                            <LoaderCircle className="h-5 w-5 animate-spin" />
                            <span className="text-sm font-medium">Carregando o estoque...</span>
                        </div>
                    </section>
                ) : visibleVehicles.length ? (
                    <section className="grid gap-5 md:grid-cols-2 2xl:grid-cols-4 xl:grid-cols-3">
                        {visibleVehicles.map((vehicle) => (
                            <InventoryVehicleCard key={vehicle.id} vehicle={vehicle} onEdit={() => openEditEditor(vehicle)} />
                        ))}
                    </section>
                ) : (
                    <section className="rounded-[34px] border border-dashed border-black/12 bg-white px-6 py-12 text-center shadow-[0_18px_45px_rgba(0,0,0,0.04)]">
                        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-black/[0.04]">
                            <Search className="h-6 w-6 text-black/45" />
                        </div>
                        <h2 className="mt-4 font-display text-2xl font-bold text-io-dark">Nenhum veículo encontrado</h2>
                        <p className="mt-2 text-sm text-black/52">Ajuste a pesquisa ou cadastre um novo veículo para preencher essa vitrine.</p>
                    </section>
                )}
            </div>

            {isEditorOpen ? (
                <div className="fixed inset-0 z-50 bg-black/55 px-4 py-6 backdrop-blur-sm">
                    <div className="mx-auto flex h-full max-w-6xl items-start justify-center">
                        <div className="flex max-h-full w-full flex-col overflow-hidden rounded-[34px] border border-white/15 bg-white shadow-[0_24px_80px_rgba(0,0,0,0.28)]">
                            <div className="flex items-center justify-between gap-4 border-b border-black/8 px-6 py-5 md:px-8">
                                <div>
                                    <p className="text-xs font-semibold uppercase tracking-[0.22em] text-black/40">Cadastro do veículo</p>
                                    <h2 className="mt-1 font-display text-2xl font-bold text-io-dark">{form.id ? "Editar veículo" : "Novo veículo"}</h2>
                                    <p className="mt-1 text-sm text-black/55">Configure dados comerciais, mídia e distribuição para as plataformas conectadas.</p>
                                </div>

                                <button
                                    type="button"
                                    onClick={closeEditor}
                                    className="inline-flex h-11 w-11 items-center justify-center rounded-full border border-black/10 text-black/65 transition hover:border-black/20 hover:text-black"
                                    aria-label="Fechar editor"
                                >
                                    <X className="h-5 w-5" />
                                </button>
                            </div>

                            <form onSubmit={handleSaveVehicle} className="flex min-h-0 flex-1 flex-col">
                                {error ? <p className="mx-6 mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-700 md:mx-8">{error}</p> : null}
                                <div className="min-h-0 flex-1 overflow-y-auto px-6 py-6 md:px-8">
                                    <div className="grid gap-6">
                                        <div className="grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
                                            <section className="rounded-[30px] border border-black/10 bg-[#faf8f4] p-5">
                                                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                                                    <Field label="Título" value={form.title} onChange={(value) => updateField("title", value)} required />
                                                    <Field label="Marca" value={form.brand} onChange={(value) => updateField("brand", value)} required />
                                                    <Field label="Modelo" value={form.model} onChange={(value) => updateField("model", value)} required />
                                                    <Field label="Versão" value={form.version} onChange={(value) => updateField("version", value)} />
                                                    <Field label="Ano modelo" value={form.modelYear} onChange={(value) => updateField("modelYear", value)} />
                                                    <Field label="Ano fabricação" value={form.manufactureYear} onChange={(value) => updateField("manufactureYear", value)} />
                                                    <Field label="Preço (centavos)" value={form.priceCents} onChange={(value) => updateField("priceCents", value)} />
                                                    <Field label="Quilometragem" value={form.mileage} onChange={(value) => updateField("mileage", value)} />
                                                    <Field label="Número de estoque" value={form.stockNumber} onChange={(value) => updateField("stockNumber", value)} />
                                                    <Field label="Transmissão" value={form.transmission} onChange={(value) => updateField("transmission", value)} />
                                                    <Field label="Combustível" value={form.fuelType} onChange={(value) => updateField("fuelType", value)} />
                                                    <Field label="Carroceria" value={form.bodyType} onChange={(value) => updateField("bodyType", value)} />
                                                    <Field label="Cor" value={form.color} onChange={(value) => updateField("color", value)} />
                                                    <Field label="Final da placa" value={form.plateFinal} onChange={(value) => updateField("plateFinal", value)} />
                                                    <Field label="Cidade" value={form.city} onChange={(value) => updateField("city", value)} />
                                                    <Field label="UF" value={form.state} onChange={(value) => updateField("state", value)} />
                                                </div>
                                            </section>

                                            <section className="grid gap-4">
                                                <div className="rounded-[30px] border border-black/10 bg-black p-5 text-white">
                                                    <div className="mt-4 grid gap-3">
                                                        <label className="flex items-center gap-3 rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white/82">
                                                            <input type="checkbox" checked={form.featured} onChange={(event) => updateField("featured", event.target.checked)} className="h-4 w-4" />
                                                            Destacar na vitrine
                                                        </label>

                                                        <label className="grid gap-2 rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white/82">
                                                            <span>Status do veículo</span>
                                                            <select value={form.status} onChange={(event) => updateField("status", event.target.value)} className="rounded-xl border border-white/10 bg-white/10 px-3 py-2 text-sm text-white outline-none">
                                                                <option value="DRAFT" className="text-black">Rascunho</option>
                                                                <option value="READY" className="text-black">Pronto</option>
                                                                <option value="PUBLISHED" className="text-black">Publicado</option>
                                                                <option value="ARCHIVED" className="text-black">Arquivado</option>
                                                            </select>
                                                        </label>
                                                    </div>
                                                </div>

                                                <div className="rounded-[30px] border border-black/10 bg-white p-5">
                                                    <p className="text-sm font-semibold text-io-dark">Distribuição</p>
                                                    <div className="mt-4 grid gap-3">
                                                        {publicationIntegrations.length ? (
                                                            publicationIntegrations.map((integration) => {
                                                                const selected = form.targetIntegrations.includes(integration.providerKey);
                                                                return (
                                                                    <label key={integration.providerKey} className={`rounded-2xl border px-4 py-3 text-sm transition ${selected ? "border-black bg-black text-white" : "border-black/10 bg-[#f7f7f7] text-black/70 hover:border-black/20"}`}>
                                                                        <div className="flex items-center gap-3">
                                                                            <input
                                                                                type="checkbox"
                                                                                checked={selected}
                                                                                onChange={(event) => {
                                                                                    const next = event.target.checked ? [...form.targetIntegrations, integration.providerKey] : form.targetIntegrations.filter((item) => item !== integration.providerKey);
                                                                                    updateField("targetIntegrations", Array.from(new Set(next)));
                                                                                }}
                                                                                className="h-4 w-4"
                                                                            />
                                                                            <div>
                                                                                <p className="font-medium">{integration.displayName}</p>
                                                                                <p className={`text-[11px] ${selected ? "text-white/55" : "text-black/48"}`}>{statusLabel(integration.status)}</p>
                                                                            </div>
                                                                        </div>
                                                                    </label>
                                                                );
                                                            })
                                                        ) : (
                                                            <p className="rounded-2xl bg-[#f7f7f7] px-4 py-4 text-sm text-black/55">Nenhuma integração de publicação disponível no momento.</p>
                                                        )}
                                                    </div>
                                                </div>
                                            </section>
                                        </div>

                                        <div className="grid gap-4 lg:grid-cols-2">
                                            <section className="rounded-[30px] border border-black/10 bg-white p-5">
                                                <div className="grid gap-4">
                                                    <Field label="URL da capa" value={form.coverImageUrl} onChange={(value) => updateField("coverImageUrl", value)} />
                                                    <TextArea label="Descrição do anúncio" value={form.description} onChange={(value) => updateField("description", value)} />
                                                </div>
                                            </section>

                                            <section className="rounded-[30px] border border-black/10 bg-white p-5">
                                                <div className="grid gap-4">
                                                    <TextArea label="Galeria (1 URL por linha)" value={form.galleryInput} onChange={(value) => updateField("galleryInput", value)} />
                                                    <TextArea label="Opcionais (1 item por linha)" value={form.optionalsInput} onChange={(value) => updateField("optionalsInput", value)} />
                                                </div>
                                            </section>
                                        </div>
                                    </div>
                                </div>

                                <div className="flex flex-wrap items-center justify-between gap-3 border-t border-black/8 px-6 py-5 md:px-8">
                                    <div className="text-xs text-black/45">{connectedIntegrations.length} integrações conectadas prontas para sincronização.</div>
                                    <div className="flex items-center gap-3">
                                        <button type="button" onClick={closeEditor} className="inline-flex h-12 items-center justify-center rounded-full border border-black/12 px-5 text-sm font-semibold text-black/68 transition hover:border-black/20 hover:text-black">
                                            Cancelar
                                        </button>
                                        <button type="submit" disabled={saving} className="inline-flex h-12 items-center justify-center gap-2 rounded-full bg-black px-5 text-sm font-semibold text-white transition hover:bg-black/85 disabled:cursor-not-allowed disabled:bg-black/30">
                                            {saving ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                                            Salvar cadastro
                                        </button>
                                    </div>
                                </div>
                            </form>
                        </div>
                    </div>
                </div>
            ) : null}
        </>
    );
}

function InventoryVehicleCard({ vehicle, onEdit }: { vehicle: VehicleRecord; onEdit: () => void }) {
    const imageUrl = getVehicleImage(vehicle);

    return (
        <article className="group overflow-hidden rounded-[30px] border border-black/10 bg-white p-3 shadow-[0_18px_45px_rgba(15,23,42,0.06)] transition hover:-translate-y-1 hover:shadow-[0_24px_60px_rgba(15,23,42,0.12)]">
            <div className="relative overflow-hidden rounded-[24px] bg-[#f1eee8]">
                {imageUrl ? (
                    <img src={imageUrl} alt={vehicle.title} className="h-60 w-full object-cover transition duration-500 group-hover:scale-[1.03]" />
                ) : (
                    <div className="flex h-60 w-full items-center justify-center bg-[linear-gradient(135deg,_rgba(17,17,17,0.96),_rgba(64,64,64,0.9))] text-white">
                        <div className="text-center">
                            <CarFront className="mx-auto h-10 w-10 text-white/75" />
                            <p className="mt-3 text-sm text-white/65">Sem imagem principal</p>
                        </div>
                    </div>
                )}

                <div className="absolute inset-x-0 top-0 flex items-start justify-between gap-3 p-3">
                    <span className={`rounded-full px-3 py-2 text-[11px] font-semibold shadow-sm ${vehicle.featured ? "bg-[#f9425f] text-white" : "bg-white text-black/70"}`}>
                        {vehicle.featured ? "Patrocinado" : "Em estoque"}
                    </span>
                </div>
            </div>

            <div className="px-2 pb-2 pt-4">
                <div className="flex items-start justify-between gap-3">
                    <div>
                        <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-black/36">{vehicle.stockNumber ? `Estoque ${vehicle.stockNumber}` : "Cadastro unificado"}</p>
                        <h3 className="mt-2 font-display text-[1.65rem] font-bold uppercase leading-[1.12] tracking-tight text-io-dark">{vehicle.title}</h3>
                        <p className="mt-2 text-sm leading-6 text-black/58">{buildVehicleSubtitle(vehicle)}</p>
                    </div>
                    <span className="rounded-full bg-black/[0.04] px-3 py-2 text-[11px] font-semibold text-black/55">{statusLabel(vehicle.status)}</span>
                </div>

                <div className="mt-4 flex flex-wrap gap-3 text-sm text-black/56">
                    <MetaItem icon={<CalendarDays className="h-4 w-4" />} text={formatVehicleYears(vehicle)} />
                    <MetaItem icon={<Gauge className="h-4 w-4" />} text={formatMileage(vehicle.mileage)} />
                    <MetaItem icon={<MapPin className="h-4 w-4" />} text={formatVehicleLocation(vehicle)} />
                </div>

                <div className="mt-5 rounded-[24px] bg-[#faf8f4] px-4 py-3">
                    <div>
                        <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-black/36">Plataformas</p>
                        <div className="mt-2 flex flex-wrap gap-2">
                            {vehicle.publications.length ? (
                                vehicle.publications.map((publication) => <PublicationBadge key={publication.id} publication={publication} size="sm" />)
                            ) : (
                                <span className="inline-flex items-center rounded-full bg-white px-3 py-2 text-xs text-black/48">Nenhuma ativa</span>
                            )}
                        </div>
                    </div>
                </div>

                <div className="mt-5 flex items-end justify-between gap-3">
                    <div>
                        <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-black/36">Preço</p>
                        <p className="mt-1 text-3xl font-bold tracking-tight text-io-dark">{formatMoney(vehicle.priceCents)}</p>
                        <p className="mt-1 text-xs text-black/45">Atualizado em {formatDateTime(vehicle.updatedAt)}</p>
                    </div>

                    <button type="button" onClick={onEdit} className="inline-flex h-12 items-center justify-center gap-2 rounded-full bg-[#202028] px-5 text-sm font-semibold text-white transition hover:bg-[#111111]">
                        <PencilLine className="h-4 w-4" />
                        Editar cadastro
                    </button>
                </div>
            </div>
        </article>
    );
}

function MetricCard({ label, value, detail }: { label: string; value: string; detail: string }) {
    return (
        <div className="rounded-[28px] border border-black/8 bg-white/92 px-5 py-4 shadow-[0_12px_24px_rgba(15,23,42,0.05)]">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-black/35">{label}</p>
            <div className="mt-2 flex items-center justify-between gap-3">
                <p className="text-3xl font-bold tracking-tight text-io-dark">{value}</p>
                <div className="flex h-11 w-11 items-center justify-center rounded-full bg-[#faf8f4] text-black/45">
                    <Globe2 className="h-5 w-5" />
                </div>
            </div>
            <p className="mt-2 text-sm text-black/52">{detail}</p>
        </div>
    );
}

function PublicationBadge({ publication, size = "md" }: { publication: VehiclePublication; size?: "sm" | "md" }) {
    const config = getPublicationBadgeConfig(publication);
    const sizeClassName = size === "sm" ? "h-8 min-w-8 px-2.5 text-[10px]" : "h-10 min-w-10 px-3 text-[11px]";
    return (
        <span
            title={config.label}
            className={`inline-flex items-center justify-center rounded-full border font-semibold ${config.className} ${sizeClassName}`}
        >
            {config.shortLabel}
        </span>
    );
}

function MetaItem({ icon, text }: { icon: ReactNode; text: string }) {
    return (
        <span className="inline-flex items-center gap-2 rounded-full bg-black/[0.04] px-3 py-2">
            <span className="text-black/42">{icon}</span>
            <span>{text}</span>
        </span>
    );
}

function Field({ label, value, onChange, required = false }: { label: string; value: string; onChange: (value: string) => void; required?: boolean }) {
    return (
        <label className="grid gap-2">
            <span className="text-sm font-medium text-black/60">{label}</span>
            <input
                value={value}
                onChange={(event) => onChange(event.target.value)}
                required={required}
                className="h-12 rounded-2xl border border-black/10 bg-[#f7f7f7] px-4 text-sm text-io-dark outline-none transition focus:border-black/30 focus:bg-white"
            />
        </label>
    );
}

function TextArea({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
    return (
        <label className="grid gap-2">
            <span className="text-sm font-medium text-black/60">{label}</span>
            <textarea
                value={value}
                onChange={(event) => onChange(event.target.value)}
                rows={5}
                className="min-h-40 rounded-[24px] border border-black/10 bg-[#f7f7f7] px-4 py-4 text-sm text-io-dark outline-none transition focus:border-black/30 focus:bg-white"
            />
        </label>
    );
}
