import { BrandMark } from "@/modules/ioauto/components/BrandMark";
import { SignupCheckoutForm } from "@/modules/ioauto/components/SignupCheckoutForm";

export default function AssinarPage() {
    return (
        <main className="min-h-screen bg-[#f3f3f3] px-6 py-10">
            <div className="mx-auto max-w-5xl">
                <BrandMark />
                <div className="mt-10 grid gap-6 lg:grid-cols-[0.85fr_1.15fr]">
                    <section className="rounded-[36px] border border-black/10 bg-black p-8 text-white shadow-[0_30px_80px_rgba(0,0,0,0.15)]">
                        <p className="text-xs uppercase tracking-[0.28em] text-white/45">Checkout recorrente</p>
                        <h1 className="mt-5 font-display text-5xl font-bold leading-[1.04]">Ative seu tenant IOAuto sem onboarding manual.</h1>
                        <p className="mt-5 text-sm leading-8 text-white/70">
                            Depois do pagamento, a conta da operacao fica pronta para login com os dados definidos no cadastro.
                        </p>
                    </section>
                    <SignupCheckoutForm compact />
                </div>
            </div>
        </main>
    );
}
