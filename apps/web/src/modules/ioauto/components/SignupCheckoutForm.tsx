"use client";

import { useState } from "react";
import { ArrowRight, LoaderCircle } from "lucide-react";

type SignupCheckoutFormProps = {
    compact?: boolean;
};

export function SignupCheckoutForm({ compact = false }: SignupCheckoutFormProps) {
    const [form, setForm] = useState({
        ownerFullName: "",
        companyName: "",
        email: "",
        password: "",
    });
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);

    async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setLoading(true);
        setError(null);

        const response = await fetch("/api/public/signup/checkout", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(form),
        });

        if (!response.ok) {
            const payload = await response.json().catch(() => ({ message: "Falha ao iniciar o checkout." }));
            setError(payload.message ?? "Falha ao iniciar o checkout.");
            setLoading(false);
            return;
        }

        const payload = (await response.json()) as { checkoutUrl: string };
        window.location.assign(payload.checkoutUrl);
    }

    return (
        <form onSubmit={handleSubmit} className={`grid gap-4 rounded-[32px] border border-black/10 bg-white p-6 shadow-[0_18px_45px_rgba(0,0,0,0.08)] ${compact ? "" : "max-w-xl"}`}>
            <div>
                <p className="text-xs uppercase tracking-[0.28em] text-black/40">Assinatura imediata</p>
                <h2 className="mt-3 font-display text-3xl font-bold text-io-dark">Crie sua operação IOAuto e siga para o checkout</h2>
                <p className="mt-2 text-sm leading-7 text-black/55">
                    O tenant é criado automaticamente após a confirmação do pagamento. Use estes dados depois para entrar na plataforma.
                </p>
            </div>

            {error ? <p className="rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p> : null}

            <div className="grid gap-4 md:grid-cols-2">
                <Field label="Responsável" value={form.ownerFullName} onChange={(value) => setForm((current) => ({ ...current, ownerFullName: value }))} />
                <Field label="Empresa / Loja" value={form.companyName} onChange={(value) => setForm((current) => ({ ...current, companyName: value }))} />
                <Field label="E-mail" value={form.email} onChange={(value) => setForm((current) => ({ ...current, email: value }))} type="email" />
                <Field label="Senha inicial" value={form.password} onChange={(value) => setForm((current) => ({ ...current, password: value }))} type="password" className="md:col-span-2" />
            </div>

            <button
                type="submit"
                disabled={loading}
                className="inline-flex items-center justify-center gap-2 rounded-full bg-black px-5 py-3 text-sm font-semibold text-white transition hover:bg-black/85 disabled:cursor-not-allowed disabled:bg-black/20"
            >
                {loading ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <ArrowRight className="h-4 w-4" />}
                Ir para o checkout recorrente
            </button>
        </form>
    );
}

function Field({
    label,
    value,
    onChange,
    type = "text",
    className = "",
}: {
    label: string;
    value: string;
    onChange: (value: string) => void;
    type?: string;
    className?: string;
}) {
    return (
        <label className={`grid gap-2 ${className}`}>
            <span className="text-sm font-medium text-black/60">{label}</span>
            <input
                type={type}
                value={value}
                onChange={(event) => onChange(event.target.value)}
                className="h-12 rounded-2xl border border-black/10 bg-[#f7f7f7] px-4 text-sm text-io-dark outline-none transition focus:border-black/30 focus:bg-white"
                required
            />
        </label>
    );
}
