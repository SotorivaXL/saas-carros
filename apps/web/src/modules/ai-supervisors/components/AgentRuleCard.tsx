"use client";

import { useState } from "react";
import { FieldError, InlineToggle, StatusPill } from "./SupervisorUi";

export type AgentRuleDraft = {
    agentId: string;
    agentName: string;
    isActive: boolean;
    enabled: boolean;
    triageText: string;
};

type AgentRuleCardProps = {
    rule: AgentRuleDraft;
    error?: string | null;
    onChange: (next: AgentRuleDraft) => void;
    onRemove: () => void;
};

export function AgentRuleCard({
    rule,
    error,
    onChange,
    onRemove,
}: AgentRuleCardProps) {
    const [expanded, setExpanded] = useState(true);

    return (
        <article className="rounded-2xl border border-black/10 bg-white p-4 shadow-sm">
            <div className="flex flex-col gap-3 border-b border-black/5 pb-3 lg:flex-row lg:items-start lg:justify-between">
                <div className="space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-base font-semibold text-io-dark">{rule.agentName}</h3>
                        <StatusPill tone={rule.enabled ? "success" : "default"}>{rule.enabled ? "Ativo na triagem" : "Desativado"}</StatusPill>
                        {!rule.isActive ? <StatusPill tone="warning">Agente inativo</StatusPill> : null}
                    </div>
                    <p className="text-xs text-black/55">ID do agente: {rule.agentId}</p>
                    {!expanded && rule.triageText ? (
                        <p className="max-w-3xl text-sm text-black/60">
                            {rule.triageText.length > 180 ? `${rule.triageText.slice(0, 180)}...` : rule.triageText}
                        </p>
                    ) : null}
                </div>

                <div className="flex flex-wrap items-center gap-2">
                    <button type="button" onClick={() => setExpanded((value) => !value)} className="rounded-xl border border-black/10 px-3 py-2 text-sm text-black/65 transition hover:bg-black/5">
                        {expanded ? "Recolher" : "Expandir"}
                    </button>
                    <button type="button" onClick={onRemove} className="rounded-xl border border-red-200 px-3 py-2 text-sm font-medium text-red-700 transition hover:bg-red-50">
                        Remover
                    </button>
                </div>
            </div>

            <div className="mt-4">
                <InlineToggle
                    checked={rule.enabled}
                    onChange={(checked) => onChange({ ...rule, enabled: checked })}
                    label="Usar este agente na distribuição"
                    description="Se desativado, ele permanece listado, mas fica fora da triagem."
                />
            </div>

            {expanded ? (
                <div className="mt-4 grid gap-4">
                    <div>
                        <div className="mb-2 flex items-start justify-between gap-3">
                            <label htmlFor={`triage-${rule.agentId}`} className="text-sm font-medium text-io-dark">
                                Texto de triagem {rule.enabled ? <span className="text-red-600">*</span> : null}
                            </label>
                            <span className="text-xs text-black/45">{rule.triageText.length}/800</span>
                        </div>
                        <textarea
                            id={`triage-${rule.agentId}`}
                            rows={5}
                            maxLength={800}
                            value={rule.triageText}
                            onChange={(event) => onChange({ ...rule, triageText: event.target.value })}
                            placeholder="Descreva em poucas linhas quando esse agente deve receber a conversa e o que ele resolve melhor."
                            className="w-full rounded-2xl border border-black/10 px-3 py-3 text-sm outline-none transition focus:border-io-purple focus:ring-2 focus:ring-io-purple/10"
                        />
                        <FieldError message={error} />
                    </div>
                </div>
            ) : null}
        </article>
    );
}
