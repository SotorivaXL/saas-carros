"use client";

import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import {
    Check,
    Copy,
    Globe2,
    Link2,
    LoaderCircle,
    Megaphone,
    Plus,
    Search,
    Trash2,
    X,
} from "lucide-react";
import type { PublicLinkRecord, VehicleRecord } from "@/modules/ioauto/types";
import { formatDateTime } from "@/modules/ioauto/formatters";

type MePayload = {
    companyId?: string;
    companyName?: string | null;
};

type LinkFormState = {
    name: string;
    linkKind: "PUBLIC" | "CAMPAIGN";
    sourceType: "INFLUENCER" | "CAMPAIGN";
    scopeType: "CATALOG" | "VEHICLE";
    vehicleId: string;
    sourceReference: string;
};

function emptyForm(): LinkFormState {
    return {
        name: "",
        linkKind: "PUBLIC",
        sourceType: "INFLUENCER",
        scopeType: "CATALOG",
        vehicleId: "",
        sourceReference: "",
    };
}

function slugifyReference(value: string) {
    const normalized = value
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/^-+|-+$/g, "");

    return normalized.slice(0, 80);
}

function slugifyCompanyName(value: string) {
    return slugifyReference(value) || "catalogo";
}

function linkKindLabel(value: string) {
    if (value === "PUBLIC") return "Público";
    return "Campanha";
}

function scopeLabel(value: string) {
    if (value === "VEHICLE") return "Veículo específico";
    return "Estoque completo";
}

export function PublicLinksManager() {
    const [links, setLinks] = useState<PublicLinkRecord[]>([]);
    const [vehicles, setVehicles] = useState<VehicleRecord[]>([]);
    const [companyName, setCompanyName] = useState("Catálogo");
    const [origin, setOrigin] = useState("");
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [search, setSearch] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [isCreateOpen, setIsCreateOpen] = useState(false);
    const [copiedLinkId, setCopiedLinkId] = useState<string | null>(null);
    const [form, setForm] = useState<LinkFormState>(emptyForm());

    const filteredLinks = useMemo(() => {
        const query = search.trim().toLowerCase();
        if (!query) return links;

        return links.filter((link) =>
            [
                link.name,
                link.vehicleTitle,
                link.sourceReference,
                link.sourceType,
                link.linkKind,
                link.scopeType,
            ]
                .filter(Boolean)
                .join(" ")
                .toLowerCase()
                .includes(query)
        );
    }, [links, search]);

    const publicLinks = useMemo(() => filteredLinks.filter((link) => link.linkKind === "PUBLIC"), [filteredLinks]);
    const campaignLinks = useMemo(() => filteredLinks.filter((link) => link.linkKind === "CAMPAIGN"), [filteredLinks]);
    const previewPath = useMemo(() => {
        const basePath =
            form.scopeType === "VEHICLE" && form.vehicleId
                ? `/estoque-publico/${slugifyCompanyName(companyName)}/veiculo/${form.vehicleId}`
                : `/estoque-publico/${slugifyCompanyName(companyName)}`;

        if (form.linkKind === "PUBLIC") {
            return basePath;
        }

        const reference = slugifyReference(form.sourceReference);
        if (!reference) return basePath;
        return `${basePath}?source=${form.sourceType.toLowerCase()}&ref=${reference}`;
    }, [companyName, form.linkKind, form.scopeType, form.sourceReference, form.sourceType, form.vehicleId]);

    useEffect(() => {
        setOrigin(window.location.origin);
        void loadData();
    }, []);

    useEffect(() => {
        if (form.linkKind !== "PUBLIC") return;
        if (form.scopeType === "CATALOG" && !form.vehicleId) return;

        setForm((current) => ({
            ...current,
            scopeType: "CATALOG",
            vehicleId: "",
        }));
    }, [form.linkKind, form.scopeType, form.vehicleId]);

    async function loadData() {
        setLoading(true);
        try {
            const [linksResponse, vehiclesResponse, meResponse] = await Promise.all([
                fetch("/api/ioauto/public-links", { cache: "no-store" }),
                fetch("/api/ioauto/vehicles", { cache: "no-store" }),
                fetch("/api/auth/me", { cache: "no-store" }),
            ]);

            if (!linksResponse.ok) throw new Error("Falha ao carregar os links públicos.");
            if (!vehiclesResponse.ok) throw new Error("Falha ao carregar os veículos.");

            const [linksPayload, vehiclesPayload, mePayload] = await Promise.all([
                linksResponse.json() as Promise<PublicLinkRecord[]>,
                vehiclesResponse.json() as Promise<VehicleRecord[]>,
                meResponse.ok ? meResponse.json() as Promise<MePayload> : Promise.resolve(null),
            ]);

            setLinks(linksPayload);
            setVehicles(vehiclesPayload);
            setCompanyName(mePayload?.companyName?.trim() || "Catálogo");
            setError(null);
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "Falha ao carregar os links.");
        } finally {
            setLoading(false);
        }
    }

    function updateForm<K extends keyof LinkFormState>(key: K, value: LinkFormState[K]) {
        setForm((current) => ({ ...current, [key]: value }));
    }

    function openCreateModal() {
        setForm({
            name: "",
            linkKind: "PUBLIC",
            sourceType: "INFLUENCER",
            scopeType: "CATALOG",
            vehicleId: "",
            sourceReference: "",
        });
        setError(null);
        setIsCreateOpen(true);
    }

    function closeCreateModal() {
        setIsCreateOpen(false);
        setForm(emptyForm());
    }

    async function handleCreateLink(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setSaving(true);
        setError(null);

        const payload = {
            name: form.name,
            linkKind: form.linkKind,
            scopeType: form.scopeType,
            vehicleId: form.scopeType === "VEHICLE" ? form.vehicleId || null : null,
            sourceType: form.linkKind === "CAMPAIGN" ? form.sourceType : null,
            sourceReference: form.linkKind === "CAMPAIGN" ? slugifyReference(form.sourceReference) : null,
        };

        const response = await fetch("/api/ioauto/public-links", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        });

        if (!response.ok) {
            const data = (await response.json().catch(() => null)) as { message?: string } | null;
            setError(data?.message ?? "Falha ao criar o link.");
            setSaving(false);
            return;
        }

        await loadData();
        setSaving(false);
        closeCreateModal();
    }

    async function handleDeleteLink(link: PublicLinkRecord) {
        const confirmed = window.confirm(`Deseja remover o link "${link.name}"?`);
        if (!confirmed) return;

        const response = await fetch(`/api/ioauto/public-links/${link.id}`, {
            method: "DELETE",
        });

        if (!response.ok) {
            const data = (await response.json().catch(() => null)) as { message?: string } | null;
            setError(data?.message ?? "Falha ao remover o link.");
            return;
        }

        await loadData();
    }

    async function handleCopyLink(link: PublicLinkRecord) {
        try {
            await navigator.clipboard.writeText(`${origin}${link.publicPath}`);
            setCopiedLinkId(link.id);
            window.setTimeout(() => setCopiedLinkId(null), 2200);
        } catch {
            setError("Não foi possível copiar o link.");
        }
    }

    return (
        <>
            <div className="grid gap-6">
                <section className="overflow-hidden rounded-[34px] border border-black/10 bg-[radial-gradient(circle_at_top_left,_rgba(255,255,255,0.96),_rgba(246,244,240,0.98)_45%,_rgba(239,236,230,0.96)_100%)] p-5 shadow-[0_18px_45px_rgba(0,0,0,0.06)] md:p-6">
                    <div className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
                        <div className="max-w-3xl">
                            <p className="inline-flex items-center gap-2 rounded-full bg-white/90 px-3 py-2 text-xs font-semibold uppercase tracking-[0.22em] text-black/55 shadow-sm">
                                <Megaphone className="h-4 w-4" />
                                Links públicos e campanhas
                            </p>
                            <h1 className="mt-4 font-display text-3xl font-bold tracking-tight text-io-dark md:text-4xl">
                                Gerenciamento de links
                            </h1>
                            <p className="mt-2 text-sm text-black/56">
                                Centralize os links públicos do estoque e as campanhas com influenciadores ou divulgadores em um único lugar.
                            </p>
                        </div>

                        <div className="flex w-full flex-col gap-3 xl:max-w-[720px] xl:flex-row xl:items-center xl:justify-end">
                            <label className="flex h-14 flex-1 items-center gap-3 rounded-full border border-black/10 bg-white px-5 shadow-[0_12px_24px_rgba(15,23,42,0.06)]">
                                <Search className="h-5 w-5 text-black/40" />
                                <input
                                    value={search}
                                    onChange={(event) => setSearch(event.target.value)}
                                    placeholder="Pesquisar por nome, origem ou veículo"
                                    className="w-full bg-transparent text-sm text-io-dark outline-none placeholder:text-black/35"
                                />
                            </label>

                            <button
                                type="button"
                                onClick={openCreateModal}
                                className="inline-flex h-14 items-center justify-center gap-2 rounded-full bg-black px-5 text-sm font-semibold text-white transition hover:bg-black/85"
                            >
                                <Plus className="h-4 w-4" />
                                Novo link
                            </button>
                        </div>
                    </div>
                </section>

                {error ? <p className="rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p> : null}

                {loading ? (
                    <section className="flex min-h-[280px] items-center justify-center rounded-[34px] border border-black/10 bg-white shadow-[0_18px_45px_rgba(0,0,0,0.06)]">
                        <div className="flex items-center gap-3 text-black/45">
                            <LoaderCircle className="h-5 w-5 animate-spin" />
                            <span className="text-sm font-medium">Carregando os links...</span>
                        </div>
                    </section>
                ) : (
                    <div className="grid gap-6">
                        <LinkSection
                            title="Links públicos do estoque"
                            description="Links limpos para divulgar a vitrine pública sem origem de campanha."
                            icon={<Globe2 className="h-4 w-4" />}
                            links={publicLinks}
                            copiedLinkId={copiedLinkId}
                            onCopyLink={handleCopyLink}
                            onDeleteLink={handleDeleteLink}
                        />

                        <LinkSection
                            title="Links de influenciadores e campanhas"
                            description="Links rastreáveis para medir qual parceiro ou campanha trouxe mais interações."
                            icon={<Megaphone className="h-4 w-4" />}
                            links={campaignLinks}
                            copiedLinkId={copiedLinkId}
                            onCopyLink={handleCopyLink}
                            onDeleteLink={handleDeleteLink}
                        />
                    </div>
                )}
            </div>

            {isCreateOpen ? (
                <div className="fixed inset-0 z-50 bg-black/55 px-4 py-6 backdrop-blur-sm">
                    <div className="mx-auto flex h-full max-w-3xl items-center justify-center">
                        <div className="w-full rounded-[34px] border border-white/15 bg-white p-6 shadow-[0_24px_80px_rgba(0,0,0,0.28)] md:p-7">
                            <div className="flex items-start justify-between gap-4">
                                <div>
                                    <p className="text-xs font-semibold uppercase tracking-[0.22em] text-black/38">Novo link</p>
                                    <h2 className="mt-2 font-display text-2xl font-bold text-io-dark">Criar link</h2>
                                    <p className="mt-2 text-sm text-black/56">
                                        Escolha se o link será público ou de campanha e defina se ele aponta para o estoque inteiro ou para um veículo específico.
                                    </p>
                                </div>

                                <button
                                    type="button"
                                    onClick={closeCreateModal}
                                    className="inline-flex h-11 w-11 items-center justify-center rounded-full border border-black/10 text-black/65 transition hover:border-black/20 hover:text-black"
                                    aria-label="Fechar modal"
                                >
                                    <X className="h-5 w-5" />
                                </button>
                            </div>

                            <form onSubmit={handleCreateLink} className="mt-6 grid gap-4">
                                <div className="grid gap-4 md:grid-cols-2">
                                    <Field label="Nome do link" value={form.name} onChange={(value) => updateForm("name", value)} required />

                                    <SelectField
                                        label="Tipo"
                                        value={form.linkKind}
                                        onChange={(value) => updateForm("linkKind", value as LinkFormState["linkKind"])}
                                        options={[
                                            { value: "PUBLIC", label: "Público" },
                                            { value: "CAMPAIGN", label: "Influenciador / campanha" },
                                        ]}
                                    />
                                </div>

                                {form.linkKind === "CAMPAIGN" ? (
                                    <div className="grid gap-4 md:grid-cols-2">
                                        <SelectField
                                            label="Origem"
                                            value={form.sourceType}
                                            onChange={(value) => updateForm("sourceType", value as LinkFormState["sourceType"])}
                                            options={[
                                                { value: "INFLUENCER", label: "Influenciador" },
                                                { value: "CAMPAIGN", label: "Campanha" },
                                            ]}
                                        />
                                        <Field
                                            label="Identificador"
                                            value={form.sourceReference}
                                            onChange={(value) => updateForm("sourceReference", value)}
                                            placeholder="Ex.: joao-da-radio"
                                            required
                                        />
                                    </div>
                                ) : null}

                                {form.linkKind === "CAMPAIGN" ? (
                                    <div className="grid gap-4 md:grid-cols-2">
                                        <SelectField
                                            label="Destino"
                                            value={form.scopeType}
                                            onChange={(value) => updateForm("scopeType", value as LinkFormState["scopeType"])}
                                            options={[
                                                { value: "CATALOG", label: "Estoque completo" },
                                                { value: "VEHICLE", label: "Veículo específico" },
                                            ]}
                                        />

                                        {form.scopeType === "VEHICLE" ? (
                                            <SelectField
                                                label="Veículo"
                                                value={form.vehicleId}
                                                onChange={(value) => updateForm("vehicleId", value)}
                                                options={vehicles.map((vehicle) => ({
                                                    value: vehicle.id,
                                                    label: vehicle.title,
                                                }))}
                                                placeholder="Selecione um veículo"
                                            />
                                        ) : (
                                            <div className="rounded-[26px] border border-black/10 bg-[#faf8f4] px-5 py-4 text-sm text-black/58">
                                                Esse link vai abrir a listagem completa do estoque.
                                            </div>
                                        )}
                                    </div>
                                ) : (
                                    <div className="rounded-[26px] border border-black/10 bg-[#faf8f4] px-5 py-4 text-sm text-black/58">
                                        O link público sempre aponta para o estoque completo da empresa.
                                    </div>
                                )}

                                <div className="rounded-[28px] bg-[#faf8f4] px-5 py-5">
                                    <p className="text-xs font-semibold uppercase tracking-[0.2em] text-black/38">Preview do link</p>
                                    <p className="mt-3 break-all text-sm leading-6 text-black/68">{origin}{previewPath}</p>
                                </div>

                                <div className="flex flex-col gap-3 sm:flex-row sm:justify-end">
                                    <button
                                        type="button"
                                        onClick={closeCreateModal}
                                        className="inline-flex h-12 items-center justify-center rounded-full border border-black/12 px-5 text-sm font-semibold text-black/68 transition hover:border-black/20 hover:text-black"
                                    >
                                        Cancelar
                                    </button>
                                    <button
                                        type="submit"
                                        disabled={saving || (form.linkKind === "CAMPAIGN" && form.scopeType === "VEHICLE" && !form.vehicleId)}
                                        className="inline-flex h-12 items-center justify-center gap-2 rounded-full bg-black px-5 text-sm font-semibold text-white transition hover:bg-black/85 disabled:cursor-not-allowed disabled:bg-black/30"
                                    >
                                        {saving ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
                                        Criar link
                                    </button>
                                </div>
                            </form>
                        </div>
                    </div>
                </div>
            ) : null}
        </>
    );
}

function LinkSection({
    title,
    description,
    icon,
    links,
    copiedLinkId,
    onCopyLink,
    onDeleteLink,
}: {
    title: string;
    description: string;
    icon: ReactNode;
    links: PublicLinkRecord[];
    copiedLinkId: string | null;
    onCopyLink: (link: PublicLinkRecord) => void;
    onDeleteLink: (link: PublicLinkRecord) => void;
}) {
    return (
        <section className="rounded-[34px] border border-black/10 bg-white p-5 shadow-[0_18px_45px_rgba(0,0,0,0.06)] md:p-6">
            <div>
                <p className="inline-flex items-center gap-2 rounded-full bg-[#f4efe7] px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.22em] text-[#7b5b2a]">
                    {icon}
                    {title}
                </p>
                <p className="mt-3 text-sm text-black/56">{description}</p>
            </div>

            {links.length ? (
                <div className="mt-5 overflow-hidden rounded-[28px] border border-black/10">
                    {links.map((link, index) => (
                        <article
                            key={link.id}
                            className={`grid gap-4 bg-white px-4 py-4 lg:grid-cols-[1.3fr_0.95fr_0.7fr_0.7fr_0.7fr_0.95fr_auto] lg:items-center ${
                                index === 0 ? "" : "border-t border-black/8"
                            }`}
                        >
                            <div className="min-w-0">
                                <p className="truncate text-base font-semibold text-io-dark">{link.name}</p>
                                <p className="mt-1 text-sm text-black/56">
                                    {linkKindLabel(link.linkKind)} • {scopeLabel(link.scopeType)}
                                    {link.vehicleTitle ? ` • ${link.vehicleTitle}` : ""}
                                </p>
                            </div>

                            <div>
                                <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-black/35">Origem</p>
                                <p className="mt-1 text-sm text-black/62">{link.sourceReference || "Sem rastreio"}</p>
                            </div>

                            <div>
                                <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-black/35">Interações</p>
                                <p className="mt-1 text-sm text-black/62">{link.totalInteractions}</p>
                            </div>

                            <div>
                                <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-black/35">Contatos</p>
                                <p className="mt-1 text-sm text-black/62">{link.contactClicks}</p>
                            </div>

                            <div>
                                <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-black/35">Interesses</p>
                                <p className="mt-1 text-sm text-black/62">{link.interestClicks}</p>
                            </div>

                            <div>
                                <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-black/35">Última interação</p>
                                <p className="mt-1 text-sm text-black/62">{formatDateTime(link.lastInteractionAt)}</p>
                            </div>

                            <div className="flex items-center justify-end gap-2">
                                <button
                                    type="button"
                                    onClick={() => onCopyLink(link)}
                                    className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-black/12 bg-white text-black/72 transition hover:border-black/22 hover:text-black"
                                    aria-label={`Copiar link ${link.name}`}
                                    title={copiedLinkId === link.id ? "Copiado" : "Copiar link"}
                                >
                                    {copiedLinkId === link.id ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
                                </button>
                                <button
                                    type="button"
                                    onClick={() => onDeleteLink(link)}
                                    className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-red-200 bg-red-50 text-red-700 transition hover:bg-red-100"
                                    aria-label={`Excluir link ${link.name}`}
                                    title="Excluir link"
                                >
                                    <Trash2 className="h-4 w-4" />
                                </button>
                            </div>
                        </article>
                    ))}
                </div>
            ) : (
                <div className="mt-5 rounded-[28px] border border-dashed border-black/12 bg-[#faf8f4] px-6 py-10 text-center">
                    <div className="mx-auto grid h-14 w-14 place-items-center rounded-full bg-white text-black/48 shadow-[0_10px_25px_rgba(15,23,42,0.05)]">
                        <Link2 className="h-5 w-5" />
                    </div>
                    <h3 className="mt-4 font-display text-2xl font-bold text-io-dark">Nenhum link encontrado</h3>
                    <p className="mt-2 text-sm text-black/56">Crie um novo link para começar a divulgar o estoque ou medir campanhas.</p>
                </div>
            )}
        </section>
    );
}

function Field({
    label,
    value,
    onChange,
    placeholder,
    required = false,
}: {
    label: string;
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
    required?: boolean;
}) {
    return (
        <label className="grid gap-2">
            <span className="text-sm font-medium text-black/60">{label}</span>
            <input
                value={value}
                onChange={(event) => onChange(event.target.value)}
                placeholder={placeholder}
                required={required}
                className="h-12 rounded-2xl border border-black/10 bg-[#f7f7f7] px-4 text-sm text-io-dark outline-none transition focus:border-black/30 focus:bg-white"
            />
        </label>
    );
}

function SelectField({
    label,
    value,
    onChange,
    options,
    placeholder,
}: {
    label: string;
    value: string;
    onChange: (value: string) => void;
    options: Array<{ value: string; label: string }>;
    placeholder?: string;
}) {
    return (
        <label className="grid gap-2">
            <span className="text-sm font-medium text-black/60">{label}</span>
            <select
                value={value}
                onChange={(event) => onChange(event.target.value)}
                className="h-12 rounded-2xl border border-black/10 bg-[#f7f7f7] px-4 text-sm text-io-dark outline-none transition focus:border-black/30 focus:bg-white"
            >
                {placeholder ? <option value="">{placeholder}</option> : null}
                {options.map((option) => (
                    <option key={option.value} value={option.value}>
                        {option.label}
                    </option>
                ))}
            </select>
        </label>
    );
}
