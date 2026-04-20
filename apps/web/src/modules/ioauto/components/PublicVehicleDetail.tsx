"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
    ArrowLeft,
    CalendarDays,
    CarFront,
    Gauge,
    Info,
    MapPin,
    MessageCircle,
    Palette,
    Settings2,
    ShieldCheck,
    Sparkles,
} from "lucide-react";
import { formatMoney } from "@/modules/ioauto/formatters";
import type { PublicVehicleDetail } from "@/modules/ioauto/types";

function getVehicleImages(detail: PublicVehicleDetail["vehicle"]) {
    return Array.from(new Set([detail.coverImageUrl, ...detail.gallery].filter(Boolean))) as string[];
}

function formatMileage(value?: number | null) {
    if (value == null || Number.isNaN(Number(value))) return "Quilometragem nao informada";
    return `${new Intl.NumberFormat("pt-BR").format(value)} km`;
}

function formatVehicleYears(detail: Pick<PublicVehicleDetail["vehicle"], "modelYear" | "manufactureYear">) {
    if (detail.manufactureYear && detail.modelYear) return `${detail.manufactureYear}/${detail.modelYear}`;
    if (detail.modelYear) return String(detail.modelYear);
    if (detail.manufactureYear) return String(detail.manufactureYear);
    return "Ano nao informado";
}

function buildVehicleLocation(detail: Pick<PublicVehicleDetail["vehicle"], "city" | "state">) {
    const parts = [detail.city, detail.state].filter(Boolean);
    return parts.length ? parts.join(" / ") : "Localizacao nao informada";
}

function buildWhatsappHref(phone?: string | null, vehicleTitle?: string) {
    const digits = String(phone ?? "").replace(/\D/g, "");
    if (!digits) return null;
    const message = vehicleTitle
        ? `Ola! Tenho interesse no veiculo ${vehicleTitle}.`
        : "Ola! Gostaria de mais informacoes sobre o catalogo.";
    return `https://wa.me/${digits}?text=${encodeURIComponent(message)}`;
}

function getInitials(name?: string | null) {
    const parts = String(name ?? "IO Auto")
        .trim()
        .split(/\s+/)
        .filter(Boolean);
    return `${parts[0]?.[0] ?? "I"}${parts[1]?.[0] ?? "O"}`.toUpperCase();
}

export function PublicVehicleDetailView({ data }: { data: PublicVehicleDetail }) {
    const images = useMemo(() => getVehicleImages(data.vehicle), [data.vehicle]);
    const [selectedImage, setSelectedImage] = useState(images[0] ?? null);

    useEffect(() => {
        setSelectedImage(images[0] ?? null);
    }, [images]);

    const contactHref = buildWhatsappHref(data.company.whatsappNumber, data.vehicle.title);
    const specifications = [
        { label: "Marca", value: data.vehicle.brand, icon: <CarFront className="h-4 w-4" /> },
        { label: "Modelo", value: data.vehicle.model, icon: <Info className="h-4 w-4" /> },
        { label: "Ano", value: formatVehicleYears(data.vehicle), icon: <CalendarDays className="h-4 w-4" /> },
        { label: "Quilometragem", value: formatMileage(data.vehicle.mileage), icon: <Gauge className="h-4 w-4" /> },
        { label: "Cambio", value: data.vehicle.transmission ?? "Nao informado", icon: <Settings2 className="h-4 w-4" /> },
        { label: "Combustivel", value: data.vehicle.fuelType ?? "Nao informado", icon: <Sparkles className="h-4 w-4" /> },
        { label: "Carroceria", value: data.vehicle.bodyType ?? "Nao informado", icon: <ShieldCheck className="h-4 w-4" /> },
        { label: "Cor", value: data.vehicle.color ?? "Nao informado", icon: <Palette className="h-4 w-4" /> },
        { label: "Localizacao", value: buildVehicleLocation(data.vehicle), icon: <MapPin className="h-4 w-4" /> },
        { label: "Final da placa", value: data.vehicle.plateFinal ?? "Nao informado", icon: <Info className="h-4 w-4" /> },
    ];

    return (
        <main className="min-h-screen bg-[radial-gradient(circle_at_top,_rgba(255,255,255,0.95),_rgba(245,243,239,0.96)_56%,_rgba(236,232,226,0.98))] text-io-dark">
            <div className="mx-auto max-w-7xl px-4 py-5 md:px-6 md:py-7">
                <header className="rounded-[30px] border border-black/10 bg-white/92 px-5 py-4 shadow-[0_20px_55px_rgba(15,23,42,0.08)] backdrop-blur md:px-7">
                    <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                        <div className="flex items-center gap-4">
                            {data.company.profileImageUrl ? (
                                <img src={data.company.profileImageUrl} alt={data.company.name} className="h-14 w-14 rounded-[20px] border border-black/10 object-cover" />
                            ) : (
                                <div className="grid h-14 w-14 place-items-center rounded-[20px] bg-io-dark text-sm font-bold text-white">
                                    {getInitials(data.company.name)}
                                </div>
                            )}

                            <div>
                                <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-black/42">Catalogo publico</p>
                                <h1 className="mt-1 font-display text-2xl font-bold md:text-3xl">{data.company.name}</h1>
                            </div>
                        </div>

                        <a
                            href={contactHref ?? undefined}
                            target={contactHref ? "_blank" : undefined}
                            rel={contactHref ? "noreferrer" : undefined}
                            className={`inline-flex h-12 items-center justify-center gap-2 rounded-full px-5 text-sm font-semibold transition ${
                                contactHref
                                    ? "bg-[#111111] text-white hover:bg-black/85"
                                    : "cursor-not-allowed bg-black/[0.06] text-black/45"
                            }`}
                        >
                            <MessageCircle className="h-4 w-4" />
                            {contactHref ? "Contato via WhatsApp" : "Contato indisponivel"}
                        </a>
                    </div>
                </header>

                <div className="mt-6">
                    <Link
                        href={`/estoque-publico/${data.company.id}`}
                        className="inline-flex items-center gap-2 rounded-full bg-white px-4 py-3 text-sm font-medium text-black/65 shadow-[0_10px_30px_rgba(15,23,42,0.07)] transition hover:text-black"
                    >
                        <ArrowLeft className="h-4 w-4" />
                        Voltar ao catalogo
                    </Link>
                </div>

                <section className="mt-5 grid gap-6 lg:grid-cols-[1.2fr_0.8fr] lg:items-start">
                    <div className="rounded-[34px] border border-black/10 bg-white p-4 shadow-[0_20px_60px_rgba(15,23,42,0.09)] md:p-5">
                        <div className="overflow-hidden rounded-[28px] bg-[#efebe4]">
                            {selectedImage ? (
                                <img src={selectedImage} alt={data.vehicle.title} className="h-[320px] w-full object-cover md:h-[520px]" />
                            ) : (
                                <div className="flex h-[320px] items-center justify-center bg-[linear-gradient(135deg,_#171717,_#4a4a4a)] text-white/70 md:h-[520px]">
                                    Sem imagens disponiveis
                                </div>
                            )}
                        </div>

                        {images.length ? (
                            <div className="mt-4 flex gap-3 overflow-x-auto pb-2">
                                {images.map((imageUrl) => (
                                    <button
                                        key={imageUrl}
                                        type="button"
                                        onClick={() => setSelectedImage(imageUrl)}
                                        className={`overflow-hidden rounded-[20px] border transition ${
                                            imageUrl === selectedImage ? "border-black shadow-[0_12px_30px_rgba(15,23,42,0.16)]" : "border-black/10"
                                        }`}
                                    >
                                        <img src={imageUrl} alt={data.vehicle.title} className="h-20 w-24 object-cover md:h-24 md:w-32" />
                                    </button>
                                ))}
                            </div>
                        ) : null}
                    </div>

                    <aside className="rounded-[34px] border border-black/10 bg-white p-6 shadow-[0_20px_60px_rgba(15,23,42,0.09)] lg:sticky lg:top-6">
                        <div className="flex flex-wrap gap-2">
                            <span className="rounded-full bg-[#101010] px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] text-white">
                                {data.vehicle.brand}
                            </span>
                            {data.vehicle.featured ? (
                                <span className="rounded-full bg-[#8aa7ff] px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.18em] text-white">
                                    Novidade
                                </span>
                            ) : null}
                        </div>

                        <h2 className="mt-4 font-display text-4xl font-bold leading-none md:text-5xl">{data.vehicle.modelYear ?? "Sem ano"}</h2>
                        <p className="mt-3 text-lg font-semibold text-black/72">{data.vehicle.title}</p>

                        <div className="mt-5 flex flex-wrap gap-3 text-sm text-black/58">
                            <span className="inline-flex items-center gap-2 rounded-full bg-black/[0.04] px-3 py-2">
                                <CalendarDays className="h-4 w-4" />
                                {formatVehicleYears(data.vehicle)}
                            </span>
                            <span className="inline-flex items-center gap-2 rounded-full bg-black/[0.04] px-3 py-2">
                                <Gauge className="h-4 w-4" />
                                {formatMileage(data.vehicle.mileage)}
                            </span>
                        </div>

                        <div className="mt-6 rounded-[28px] border border-black/10 bg-[#faf8f4] px-5 py-5">
                            <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-black/38">Preco sugerido</p>
                            <p className="mt-2 text-4xl font-bold tracking-tight text-io-dark">{formatMoney(data.vehicle.priceCents)}</p>
                        </div>

                        <a
                            href={contactHref ?? undefined}
                            target={contactHref ? "_blank" : undefined}
                            rel={contactHref ? "noreferrer" : undefined}
                            className={`mt-6 inline-flex h-14 w-full items-center justify-center gap-2 rounded-full px-5 text-sm font-semibold transition ${
                                contactHref
                                    ? "bg-[#22c55e] text-white hover:bg-[#16a34a]"
                                    : "cursor-not-allowed bg-black/[0.06] text-black/45"
                            }`}
                        >
                            <MessageCircle className="h-4 w-4" />
                            Tenho Interesse
                        </a>

                        <div className="mt-6 rounded-[26px] bg-black/[0.03] px-4 py-4">
                            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-black/38">Resumo rapido</p>
                            <div className="mt-3 grid gap-2 text-sm text-black/62">
                                <span>{buildVehicleLocation(data.vehicle)}</span>
                                <span>{data.vehicle.transmission ?? "Cambio nao informado"}</span>
                                <span>{data.vehicle.fuelType ?? "Combustivel nao informado"}</span>
                            </div>
                        </div>
                    </aside>
                </section>

                <section className="mt-7">
                    <div className="flex items-center gap-3">
                        <div className="h-8 w-1 rounded-full bg-black" />
                        <h3 className="font-display text-2xl font-bold md:text-3xl">Especificacoes detalhadas</h3>
                    </div>

                    <div className="mt-5 rounded-[34px] border border-black/10 bg-white p-5 shadow-[0_18px_55px_rgba(15,23,42,0.07)] md:p-6">
                        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                            {specifications.map((item) => (
                                <div key={item.label} className="rounded-[24px] bg-[#faf8f4] px-4 py-4">
                                    <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.22em] text-black/42">
                                        {item.icon}
                                        {item.label}
                                    </div>
                                    <p className="mt-3 text-sm font-medium text-io-dark">{item.value}</p>
                                </div>
                            ))}
                        </div>

                        <div className="mt-6 grid gap-5 lg:grid-cols-[0.9fr_1.1fr]">
                            <div className="rounded-[28px] bg-[#111111] px-5 py-5 text-white">
                                <p className="text-xs font-semibold uppercase tracking-[0.22em] text-white/45">Descricao</p>
                                <p className="mt-4 text-sm leading-7 text-white/76">
                                    {data.vehicle.description?.trim() || "Este veiculo esta disponivel para atendimento e negociacao. Fale com a loja para receber fotos, condicoes e simulacoes."}
                                </p>
                            </div>

                            <div className="rounded-[28px] border border-black/10 bg-[#faf8f4] px-5 py-5">
                                <p className="text-xs font-semibold uppercase tracking-[0.22em] text-black/42">Itens e opcionais</p>
                                {data.vehicle.optionals.length ? (
                                    <div className="mt-4 grid gap-2 md:grid-cols-2">
                                        {data.vehicle.optionals.map((optional) => (
                                            <div key={optional} className="rounded-2xl bg-white px-4 py-3 text-sm text-black/72 shadow-[0_10px_25px_rgba(15,23,42,0.05)]">
                                                {optional}
                                            </div>
                                        ))}
                                    </div>
                                ) : (
                                    <p className="mt-4 text-sm text-black/56">Os opcionais detalhados deste veiculo nao foram informados.</p>
                                )}
                            </div>
                        </div>
                    </div>
                </section>
            </div>
        </main>
    );
}
