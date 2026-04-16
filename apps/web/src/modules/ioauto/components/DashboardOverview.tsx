"use client";

import type { ReactNode } from "react";
import { useEffect, useMemo, useState } from "react";
import Highcharts from "highcharts";
import HighchartsReact from "highcharts-react-official";
import { BadgeDollarSign, Cable, CarFront, MessagesSquare, Stars } from "lucide-react";
import type { DashboardResponse } from "@/modules/ioauto/types";
import { formatDateTime, formatMoney, statusLabel } from "@/modules/ioauto/formatters";

const statCards = [
    { key: "vehicleCount", label: "Veículos no catálogo", icon: <CarFront className="h-5 w-5" /> },
    { key: "publicationCount", label: "Publicações em andamento", icon: <Stars className="h-5 w-5" /> },
    { key: "leadCount", label: "Leads em andamento", icon: <MessagesSquare className="h-5 w-5" /> },
    { key: "connectedIntegrations", label: "Integrações conectadas", icon: <Cable className="h-5 w-5" /> },
] as const;

const periodOptions = [
    { key: "7d", label: "7 dias" },
    { key: "30d", label: "30 dias" },
    { key: "90d", label: "90 dias" },
    { key: "month", label: "Mês atual" },
    { key: "custom", label: "Personalizado" },
] as const;

type PeriodPreset = (typeof periodOptions)[number]["key"];

function toDateInputValue(date: Date) {
    const year = date.getFullYear();
    const month = `${date.getMonth() + 1}`.padStart(2, "0");
    const day = `${date.getDate()}`.padStart(2, "0");
    return `${year}-${month}-${day}`;
}

function daysAgo(days: number) {
    const base = new Date();
    base.setDate(base.getDate() - days);
    return toDateInputValue(base);
}

function sum(values: number[]) {
    return values.reduce((total, current) => total + current, 0);
}

export function DashboardOverview() {
    const [data, setData] = useState<DashboardResponse | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [preset, setPreset] = useState<PeriodPreset>("30d");
    const [customFrom, setCustomFrom] = useState<string>(daysAgo(29));
    const [customTo, setCustomTo] = useState<string>(toDateInputValue(new Date()));

    useEffect(() => {
        if (preset === "custom" && (!customFrom || !customTo)) {
            return;
        }

        const controller = new AbortController();
        const query = new URLSearchParams();
        query.set("preset", preset);
        if (preset === "custom") {
            query.set("from", customFrom);
            query.set("to", customTo);
        }

        setLoading(true);
        fetch(`/api/ioauto/dashboard?${query.toString()}`, {
            cache: "no-store",
            signal: controller.signal,
        })
            .then(async (response) => {
                if (!response.ok) {
                    const payload = await response.json().catch(() => ({ message: "Falha ao carregar o dashboard." }));
                    throw new Error(payload.message ?? "Falha ao carregar o dashboard.");
                }
                return response.json();
            })
            .then((payload: DashboardResponse) => {
                setData(payload);
                setError(null);
            })
            .catch((cause: Error) => {
                if (cause.name === "AbortError") return;
                setError(cause.message);
            })
            .finally(() => {
                if (!controller.signal.aborted) {
                    setLoading(false);
                }
            });

        return () => {
            controller.abort();
        };
    }, [preset, customFrom, customTo]);

    const lineChartOptions = useMemo<Highcharts.Options>(() => {
        const points = data?.leadVsSales ?? [];
        return {
            chart: {
                type: "spline",
                backgroundColor: "transparent",
                height: 360,
                spacing: [20, 12, 12, 12],
            },
            title: { text: undefined },
            credits: { enabled: false },
            legend: {
                itemStyle: {
                    color: "#14213d",
                    fontWeight: "600",
                },
                symbolRadius: 999,
            },
            xAxis: {
                categories: points.map((item) => item.label),
                lineColor: "#d9d9d9",
                tickColor: "#d9d9d9",
                labels: {
                    style: {
                        color: "#5b6170",
                        fontSize: "11px",
                    },
                },
            },
            yAxis: {
                title: { text: undefined },
                gridLineColor: "rgba(20,33,61,0.08)",
                labels: {
                    style: {
                        color: "#5b6170",
                        fontSize: "11px",
                    },
                },
                allowDecimals: false,
            },
            tooltip: {
                shared: true,
                backgroundColor: "#111111",
                borderWidth: 0,
                borderRadius: 18,
                style: {
                    color: "#ffffff",
                },
            },
            plotOptions: {
                series: {
                    marker: {
                        enabled: false,
                        symbol: "circle",
                    },
                    lineWidth: 3,
                },
                spline: {
                    states: {
                        hover: {
                            lineWidthPlus: 0,
                        },
                    },
                },
            },
            series: [
                {
                    type: "spline",
                    name: "Atendimentos",
                    data: points.map((item) => item.leads),
                    color: "#f59e0b",
                },
                {
                    type: "spline",
                    name: "Vendas",
                    data: points.map((item) => item.sales),
                    color: "#111111",
                },
            ],
        };
    }, [data?.leadVsSales]);

    const sellerChartOptions = useMemo<Highcharts.Options>(() => {
        const sellers = data?.salesBySeller ?? [];
        return {
            chart: {
                type: "bar",
                backgroundColor: "transparent",
                height: Math.max(320, sellers.length * 60 + 110),
                spacing: [20, 12, 12, 12],
            },
            title: { text: undefined },
            credits: { enabled: false },
            legend: { enabled: false },
            xAxis: {
                categories: sellers.map((item) => item.sellerName),
                lineColor: "#d9d9d9",
                tickColor: "#d9d9d9",
                labels: {
                    style: {
                        color: "#5b6170",
                        fontSize: "12px",
                    },
                },
            },
            yAxis: {
                title: { text: undefined },
                allowDecimals: false,
                gridLineColor: "rgba(20,33,61,0.08)",
                labels: {
                    style: {
                        color: "#5b6170",
                        fontSize: "11px",
                    },
                },
            },
            tooltip: {
                backgroundColor: "#111111",
                borderWidth: 0,
                borderRadius: 18,
                style: {
                    color: "#ffffff",
                },
                pointFormat: "<b>{point.y}</b> vendas",
            },
            plotOptions: {
                bar: {
                    borderRadius: 10,
                    pointPadding: 0.16,
                    groupPadding: 0.08,
                },
                series: {
                    color: "#14213d",
                },
            },
            series: [
                {
                    type: "bar",
                    name: "Vendas",
                    data: sellers.map((item) => item.totalSales),
                },
            ],
        };
    }, [data?.salesBySeller]);

    const totalPeriodLeads = useMemo(() => sum((data?.leadVsSales ?? []).map((item) => item.leads)), [data?.leadVsSales]);
    const totalPeriodSales = useMemo(() => sum((data?.leadVsSales ?? []).map((item) => item.sales)), [data?.leadVsSales]);

    if (error) {
        return <div className="rounded-[32px] border border-red-200 bg-red-50 px-6 py-5 text-sm text-red-700">{error}</div>;
    }

    return (
        <div className="grid gap-6">
            <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                {statCards.map((item) => (
                    <article key={item.key} className="rounded-[28px] border border-black/10 bg-white p-5 shadow-[0_18px_45px_rgba(0,0,0,0.06)]">
                        <div className="flex items-center justify-between">
                            <span className="grid h-11 w-11 place-items-center rounded-2xl bg-black text-white">{item.icon}</span>
                            <span className="text-3xl font-bold text-io-dark">{String(data?.[item.key] ?? "-")}</span>
                        </div>
                        <p className="mt-4 text-sm text-black/55">{item.label}</p>
                    </article>
                ))}
            </section>

            <section className="rounded-[34px] border border-black/10 bg-white p-6 shadow-[0_18px_45px_rgba(0,0,0,0.06)]">
                <div className="flex flex-col gap-5 xl:flex-row xl:items-start xl:justify-between">
                    <div>
                        <h2 className="mt-2 font-display text-3xl font-bold text-io-dark">Vendas x atendimentos no período</h2>
                        <p className="mt-2 max-w-2xl text-sm text-black/55">
                            Compare o volume de leads que chegaram com as vendas concluídas dentro do mesmo intervalo.
                        </p>
                    </div>

                    <div className="grid gap-3 xl:justify-items-end">
                        <div className="flex flex-wrap gap-2">
                            {periodOptions.map((option) => (
                                <button
                                    key={option.key}
                                    type="button"
                                    onClick={() => setPreset(option.key)}
                                    className={`rounded-full px-4 py-2 text-sm font-semibold transition ${
                                        preset === option.key
                                            ? "bg-black text-white shadow-[0_10px_24px_rgba(0,0,0,0.16)]"
                                            : "border border-black/10 bg-white text-black/60 hover:border-black/20 hover:text-black"
                                    }`}
                                >
                                    {option.label}
                                </button>
                            ))}
                        </div>

                        {preset === "custom" ? (
                            <div className="flex flex-wrap gap-3">
                                <label className="grid gap-1 text-xs uppercase tracking-[0.2em] text-black/40">
                                    <span>De</span>
                                    <input
                                        type="date"
                                        value={customFrom}
                                        onChange={(event) => setCustomFrom(event.target.value)}
                                        className="rounded-2xl border border-black/10 bg-white px-3 py-2 text-sm font-medium text-io-dark outline-none transition focus:border-black/25"
                                    />
                                </label>
                                <label className="grid gap-1 text-xs uppercase tracking-[0.2em] text-black/40">
                                    <span>Até</span>
                                    <input
                                        type="date"
                                        value={customTo}
                                        onChange={(event) => setCustomTo(event.target.value)}
                                        className="rounded-2xl border border-black/10 bg-white px-3 py-2 text-sm font-medium text-io-dark outline-none transition focus:border-black/25"
                                    />
                                </label>
                            </div>
                        ) : null}
                    </div>
                </div>

                <div className="mt-6 grid gap-4 md:grid-cols-3">
                    <MetricPill
                        label="Atendimentos no período"
                        value={String(totalPeriodLeads)}
                        description={`${data?.periodFilter?.from ?? "-"} até ${data?.periodFilter?.to ?? "-"}`}
                    />
                    <MetricPill
                        label="Vendas no período"
                        value={String(totalPeriodSales)}
                        description="Fechamentos confirmados com veículo vinculado"
                    />
                    <MetricPill
                        label="Receita base do catálogo"
                        value={formatMoney(data?.recentVehicles?.[0]?.priceCents ?? null)}
                        description="Referência rápida do estoque mais recente"
                        icon={<BadgeDollarSign className="h-4 w-4" />}
                    />
                </div>

                <div className="mt-6 rounded-[30px] bg-[#f7f4ee] p-4">
                    {loading ? (
                        <div className="grid h-[360px] place-items-center text-sm text-black/45">Carregando gráfico...</div>
                    ) : (
                        <HighchartsReact highcharts={Highcharts} options={lineChartOptions} />
                    )}
                </div>
            </section>

            <section className="rounded-[34px] border border-black/10 bg-white p-6 shadow-[0_18px_45px_rgba(0,0,0,0.06)]">
                <div>
                    <h2 className="mt-2 font-display text-3xl font-bold text-io-dark">Total de vendas por vendedor</h2>
                    <p className="mt-2 text-sm text-black/55">
                        Cada venda concluída entra automaticamente no ranking do vendedor responsável pelo atendimento.
                    </p>
                </div>

                <div className="mt-6 rounded-[30px] bg-[#f3f5f7] p-4">
                    {loading ? (
                        <div className="grid h-[320px] place-items-center text-sm text-black/45">Carregando ranking...</div>
                    ) : (data?.salesBySeller?.length ?? 0) > 0 ? (
                        <HighchartsReact highcharts={Highcharts} options={sellerChartOptions} />
                    ) : (
                        <div className="grid h-[320px] place-items-center rounded-[26px] border border-dashed border-black/10 bg-white px-6 text-center text-sm text-black/45">
                            Assim que as primeiras vendas forem concluídas pelos atendimentos integrados, o comparativo entre vendedores aparecerá aqui.
                        </div>
                    )}
                </div>
            </section>

            <section className="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
                <article className="rounded-[32px] border border-black/10 bg-white p-6 shadow-[0_18px_45px_rgba(0,0,0,0.06)]">
                    <h2 className="font-display text-2xl font-bold text-io-dark">Veículos recentes</h2>
                    <div className="mt-5 grid gap-3">
                        {(data?.recentVehicles ?? []).length ? (
                            data!.recentVehicles.map((vehicle) => (
                                <div key={vehicle.id} className="rounded-2xl bg-black/[0.03] px-4 py-4">
                                    <div className="flex items-center justify-between gap-3">
                                        <p className="text-sm font-semibold text-io-dark">{vehicle.title}</p>
                                        <span className="text-sm font-bold text-io-dark">{formatMoney(vehicle.priceCents)}</span>
                                    </div>
                                    <div className="mt-2 flex flex-wrap items-center gap-3 text-xs text-black/50">
                                        <span>{statusLabel(vehicle.status)}</span>
                                        <span>{vehicle.publicationCount} canais</span>
                                        <span>{formatDateTime(vehicle.updatedAt)}</span>
                                    </div>
                                </div>
                            ))
                        ) : (
                            <p className="rounded-2xl border border-dashed border-black/10 px-4 py-4 text-sm text-black/45">
                                O cadastro unificado de veículos vai alimentar esta lista.
                            </p>
                        )}
                    </div>
                </article>

                <article className="rounded-[32px] border border-black/10 bg-white p-6 shadow-[0_18px_45px_rgba(0,0,0,0.06)]">
                    <h2 className="font-display text-2xl font-bold text-io-dark">Ranking de origem de leads</h2>
                    <p className="mt-1 text-sm text-black/55">Veja a plataforma que mais está trazendo leads para sua operação.</p>
                    <div className="mt-5 grid gap-3">
                        {(data?.leadSources ?? []).length ? (
                            data!.leadSources.map((source) => (
                                <div key={source.key} className="flex items-center justify-between rounded-2xl bg-black/[0.03] px-4 py-3">
                                    <span className="text-sm font-medium text-black/65">{source.label}</span>
                                    <span className="rounded-full bg-black px-3 py-1 text-sm font-semibold text-white">{source.total}</span>
                                </div>
                            ))
                        ) : (
                            <p className="rounded-2xl border border-dashed border-black/10 px-4 py-4 text-sm text-black/45">
                                As origens dos leads vão aparecer aqui assim que os atendimentos entrarem pelas integrações de marketplace.
                            </p>
                        )}
                    </div>
                </article>
            </section>
        </div>
    );
}

function MetricPill({
    label,
    value,
    description,
    icon,
}: {
    label: string;
    value: string;
    description: string;
    icon?: ReactNode;
}) {
    return (
        <div className="rounded-[24px] border border-black/10 bg-black/[0.02] px-4 py-4">
            <div className="flex items-center gap-2 text-xs uppercase tracking-[0.24em] text-black/40">
                {icon ?? <span className="h-2 w-2 rounded-full bg-black/35" />}
                <span>{label}</span>
            </div>
            <p className="mt-3 text-2xl font-bold text-io-dark">{value}</p>
            <p className="mt-2 text-sm text-black/50">{description}</p>
        </div>
    );
}
