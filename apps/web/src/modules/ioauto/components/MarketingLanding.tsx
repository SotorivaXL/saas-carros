import Link from "next/link";
import { ArrowRight, Cable, CarFront, MessageSquareText, Workflow } from "lucide-react";
import { BrandMark } from "@/modules/ioauto/components/BrandMark";
import { SignupCheckoutForm } from "@/modules/ioauto/components/SignupCheckoutForm";

const features = [
    {
        icon: <CarFront className="h-5 w-5" />,
        title: "Cadastro único de veículos",
        body: "Preencha uma vez e transforme o mesmo cadastro em distribuição para todos os canais conectados.",
    },
    {
        icon: <MessageSquareText className="h-5 w-5" />,
        title: "Central de leads integrada",
        body: "Cada atendimento chega com a plataforma de origem em destaque para orientar o vendedor desde o primeiro contato.",
    },
    {
        icon: <Workflow className="h-5 w-5" />,
        title: "Publicação organizada por tenant",
        body: "Pronto para crescer como micro-saas multi-tenant com trilhas separadas de estoque, leads e operação.",
    },
    {
        icon: <Cable className="h-5 w-5" />,
        title: "Base de integrações pronta",
        body: "Webmotors primeiro e espaço estruturado para próximos marketplaces de vendas.",
    },
];

export function MarketingLanding() {
    return (
        <main className="min-h-screen bg-[#f3f3f3] text-io-dark">
            <section className="mx-auto max-w-7xl px-6 py-8">
                <div className="flex items-center justify-between gap-4">
                    <BrandMark />
                    <div className="flex items-center gap-3">
                        <Link href="/login" className="rounded-full border border-black/10 px-4 py-2 text-sm font-semibold text-black/65 transition hover:border-black/20 hover:text-black">
                            Entrar
                        </Link>
                        <Link href="/assinar" className="rounded-full bg-black px-4 py-2 text-sm font-semibold text-white transition hover:bg-black/85">
                            Assinar
                        </Link>
                    </div>
                </div>
            </section>

            <section className="mx-auto grid max-w-7xl gap-10 px-6 pb-16 pt-4 lg:grid-cols-[1.15fr_0.85fr]">
                <div className="rounded-[40px] border border-black/10 bg-gradient-to-br from-white via-[#ececec] to-[#dfdfdf] p-8 shadow-[0_30px_90px_rgba(0,0,0,0.08)] sm:p-10">
                    <span className="inline-flex rounded-full border border-black/10 bg-white px-3 py-1 text-xs uppercase tracking-[0.28em] text-black/45">
                        Micro-saas automotivo
                    </span>
                    <h1 className="mt-6 max-w-4xl font-display text-5xl font-bold leading-[1.04] sm:text-6xl">
                        O sistema operacional para vender carros com leads integrados e publicação multicanal.
                    </h1>
                    <p className="mt-6 max-w-2xl text-base leading-8 text-black/60">
                        O IOAuto nasce sobre uma base multi-tenant já pronta para assinatura recorrente, Webmotors e futuras integrações de marketplace.
                    </p>

                    <div className="mt-8 flex flex-wrap gap-3">
                        <Link href="/assinar" className="inline-flex items-center gap-2 rounded-full bg-black px-5 py-3 text-sm font-semibold text-white transition hover:bg-black/85">
                            Começar agora
                            <ArrowRight className="h-4 w-4" />
                        </Link>
                        <Link href="/login" className="rounded-full border border-black/10 px-5 py-3 text-sm font-semibold text-black/65 transition hover:border-black/20 hover:text-black">
                            Acessar área do cliente
                        </Link>
                    </div>

                    <div className="mt-10 grid gap-4 md:grid-cols-2">
                        {features.map((feature) => (
                            <article key={feature.title} className="rounded-[28px] border border-black/10 bg-white/70 p-5">
                                <div className="grid h-10 w-10 place-items-center rounded-2xl bg-black text-white">{feature.icon}</div>
                                <h2 className="mt-4 font-display text-2xl font-bold">{feature.title}</h2>
                                <p className="mt-2 text-sm leading-7 text-black/55">{feature.body}</p>
                            </article>
                        ))}
                    </div>
                </div>

                <div className="grid gap-6">
                    <SignupCheckoutForm />
                    <div className="rounded-[34px] border border-black/10 bg-black p-6 text-white shadow-[0_24px_60px_rgba(0,0,0,0.16)]">
                        <p className="text-xs uppercase tracking-[0.28em] text-white/45">Pronto para deploy</p>
                        <h2 className="mt-4 font-display text-3xl font-bold">Estrutura pensada para sua VPS</h2>
                        <p className="mt-4 text-sm leading-7 text-white/70">
                            Front em Next.js, API Spring Boot, multi-tenant, billing automatizado e documentação enxuta para novos deploys e expansão de canais.
                        </p>
                    </div>
                </div>
            </section>
        </main>
    );
}
