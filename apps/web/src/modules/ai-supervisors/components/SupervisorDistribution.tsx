"use client";

import { useDeferredValue, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useFieldArray, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { AgentRuleCard, type AgentRuleDraft } from "./AgentRuleCard";
import { EmptyState, ErrorState, FieldError, FieldLabel, LoadingState, PageShell, SectionCard, StatusPill, SupervisorBreadcrumbs, ToastStack, type ToastMessage } from "./SupervisorUi";
import { getFriendlyHttpErrorMessage, getSupervisor, listAgentsForDistribution, simulateSupervisorRouting, updateSupervisorDistribution, type AiSupervisor, type AiSupervisorDistributionPayload, type DistributionAgentOption, type SupervisorSimulationResponse } from "@/services/aiSupervisors";

const BASE_ROUTE = "/protected/ai/supervisores";

const distributionSchema = z.object({
    otherRules: z.string().max(2000, "Use no máximo 2000 caracteres."),
    agents: z.array(z.object({
        agentId: z.string().min(1),
        agentName: z.string().min(1),
        isActive: z.boolean(),
        enabled: z.boolean(),
        triageText: z.string().max(800, "Use no máximo 800 caracteres."),
    })).superRefine((agents, ctx) => {
        agents.forEach((agent, index) => {
            if (agent.enabled && !agent.triageText.trim()) {
                ctx.addIssue({
                    code: z.ZodIssueCode.custom,
                    message: "Preencha o texto de triagem para agentes habilitados.",
                    path: [index, "triageText"],
                });
            }
        });
    }),
});

type DistributionFormValues = z.infer<typeof distributionSchema>;

function getToastId() {
    return Date.now() + Math.floor(Math.random() * 1000);
}

function buildInitialValues(supervisor: AiSupervisor, agents: DistributionAgentOption[]): DistributionFormValues {
    const agentMap = new Map(agents.map((agent) => [agent.id, agent]));
    return {
        otherRules: supervisor.distribution.otherRules ?? "",
        agents: supervisor.distribution.agents.map((rule) => ({
            agentId: rule.agentId,
            agentName: rule.agentName,
            isActive: agentMap.get(rule.agentId)?.isActive ?? true,
            enabled: rule.enabled,
            triageText: rule.triageText,
        })),
    };
}

function buildPayload(values: DistributionFormValues): AiSupervisorDistributionPayload {
    return {
        otherRules: values.otherRules.trim(),
        agents: values.agents.map((agent) => ({
            agentId: agent.agentId,
            enabled: agent.enabled,
            triageText: agent.triageText.trim(),
        })),
    };
}

function formatSimulationAction(action: string) {
    if (action === "ASSIGN_AGENT") return "ASSIGN_AGENT";
    if (action === "ASK_CLARIFYING") return "ASK_CLARIFYING";
    if (action === "HANDOFF_HUMAN") return "HANDOFF_HUMAN";
    return "NO_ACTION";
}

export function SupervisorDistribution({ supervisorId }: { supervisorId: string }) {
    const router = useRouter();
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [supervisor, setSupervisor] = useState<AiSupervisor | null>(null);
    const [availableAgents, setAvailableAgents] = useState<DistributionAgentOption[]>([]);
    const [agentSearch, setAgentSearch] = useState("");
    const [toasts, setToasts] = useState<ToastMessage[]>([]);
    const [submitting, setSubmitting] = useState(false);
    const [simulateInput, setSimulateInput] = useState("");
    const [simulateLoading, setSimulateLoading] = useState(false);
    const [simulateResult, setSimulateResult] = useState<SupervisorSimulationResponse | null>(null);

    const form = useForm<DistributionFormValues>({
        resolver: zodResolver(distributionSchema),
        mode: "onChange",
        defaultValues: {
            otherRules: "",
            agents: [],
        },
    });

    const fieldArray = useFieldArray({
        control: form.control,
        name: "agents",
    });

    const watchedAgents = form.watch("agents");
    const otherRulesLength = form.watch("otherRules").length;
    const deferredAgentSearch = useDeferredValue(agentSearch);
    const selectedAgentIds = new Set(watchedAgents.map((item) => item.agentId));
    const filteredAvailableAgents = availableAgents.filter((agent) => {
        if (selectedAgentIds.has(agent.id)) return false;
        if (!deferredAgentSearch.trim()) return true;
        const haystack = `${agent.name} ${agent.reasoningModel} ${agent.modelVersion}`.toLowerCase();
        return haystack.includes(deferredAgentSearch.trim().toLowerCase());
    });
    const orderedRules = (watchedAgents as AgentRuleDraft[]).map((rule, index) => ({ rule, index }));
    const hasEnabledRuleWithoutText = watchedAgents.some((agent) => agent.enabled && !agent.triageText.trim());
    const unavailableSimulationMessage =
        simulateResult && "message" in simulateResult
            ? simulateResult.message
            : null;

    useEffect(() => {
        let active = true;

        async function load() {
            setLoading(true);
            setError(null);
            try {
                const [supervisorData, agents] = await Promise.all([
                    getSupervisor(supervisorId),
                    listAgentsForDistribution(),
                ]);
                if (!active) return;
                setSupervisor(supervisorData);
                setAvailableAgents(agents);
                form.reset(buildInitialValues(supervisorData, agents));
            } catch (loadError) {
                if (!active) return;
                setError(getFriendlyHttpErrorMessage(loadError, "Não foi possível carregar a distribuição do supervisor."));
            } finally {
                if (active) setLoading(false);
            }
        }

        load();
        return () => {
            active = false;
        };
    }, [form, supervisorId]);

    useEffect(() => {
        if (!form.formState.isDirty) return;
        const handler = (event: BeforeUnloadEvent) => {
            event.preventDefault();
            event.returnValue = "";
        };
        window.addEventListener("beforeunload", handler);
        return () => window.removeEventListener("beforeunload", handler);
    }, [form.formState.isDirty]);

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

    function confirmLeave() {
        if (!form.formState.isDirty) return true;
        return window.confirm("Você tem alterações não salvas. Deseja sair mesmo assim?");
    }

    function addAgent(agent: DistributionAgentOption) {
        fieldArray.append({
            agentId: agent.id,
            agentName: agent.name,
            isActive: agent.isActive,
            enabled: agent.isActive,
            triageText: "",
        });
    }

    function updateRule(index: number, nextRule: AgentRuleDraft) {
        fieldArray.update(index, nextRule);
    }

    async function handleSave() {
        await form.handleSubmit(async (values) => {
            setSubmitting(true);
            try {
                const payload = buildPayload(values);
                const distribution = await updateSupervisorDistribution(supervisorId, payload);
                const nextSupervisor = supervisor
                    ? { ...supervisor, distribution }
                    : supervisor;
                if (nextSupervisor) setSupervisor(nextSupervisor);
                form.reset({
                    otherRules: distribution.otherRules,
                    agents: distribution.agents.map((rule) => ({
                        agentId: rule.agentId,
                        agentName: rule.agentName,
                        isActive: availableAgents.find((agent) => agent.id === rule.agentId)?.isActive ?? true,
                        enabled: rule.enabled,
                        triageText: rule.triageText,
                    })),
                });
                pushToast("Distribuição salva com sucesso.", "success");
            } catch (saveError) {
                pushToast(getFriendlyHttpErrorMessage(saveError, "Não foi possível salvar a distribuição."), "error");
            } finally {
                setSubmitting(false);
            }
        })();
    }

    async function handleSimulate() {
        setSimulateLoading(true);
        try {
            const result = await simulateSupervisorRouting(supervisorId, simulateInput, {
                distribution: buildPayload(form.getValues()),
            });
            setSimulateResult(result);
        } finally {
            setSimulateLoading(false);
        }
    }

    if (loading) {
        return <LoadingState label="Carregando lógica de distribuição..." />;
    }

    if (error) {
        return <ErrorState message={error} onRetry={() => window.location.reload()} />;
    }

    if (!supervisor) {
        return (
            <EmptyState
                title="Supervisor não encontrado"
                description="Não foi possível carregar este supervisor. Volte para a lista e tente novamente."
                action={(
                    <button
                        type="button"
                        onClick={() => router.push(BASE_ROUTE)}
                        className="rounded-xl bg-io-purple px-4 py-2 text-sm font-semibold text-white transition hover:brightness-110"
                    >
                        Voltar para supervisores
                    </button>
                )}
            />
        );
    }

    return (
        <>
            <SupervisorBreadcrumbs
                items={[
                    { label: "IA", href: "/protected/agentes-ia" },
                    { label: "Supervisores", href: BASE_ROUTE },
                    { label: supervisor.name, href: `${BASE_ROUTE}/${supervisor.id}` },
                    { label: "Distribuição" },
                ]}
            />

            <PageShell
                title={`Distribuição: ${supervisor.name}`}
                description="Selecione quais agentes entram na triagem, escreva textos de triagem objetivos e valide o comportamento esperado."
                actions={(
                    <>
                        <button
                            type="button"
                            onClick={() => {
                                if (!confirmLeave()) return;
                                router.push(`${BASE_ROUTE}/${supervisor.id}`);
                            }}
                            className="h-11 rounded-xl border border-black/10 px-4 text-sm font-medium text-black/65 transition hover:bg-black/5"
                        >
                            Voltar
                        </button>
                        <button
                            type="button"
                            onClick={handleSave}
                            disabled={submitting || hasEnabledRuleWithoutText}
                            className="h-11 rounded-xl bg-io-purple px-4 text-sm font-semibold text-white transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                            {submitting ? "Salvando..." : "Salvar distribuição"}
                        </button>
                    </>
                )}
            >
                <div className="grid gap-5 xl:grid-cols-[1.35fr_0.95fr]">
                    <div className="space-y-5">
                        <SectionCard
                            title="Agentes envolvidos"
                            description="Adicione agentes ao roteamento e descreva com clareza o papel de cada agente."
                        >
                            <div className="rounded-2xl border border-black/10 bg-black/[0.02] p-4">
                                <div className="flex flex-col gap-3 sm:flex-row">
                                    <input
                                        value={agentSearch}
                                        onChange={(event) => setAgentSearch(event.target.value)}
                                        placeholder="Buscar agentes disponíveis"
                                        className="h-11 flex-1 rounded-xl border border-black/10 bg-white px-3 text-sm outline-none transition focus:border-io-purple focus:ring-2 focus:ring-io-purple/10"
                                    />
                                    <div className="flex items-center text-xs text-black/50">
                                        {filteredAvailableAgents.length} disponíveis
                                    </div>
                                </div>

                                {filteredAvailableAgents.length ? (
                                    <div className="mt-4 flex max-h-56 flex-wrap gap-2 overflow-auto">
                                        {filteredAvailableAgents.map((agent) => (
                                            <button
                                                key={agent.id}
                                                type="button"
                                                onClick={() => addAgent(agent)}
                                                className={`rounded-full border px-3 py-2 text-sm transition ${agent.isActive ? "border-io-purple/25 bg-io-purple/10 text-io-purple hover:bg-io-purple/15" : "border-amber-200 bg-amber-50 text-amber-700 hover:bg-amber-100"}`}
                                            >
                                                + {agent.name}
                                            </button>
                                        ))}
                                    </div>
                                ) : (
                                    <p className="mt-4 text-sm text-black/55">
                                        {availableAgents.length ? "Todos os agentes encontrados já foram adicionados." : "Nenhum agente disponível foi encontrado no backend."}
                                    </p>
                                )}
                            </div>

                            {form.formState.errors.agents?.message ? <FieldError message={form.formState.errors.agents.message as string} /> : null}

                            <div className="mt-5 space-y-4">
                                {!orderedRules.length ? (
                                    <EmptyState
                                        title="Nenhum agente selecionado"
                                        description="Adicione ao menos um agente para configurar a lógica de distribuição deste supervisor."
                                    />
                                ) : (
                                    orderedRules.map(({ rule, index }) => (
                                        <AgentRuleCard
                                            key={fieldArray.fields[index]?.id ?? rule.agentId}
                                            rule={rule}
                                            error={form.formState.errors.agents?.[index]?.triageText?.message}
                                            onChange={(nextRule) => updateRule(index, nextRule)}
                                            onRemove={() => fieldArray.remove(index)}
                                        />
                                    ))
                                )}
                            </div>
                        </SectionCard>
                    </div>

                    <div className="space-y-5">
                        <SectionCard
                            title="Outras regras ou restrições"
                            description="Estas instruções complementam os textos de triagem dos agentes e ajudam o supervisor a respeitar regras do negócio."
                        >
                            <FieldLabel
                                htmlFor="other-rules"
                                label="Regras complementares"
                                description="Use instruções curtas, claras e testáveis."
                                trailing={`${otherRulesLength}/2000`}
                            />
                            <textarea
                                id="other-rules"
                                rows={9}
                                maxLength={2000}
                                {...form.register("otherRules")}
                                placeholder="Ex.: Nunca oferecer preço. Se mencionar suporte técnico, priorizar Agente Suporte."
                                className="w-full rounded-2xl border border-black/10 px-3 py-3 text-sm outline-none transition focus:border-io-purple focus:ring-2 focus:ring-io-purple/10"
                            />
                            <FieldError message={form.formState.errors.otherRules?.message} />

                            <div className="mt-4 rounded-2xl border border-black/10 bg-black/[0.02] p-4">
                                <p className="text-sm font-medium text-io-dark">Exemplos úteis</p>
                                <ul className="mt-3 space-y-2 text-sm text-black/60">
                                    <li>Nunca oferecer preço sem contexto completo.</li>
                                    <li>Se mencionar suporte técnico, direcionar para Agente Suporte.</li>
                                    <li>Se for reclamação agressiva, solicitar handoff humano.</li>
                                    <li>Se pedir cancelamento, priorizar Agente Retenção.</li>
                                </ul>
                            </div>
                        </SectionCard>

                        <SectionCard title="Resumo da configuração" description="Visão rápida do estado atual da triagem deste supervisor.">
                            <div className="grid gap-3 text-sm">
                                <div className="flex items-center justify-between rounded-2xl border border-black/10 px-4 py-3">
                                    <span className="text-black/60">Agentes selecionados</span>
                                    <span className="font-semibold text-io-dark">{watchedAgents.length}</span>
                                </div>
                                <div className="flex items-center justify-between rounded-2xl border border-black/10 px-4 py-3">
                                    <span className="text-black/60">Agentes habilitados</span>
                                    <span className="font-semibold text-io-dark">{watchedAgents.filter((item) => item.enabled).length}</span>
                                </div>
                                <div className="flex items-center justify-between rounded-2xl border border-black/10 px-4 py-3">
                                    <span className="text-black/60">Transferência humana</span>
                                    <StatusPill tone={supervisor.humanHandoffEnabled ? "warning" : "default"}>
                                        {supervisor.humanHandoffEnabled ? "Ativa" : "Desligada"}
                                    </StatusPill>
                                </div>
                                <div className="flex items-center justify-between rounded-2xl border border-black/10 px-4 py-3">
                                    <span className="text-black/60">Usuário de handoff humano</span>
                                    <span className="font-semibold text-io-dark">{supervisor.humanHandoffTeam || "-"}</span>
                                </div>
                                <div className="flex items-center justify-between rounded-2xl border border-black/10 px-4 py-3">
                                    <span className="text-black/60">Mensagem de transferência</span>
                                    <StatusPill tone={supervisor.humanHandoffSendMessage ? "info" : "default"}>
                                        {supervisor.humanHandoffSendMessage ? "Ativa" : "Desligada"}
                                    </StatusPill>
                                </div>
                                <div className="flex items-center justify-between rounded-2xl border border-black/10 px-4 py-3">
                                    <span className="text-black/60">Supervisor padrão</span>
                                    <StatusPill tone={supervisor.defaultForCompany ? "success" : "default"}>
                                        {supervisor.defaultForCompany ? "Sim" : "Não"}
                                    </StatusPill>
                                </div>
                            </div>
                        </SectionCard>

                        <SectionCard
                            title="Teste de triagem"
                            description="Use uma mensagem curta de lead para conferir o comportamento esperado. A simulação não salva alterações."
                        >
                            <FieldLabel
                                htmlFor="simulate-message"
                                label="Mensagem do lead"
                                description="Se o backend ainda não expuser a simulação, o card mostra indisponibilidade ou usa o mock local, quando habilitado."
                            />
                            <textarea
                                id="simulate-message"
                                rows={4}
                                value={simulateInput}
                                onChange={(event) => setSimulateInput(event.target.value)}
                                placeholder="Ex.: Oi, preciso de ajuda com a integração e estou sem acesso."
                                className="w-full rounded-2xl border border-black/10 px-3 py-3 text-sm outline-none transition focus:border-io-purple focus:ring-2 focus:ring-io-purple/10"
                            />

                            <div className="mt-4 flex justify-end">
                                <button
                                    type="button"
                                    onClick={handleSimulate}
                                    disabled={simulateLoading || !simulateInput.trim()}
                                    className="h-11 rounded-xl bg-io-purple px-4 text-sm font-semibold text-white transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-60"
                                >
                                    {simulateLoading ? "Simulando..." : "Simular"}
                                </button>
                            </div>

                            {simulateResult ? (
                                simulateResult.available ? (
                                    <div className="mt-4 rounded-2xl border border-black/10 bg-black/[0.02] p-4">
                                        <div className="flex flex-wrap items-center gap-2">
                                            <StatusPill tone="info">{formatSimulationAction(simulateResult.result.action)}</StatusPill>
                                            <StatusPill tone={simulateResult.source === "mock" ? "warning" : "success"}>
                                                {simulateResult.source === "mock" ? "Mock local" : "Backend"}
                                            </StatusPill>
                                        </div>
                                        <div className="mt-4 grid gap-3 text-sm">
                                            <div className="rounded-2xl border border-black/10 px-4 py-3">
                                                <p className="text-xs uppercase tracking-wide text-black/45">Agente destino</p>
                                                <p className="mt-1 font-semibold text-io-dark">{simulateResult.result.targetAgentName ?? simulateResult.result.targetAgentId ?? "-"}</p>
                                            </div>
                                            <div className="rounded-2xl border border-black/10 px-4 py-3">
                                                <p className="text-xs uppercase tracking-wide text-black/45">Mensagem sugerida</p>
                                                <p className="mt-1 text-black/70">{simulateResult.result.messageToSend ?? "-"}</p>
                                            </div>
                                            <div className="grid gap-3 sm:grid-cols-2">
                                                <div className="rounded-2xl border border-black/10 px-4 py-3">
                                                    <p className="text-xs uppercase tracking-wide text-black/45">Confiança</p>
                                                    <p className="mt-1 font-semibold text-io-dark">{Math.round(simulateResult.result.confidence * 100)}%</p>
                                                </div>
                                                <div className="rounded-2xl border border-black/10 px-4 py-3">
                                                    <p className="text-xs uppercase tracking-wide text-black/45">Fila humana</p>
                                                    <p className="mt-1 font-semibold text-io-dark">{simulateResult.result.humanQueue ?? "-"}</p>
                                                </div>
                                            </div>
                                            <div className="rounded-2xl border border-black/10 px-4 py-3">
                                                <p className="text-xs uppercase tracking-wide text-black/45">Motivo</p>
                                                <p className="mt-1 text-black/70">{simulateResult.result.reason || "-"}</p>
                                            </div>
                                            <div className="rounded-2xl border border-black/10 px-4 py-3">
                                                    <p className="text-xs uppercase tracking-wide text-black/45">Evidências</p>
                                                <p className="mt-1 text-black/70">
                                                    {simulateResult.result.evidence.length
                                                        ? simulateResult.result.evidence.join(", ")
                                                        : "-"}
                                                </p>
                                            </div>
                                        </div>
                                    </div>
                                ) : (
                                    <div className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
                                        {unavailableSimulationMessage}
                                    </div>
                                )
                            ) : null}
                        </SectionCard>
                    </div>
                </div>
            </PageShell>

            <ToastStack items={toasts} onDismiss={dismissToast} />
        </>
    );
}
