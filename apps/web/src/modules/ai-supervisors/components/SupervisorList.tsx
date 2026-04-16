"use client";

import { useDeferredValue, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { createSupervisor, getFriendlyHttpErrorMessage, listSupervisors, toggleSupervisorEnabled, type AiSupervisor, type AiSupervisorDistributionPayload, updateSupervisorDistribution } from "@/services/aiSupervisors";
import { EmptyState, ErrorState, ModalFrame, PageShell, SectionCard, StatusPill, SupervisorBreadcrumbs, ToastStack, type ToastMessage } from "./SupervisorUi";

const BASE_ROUTE = "/protected/ai/supervisores";
const PAGE_SIZE = 8;

function formatDateTime(value: string) {
    if (!value) return "-";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "-";
    return new Intl.DateTimeFormat("pt-BR", {
        dateStyle: "short",
        timeStyle: "short",
    }).format(date);
}

function getToastId() {
    return Date.now() + Math.floor(Math.random() * 1000);
}

type DuplicateModalState =
    | { open: false }
    | {
        open: true;
        supervisor: AiSupervisor;
        name: string;
        copyDistribution: boolean;
        submitting: boolean;
        error: string | null;
    };

export function SupervisorList() {
    const router = useRouter();
    const [supervisors, setSupervisors] = useState<AiSupervisor[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [search, setSearch] = useState("");
    const [page, setPage] = useState(1);
    const [toasts, setToasts] = useState<ToastMessage[]>([]);
    const [duplicateModal, setDuplicateModal] = useState<DuplicateModalState>({ open: false });

    const deferredSearch = useDeferredValue(search);
    const normalizedSearch = deferredSearch.trim().toLowerCase();
    const filteredSupervisors = supervisors.filter((supervisor) => supervisor.name.toLowerCase().includes(normalizedSearch));
    const totalPages = Math.max(1, Math.ceil(filteredSupervisors.length / PAGE_SIZE));
    const currentPage = Math.min(page, totalPages);
    const currentItems = filteredSupervisors.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

    useEffect(() => {
        setPage(1);
    }, [normalizedSearch]);

    useEffect(() => {
        loadData();
    }, []);

    async function loadData() {
        setLoading(true);
        setError(null);
        try {
            const data = await listSupervisors();
            setSupervisors(data);
        } catch (loadError) {
            setError(getFriendlyHttpErrorMessage(loadError, "Não foi possível carregar os supervisores."));
        } finally {
            setLoading(false);
        }
    }

    function pushToast(message: string, type: ToastMessage["type"]) {
        const id = getToastId();
        setToasts((current) => [...current, { id, message, type }]);
        window.setTimeout(() => {
            setToasts((current) => current.filter((item) => item.id !== id));
        }, 4500);
    }

    function dismissToast(id: number) {
        setToasts((current) => current.filter((item) => item.id !== id));
    }

    async function handleToggle(supervisor: AiSupervisor) {
        try {
            const updated = await toggleSupervisorEnabled(supervisor.id, !supervisor.enabled);
            setSupervisors((current) => current.map((item) => item.id === updated.id ? updated : item));
            pushToast(updated.enabled ? "Supervisor ativado." : "Supervisor desativado.", "success");
        } catch (toggleError) {
            pushToast(getFriendlyHttpErrorMessage(toggleError, "Não foi possível atualizar o status."), "error");
        }
    }

    function openDuplicateModal(supervisor: AiSupervisor) {
        setDuplicateModal({
            open: true,
            supervisor,
            name: `Cópia de ${supervisor.name}`.slice(0, 80),
            copyDistribution: true,
            submitting: false,
            error: null,
        });
    }

    async function confirmDuplicate() {
        if (!duplicateModal.open) return;

        const nextName = duplicateModal.name.trim();
        if (nextName.length < 3 || nextName.length > 80) {
            setDuplicateModal((current) => current.open ? { ...current, error: "Informe um nome entre 3 e 80 caracteres." } : current);
            return;
        }

        setDuplicateModal((current) => current.open ? { ...current, submitting: true, error: null } : current);

        try {
            const source = duplicateModal.supervisor;
            const created = await createSupervisor({
                name: nextName,
                communicationStyle: source.communicationStyle,
                profile: source.profile,
                objective: source.objective,
                reasoningModelVersion: source.reasoningModelVersion,
                provider: source.provider,
                model: source.model,
                humanHandoffEnabled: source.humanHandoffEnabled,
                notifyContactOnAgentTransfer: source.notifyContactOnAgentTransfer,
                humanHandoffTeam: source.humanHandoffTeam,
                humanHandoffSendMessage: source.humanHandoffSendMessage,
                humanHandoffMessage: source.humanHandoffMessage,
                agentIssueHandoffTeam: source.agentIssueHandoffTeam,
                agentIssueSendMessage: source.agentIssueSendMessage,
                humanUserChoiceEnabled: source.humanUserChoiceEnabled,
                humanChoiceOptions: source.humanChoiceOptions,
                enabled: source.enabled,
                defaultForCompany: false,
            });

            if (duplicateModal.copyDistribution) {
                const distributionPayload: AiSupervisorDistributionPayload = {
                    otherRules: source.distribution.otherRules,
                    agents: source.distribution.agents.map((rule) => ({
                        agentId: rule.agentId,
                        enabled: rule.enabled,
                        triageText: rule.triageText,
                    })),
                };
                await updateSupervisorDistribution(created.id, distributionPayload);
            }

            pushToast("Supervisor duplicado com sucesso.", "success");
            setDuplicateModal({ open: false });
            await loadData();
            router.push(`${BASE_ROUTE}/${created.id}`);
        } catch (duplicateError) {
            setDuplicateModal((current) => current.open ? { ...current, submitting: false, error: getFriendlyHttpErrorMessage(duplicateError, "Não foi possível duplicar o supervisor.") } : current);
        }
    }

    return (
        <>
            <SupervisorBreadcrumbs
                items={[
                    { label: "IA", href: "/protected/agentes-ia" },
                    { label: "Supervisores" },
                ]}
            />

            <PageShell
                title="Supervisores"
                description="Gerencie os roteadores de triagem inicial, seus modelos e como eles encaminham conversas para agentes ou para handoff humano."
                actions={(
                    <button
                        type="button"
                        onClick={() => router.push(`${BASE_ROUTE}/novo`)}
                        className="h-11 rounded-xl bg-io-purple px-4 text-sm font-semibold text-white transition hover:brightness-110"
                    >
                        Novo Supervisor
                    </button>
                )}
            >
                <SectionCard
                    title="Lista de supervisores"
                    description="Busque por nome, revise status e abra a configuração de distribuição de cada supervisor."
                    actions={(
                        <div className="flex w-full max-w-sm items-center gap-2">
                            <input
                                value={search}
                                onChange={(event) => setSearch(event.target.value)}
                                placeholder="Buscar por nome"
                                className="h-11 w-full rounded-xl border border-black/10 px-3 text-sm outline-none transition focus:border-io-purple focus:ring-2 focus:ring-io-purple/10"
                                aria-label="Buscar supervisor por nome"
                            />
                        </div>
                    )}
                >
                    {loading ? (
                        <div className="rounded-2xl border border-black/5 bg-black/[0.015] p-5 text-sm text-black/60">Carregando supervisores...</div>
                    ) : error ? (
                        <ErrorState message={error} onRetry={loadData} />
                    ) : !supervisors.length ? (
                        <EmptyState
                            title="Nenhum supervisor cadastrado"
                            description="Crie o primeiro supervisor para habilitar triagem inteligente e distribuição para agentes especializados."
                            action={(
                                <button
                                    type="button"
                                    onClick={() => router.push(`${BASE_ROUTE}/novo`)}
                                    className="rounded-xl bg-io-purple px-4 py-2 text-sm font-semibold text-white transition hover:brightness-110"
                                >
                                    Criar supervisor
                                </button>
                            )}
                        />
                    ) : !filteredSupervisors.length ? (
                        <EmptyState
                            title="Nenhum resultado encontrado"
                            description="Tente outro termo de busca ou limpe o filtro para ver todos os supervisores."
                        />
                    ) : (
                        <div className="overflow-hidden rounded-2xl border border-black/10">
                            <div className="overflow-x-auto">
                                <table className="min-w-full divide-y divide-black/10 text-sm">
                                    <thead className="bg-black/[0.03] text-left text-black/60">
                                        <tr>
                                            <th className="px-4 py-3 font-medium">Nome</th>
                                            <th className="px-4 py-3 font-medium">Status</th>
                                            <th className="px-4 py-3 font-medium">Provedor / Modelo</th>
                                            <th className="px-4 py-3 font-medium">Handoff humano</th>
                                            <th className="px-4 py-3 font-medium">Atualizado em</th>
                                            <th className="px-4 py-3 font-medium">Ações</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-black/5 bg-white">
                                        {currentItems.map((supervisor) => (
                                            <tr key={supervisor.id} className="align-top">
                                                <td className="px-4 py-4">
                                                    <div className="space-y-2">
                                                        <div className="flex flex-wrap items-center gap-2">
                                                            <span className="font-semibold text-io-dark">{supervisor.name}</span>
                                                            {supervisor.defaultForCompany ? <StatusPill tone="info">Padrão da empresa</StatusPill> : null}
                                                        </div>
                                                        <p className="max-w-md text-xs text-black/55">
                                                            {supervisor.objective || "Sem objetivo descrito."}
                                                        </p>
                                                    </div>
                                                </td>
                                                <td className="px-4 py-4">
                                                    <StatusPill tone={supervisor.enabled ? "success" : "default"}>
                                                        {supervisor.enabled ? "Ativo" : "Inativo"}
                                                    </StatusPill>
                                                </td>
                                                <td className="px-4 py-4 text-black/65">
                                                    <div className="font-medium text-io-dark">{supervisor.provider || "-"}</div>
                                                    <div className="mt-1 text-xs">{supervisor.model || "Modelo não informado"}</div>
                                                </td>
                                                <td className="px-4 py-4">
                                                    <StatusPill tone={supervisor.humanHandoffEnabled ? "warning" : "default"}>
                                                        {supervisor.humanHandoffEnabled ? "Sim" : "Não"}
                                                    </StatusPill>
                                                </td>
                                                <td className="px-4 py-4 text-black/65">{formatDateTime(supervisor.updatedAt)}</td>
                                                <td className="px-4 py-4">
                                                    <div className="flex flex-wrap gap-2">
                                                        <button
                                                            type="button"
                                                            onClick={() => router.push(`${BASE_ROUTE}/${supervisor.id}`)}
                                                            className="rounded-xl border border-black/10 px-3 py-2 text-xs font-semibold text-black/70 transition hover:bg-black/5"
                                                        >
                                                            Editar
                                                        </button>
                                                        <button
                                                            type="button"
                                                            onClick={() => openDuplicateModal(supervisor)}
                                                            className="rounded-xl border border-black/10 px-3 py-2 text-xs font-semibold text-black/70 transition hover:bg-black/5"
                                                        >
                                                            Duplicar
                                                        </button>
                                                        <button
                                                            type="button"
                                                            onClick={() => handleToggle(supervisor)}
                                                            className="rounded-xl border border-black/10 px-3 py-2 text-xs font-semibold text-black/70 transition hover:bg-black/5"
                                                        >
                                                            {supervisor.enabled ? "Desativar" : "Ativar"}
                                                        </button>
                                                        <button
                                                            type="button"
                                                            onClick={() => router.push(`${BASE_ROUTE}/${supervisor.id}/distribuicao`)}
                                                            className="rounded-xl border border-io-purple/25 bg-io-purple/10 px-3 py-2 text-xs font-semibold text-io-purple transition hover:bg-io-purple/15"
                                                        >
                                                            Abrir distribuição
                                                        </button>
                                                    </div>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>

                            {totalPages > 1 ? (
                                <div className="flex flex-col gap-3 border-t border-black/10 bg-black/[0.02] px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                                    <p className="text-xs text-black/55">
                                        Exibindo {currentItems.length} de {filteredSupervisors.length} supervisores
                                    </p>
                                    <div className="flex items-center gap-2">
                                        <button
                                            type="button"
                                            onClick={() => setPage((current) => Math.max(1, current - 1))}
                                            disabled={currentPage === 1}
                                            className="rounded-xl border border-black/10 px-3 py-2 text-xs font-semibold text-black/70 transition hover:bg-black/5 disabled:cursor-not-allowed disabled:opacity-50"
                                        >
                                            Anterior
                                        </button>
                                        <span className="text-xs text-black/55">
                                            Página {currentPage} de {totalPages}
                                        </span>
                                        <button
                                            type="button"
                                            onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
                                            disabled={currentPage === totalPages}
                                            className="rounded-xl border border-black/10 px-3 py-2 text-xs font-semibold text-black/70 transition hover:bg-black/5 disabled:cursor-not-allowed disabled:opacity-50"
                                        >
                                            Próxima
                                        </button>
                                    </div>
                                </div>
                            ) : null}
                        </div>
                    )}
                </SectionCard>
            </PageShell>

            {duplicateModal.open ? (
                <ModalFrame
                    title="Duplicar supervisor"
                    description="Crie uma nova configuração a partir do supervisor atual, com opção de copiar a lógica de distribuição."
                    onClose={() => {
                        if (duplicateModal.submitting) return;
                        setDuplicateModal({ open: false });
                    }}
                >
                    <div className="space-y-4">
                        <div>
                            <label htmlFor="duplicate-name" className="block text-sm font-medium text-io-dark">
                                Nome do novo supervisor
                            </label>
                            <input
                                id="duplicate-name"
                                value={duplicateModal.name}
                                onChange={(event) => setDuplicateModal((current) => current.open ? { ...current, name: event.target.value, error: null } : current)}
                                className="mt-2 h-11 w-full rounded-xl border border-black/10 px-3 text-sm outline-none transition focus:border-io-purple focus:ring-2 focus:ring-io-purple/10"
                            />
                        </div>

                        <label className="flex items-center gap-3 rounded-2xl border border-black/10 px-4 py-3 text-sm text-black/70">
                            <input
                                type="checkbox"
                                checked={duplicateModal.copyDistribution}
                                onChange={(event) => setDuplicateModal((current) => current.open ? { ...current, copyDistribution: event.target.checked } : current)}
                                className="h-4 w-4 rounded border-black/20 text-io-purple focus:ring-io-purple"
                            />
                            <span>Duplicar lógica de distribuição</span>
                        </label>

                        {duplicateModal.error ? (
                            <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                                {duplicateModal.error}
                            </div>
                        ) : null}

                        <div className="flex justify-end gap-2">
                            <button
                                type="button"
                                onClick={() => setDuplicateModal({ open: false })}
                                disabled={duplicateModal.submitting}
                                className="h-11 rounded-xl border border-black/10 px-4 text-sm font-medium text-black/65 transition hover:bg-black/5 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                Cancelar
                            </button>
                            <button
                                type="button"
                                onClick={confirmDuplicate}
                                disabled={duplicateModal.submitting}
                                className="h-11 rounded-xl bg-io-purple px-4 text-sm font-semibold text-white transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                {duplicateModal.submitting ? "Duplicando..." : "Confirmar duplicação"}
                            </button>
                        </div>
                    </div>
                </ModalFrame>
            ) : null}

            <ToastStack items={toasts} onDismiss={dismissToast} />
        </>
    );
}
