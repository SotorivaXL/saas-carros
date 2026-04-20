"use client";

import type { InputHTMLAttributes } from "react";
import { useMemo, useState } from "react";
import { ArrowRight, LoaderCircle, ShieldCheck } from "lucide-react";

type SignupCheckoutFormProps = {
    compact?: boolean;
};

type CheckoutLaunch = {
    intentId: string;
    checkoutUrl: string;
    checkoutId?: string;
};

export function SignupCheckoutForm({ compact = false }: SignupCheckoutFormProps) {
    const [form, setForm] = useState({
        ownerFullName: "",
        companyName: "",
        email: "",
        phone: "",
    });
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);

    const helperText = useMemo(() => {
        return "Depois desse cadastro rapido voce segue para o checkout hospedado do Asaas para concluir a assinatura.";
    }, []);

    async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setLoading(true);
        setError(null);

        const response = await fetch("/api/public/signup/checkout", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                ...form,
                phone: normalizePhone(form.phone),
            }),
        });

        if (!response.ok) {
            const payload = await response.json().catch(() => ({ message: "Falha ao iniciar o checkout." }));
            setError(payload.message ?? "Falha ao iniciar o checkout.");
            setLoading(false);
            return;
        }

        const payload = (await response.json()) as CheckoutLaunch;
        window.location.assign(payload.checkoutUrl);
    }

    return (
        <form
            onSubmit={handleSubmit}
            className={`grid gap-5 rounded-[34px] border border-[#6b00e3]/12 bg-white p-6 shadow-[0_20px_55px_rgba(90,10,160,0.10)] ${compact ? "" : "max-w-xl"}`}
        >
            <div>
                <div className="inline-flex items-center gap-2 rounded-full bg-[#efe4ff] px-3 py-1 text-xs font-semibold uppercase tracking-[0.24em] text-[#6b00e3]">
                    <ShieldCheck className="h-3.5 w-3.5" />
                    Cadastro rapido
                </div>
                <h2 className="mt-3 font-display text-3xl font-bold text-io-dark">Preencha os dados da sua loja e siga para o checkout</h2>
                <p className="mt-2 text-sm leading-7 text-black/55">{helperText}</p>
            </div>

            {error ? <p className="rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p> : null}

            <div className="grid gap-4 md:grid-cols-2">
                <Field
                    label="Nome completo"
                    value={form.ownerFullName}
                    onChange={(value) => setForm((current) => ({ ...current, ownerFullName: value }))}
                />
                <Field
                    label="Nome da empresa"
                    value={form.companyName}
                    onChange={(value) => setForm((current) => ({ ...current, companyName: value }))}
                />
                <Field
                    label="E-mail"
                    value={form.email}
                    onChange={(value) => setForm((current) => ({ ...current, email: value }))}
                    type="email"
                />
                <Field
                    label="Telefone"
                    value={form.phone}
                    onChange={(value) => setForm((current) => ({ ...current, phone: formatPhoneInput(value) }))}
                    inputMode="tel"
                    placeholder="(11) 99999-9999"
                />
            </div>

            <div className="rounded-[28px] border border-[#6b00e3]/10 bg-[#faf6ff] p-4">
                <p className="text-xs uppercase tracking-[0.24em] text-[#6b00e3]/75">Proximo passo</p>
                <p className="mt-2 text-sm leading-7 text-black/58">
                    Ao clicar em continuar, o sistema cria seu cadastro de interesse e abre o checkout do Asaas para concluir a assinatura recorrente.
                </p>
            </div>

            <button
                type="submit"
                disabled={loading}
                className="inline-flex items-center justify-center gap-2 rounded-full bg-[#6b00e3] px-5 py-3 text-sm font-semibold text-white transition hover:bg-[#5800bb] disabled:cursor-not-allowed disabled:bg-[#6b00e3]/35"
            >
                {loading ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <ArrowRight className="h-4 w-4" />}
                Continuar para o checkout
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
    inputMode,
    placeholder,
}: {
    label: string;
    value: string;
    onChange: (value: string) => void;
    type?: string;
    className?: string;
    inputMode?: InputHTMLAttributes<HTMLInputElement>["inputMode"];
    placeholder?: string;
}) {
    return (
        <label className={`grid gap-2 ${className}`}>
            <span className="text-sm font-medium text-black/60">{label}</span>
            <input
                type={type}
                value={value}
                onChange={(event) => onChange(event.target.value)}
                inputMode={inputMode}
                placeholder={placeholder}
                className="h-12 rounded-2xl border border-[#6b00e3]/12 bg-[#f9f6ff] px-4 text-sm text-io-dark outline-none transition focus:border-[#6b00e3]/35 focus:bg-white"
                required
            />
        </label>
    );
}

function normalizePhone(value: string) {
    return value.replace(/\D/g, "");
}

function formatPhoneInput(value: string) {
    const digits = normalizePhone(value).slice(0, 11);

    if (digits.length <= 2) return digits;
    if (digits.length <= 7) return `(${digits.slice(0, 2)}) ${digits.slice(2)}`;
    if (digits.length <= 10) return `(${digits.slice(0, 2)}) ${digits.slice(2, 6)}-${digits.slice(6)}`;
    return `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}`;
}
