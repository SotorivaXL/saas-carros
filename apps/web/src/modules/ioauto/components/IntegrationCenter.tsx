"use client";

import { useEffect, useMemo, useState } from "react";
import { Cable, LoaderCircle, Save } from "lucide-react";
import type { IntegrationRecord } from "@/modules/ioauto/types";
import { formatDateTime, statusLabel } from "@/modules/ioauto/formatters";

type IntegrationDraft = {
    displayName: string;
    status: string;
    endpointUrl: string;
    accountName: string;
    username: string;
    apiToken: string;
    webhookSecret: string;
    lastError: string;
};

function toDraft(record: IntegrationRecord): IntegrationDraft {
    return {
        displayName: record.displayName,
        status: record.status,
        endpointUrl: record.endpointUrl ?? "",
        accountName: record.accountName ?? "",
        username: record.username ?? "",
        apiToken: "",
        webhookSecret: "",
        lastError: record.lastError ?? "",
    };
}

export function IntegrationCenter() {
    const [integrations, setIntegrations] = useState<IntegrationRecord[]>([]);
    const [drafts, setDrafts] = useState<Record<string, IntegrationDraft>>({});
    const [savingProvider, setSavingProvider] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    async function loadIntegrations() {
        const response = await fetch("/api/ioauto/integrations", { cache: "no-store" });
        if (!response.ok) {
            const payload = await response.json().catch(() => ({ message: "Falha ao carregar as integrações." }));
            throw new Error(payload.message ?? "Falha ao carregar as integrações.");
        }
        const payload = (await response.json()) as IntegrationRecord[];
        setIntegrations(payload);
        setDrafts(Object.fromEntries(payload.map((integration) => [integration.providerKey, toDraft(integration)])));
    }

    useEffect(() => {
        loadIntegrations().catch((cause: Error) => setError(cause.message));
    }, []);

    const connectedCount = useMemo(
        () => integrations.filter((integration) => integration.status === "CONNECTED" || integration.status === "ACTIVE").length,
        [integrations]
    );

    function updateDraft(providerKey: string, patch: Partial<IntegrationDraft>) {
        setDrafts((current) => ({
            ...current,
            [providerKey]: {
                ...current[providerKey],
                ...patch,
            },
        }));
    }

    async function handleSave(providerKey: string) {
        const draft = drafts[providerKey];
        setSavingProvider(providerKey);
        setError(null);

        const response = await fetch(`/api/ioauto/integrations/${providerKey}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                displayName: draft.displayName,
                status: draft.status,
                endpointUrl: draft.endpointUrl,
                accountName: draft.accountName,
                username: draft.username,
                apiToken: draft.apiToken,
                webhookSecret: draft.webhookSecret,
                lastError: draft.lastError,
                settings: {},
                markSyncedNow: draft.status === "CONNECTED" || draft.status === "ACTIVE",
            }),
        });

        if (!response.ok) {
            const payload = await response.json().catch(() => ({ message: "Falha ao atualizar a integração." }));
            setError(payload.message ?? "Falha ao atualizar a integração.");
            setSavingProvider(null);
            return;
        }

        await loadIntegrations();
        setSavingProvider(null);
    }

    return (
        <div className="grid gap-6">
            <section className="rounded-[34px] border border-black/10 bg-white p-6 shadow-[0_18px_45px_rgba(0,0,0,0.06)]">
                <div className="flex flex-wrap items-center justify-between gap-4">
                    <div>
                        <h1 className="font-display text-3xl font-bold text-io-dark">Integrações do ecossistema</h1>
                        <p className="mt-1 text-sm text-black/55">Conecte várias plataformas de venda em apenas um painel, com estoque, anúncios e leads no mesmo fluxo.</p>
                    </div>
                    <div className="inline-flex items-center gap-2 rounded-full bg-black px-4 py-2 text-sm font-semibold text-white">
                        <Cable className="h-4 w-4" />
                        {connectedCount} conectadas
                    </div>
                </div>
                {error ? <p className="mt-5 rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p> : null}
            </section>

            <section className="grid gap-5 xl:grid-cols-2">
                {integrations.map((integration) => {
                    const draft = drafts[integration.providerKey] ?? toDraft(integration);
                    const saving = savingProvider === integration.providerKey;

                    return (
                        <article key={integration.providerKey} className="rounded-[32px] border border-black/10 bg-white p-5 shadow-[0_18px_45px_rgba(0,0,0,0.06)]">
                            <div className="flex items-start justify-between gap-3">
                                <div>
                                    <p className="text-xs uppercase tracking-[0.28em] text-black/40">{integration.providerKey}</p>
                                    <h2 className="mt-2 font-display text-2xl font-bold text-io-dark">{integration.displayName}</h2>
                                    <p className="mt-1 text-sm text-black/55">{statusLabel(integration.status)}</p>
                                </div>
                                <span className="rounded-full bg-black/[0.04] px-3 py-1 text-xs text-black/55">
                                    Último sync {formatDateTime(integration.lastSyncAt)}
                                </span>
                            </div>

                            <div className="mt-5 grid gap-4 sm:grid-cols-2">
                                <Field label="Nome exibido" value={draft.displayName} onChange={(value) => updateDraft(integration.providerKey, { displayName: value })} />
                                <SelectField label="Status" value={draft.status} onChange={(value) => updateDraft(integration.providerKey, { status: value })} />
                                <Field label="Endpoint" value={draft.endpointUrl} onChange={(value) => updateDraft(integration.providerKey, { endpointUrl: value })} className="sm:col-span-2" />
                                <Field label="Conta / Dealer" value={draft.accountName} onChange={(value) => updateDraft(integration.providerKey, { accountName: value })} />
                                <Field label="Usuário" value={draft.username} onChange={(value) => updateDraft(integration.providerKey, { username: value })} />
                                <Field label={integration.hasApiToken ? "Novo token (opcional)" : "Token API"} value={draft.apiToken} onChange={(value) => updateDraft(integration.providerKey, { apiToken: value })} />
                                <Field label={integration.hasWebhookSecret ? "Novo segredo (opcional)" : "Webhook secret"} value={draft.webhookSecret} onChange={(value) => updateDraft(integration.providerKey, { webhookSecret: value })} />
                                <Field label="Observação de erro" value={draft.lastError} onChange={(value) => updateDraft(integration.providerKey, { lastError: value })} className="sm:col-span-2" />
                            </div>

                            <p className="mt-4 text-xs text-black/45">
                                {integration.supportsPublication
                                    ? "Esta integração participa do fluxo de publicação de veículos."
                                    : "Esta integração não publica veículos; ela é usada para receber e organizar atendimentos vindos das plataformas."}
                            </p>

                            <div className="mt-5 flex justify-end">
                                <button
                                    type="button"
                                    onClick={() => handleSave(integration.providerKey)}
                                    disabled={saving}
                                    className="inline-flex items-center gap-2 rounded-full bg-black px-5 py-3 text-sm font-semibold text-white transition hover:bg-black/85 disabled:cursor-not-allowed disabled:bg-black/20"
                                >
                                    {saving ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                                    Salvar integração
                                </button>
                            </div>
                        </article>
                    );
                })}
            </section>
        </div>
    );
}

function Field({ label, value, onChange, className = "" }: { label: string; value: string; onChange: (value: string) => void; className?: string }) {
    return (
        <label className={`grid gap-2 ${className}`}>
            <span className="text-sm font-medium text-black/60">{label}</span>
            <input value={value} onChange={(event) => onChange(event.target.value)} className="h-12 rounded-2xl border border-black/10 bg-[#f7f7f7] px-4 text-sm text-io-dark outline-none transition focus:border-black/30 focus:bg-white" />
        </label>
    );
}

function SelectField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
    return (
        <label className="grid gap-2">
            <span className="text-sm font-medium text-black/60">{label}</span>
            <select value={value} onChange={(event) => onChange(event.target.value)} className="h-12 rounded-2xl border border-black/10 bg-[#f7f7f7] px-4 text-sm text-io-dark outline-none transition focus:border-black/30 focus:bg-white">
                <option value="CONFIGURATION_REQUIRED">Configurar</option>
                <option value="CONNECTED">Conectado</option>
                <option value="ACTIVE">Ativo</option>
                <option value="ERROR">Com erro</option>
            </select>
        </label>
    );
}
