import Link from "next/link";

export default function AssinaturaCanceladaPage() {
    return (
        <main className="min-h-screen bg-[#f3f3f3] px-6 py-10">
            <div className="mx-auto max-w-3xl rounded-[34px] border border-black/10 bg-white p-8 shadow-[0_18px_45px_rgba(0,0,0,0.08)]">
                <p className="text-xs uppercase tracking-[0.28em] text-black/40">Checkout cancelado</p>
                <h1 className="mt-4 font-display text-4xl font-bold text-io-dark">Nenhuma cobranca foi concluida.</h1>
                <p className="mt-4 text-sm leading-7 text-black/55">
                    Voce pode revisar os dados da operacao e iniciar novamente o checkout quando quiser.
                </p>
                <div className="mt-6 flex gap-3">
                    <Link href="/assinar" className="rounded-full bg-black px-5 py-3 text-sm font-semibold text-white transition hover:bg-black/85">
                        Tentar novamente
                    </Link>
                    <Link href="/" className="rounded-full border border-black/10 px-5 py-3 text-sm font-semibold text-black/65 transition hover:border-black/20 hover:text-black">
                        Voltar para a home
                    </Link>
                </div>
            </div>
        </main>
    );
}
