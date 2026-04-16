import type { Metadata } from "next";
import { Manrope, Space_Grotesk } from "next/font/google";

import "./globals.css";

const manrope = Manrope({
    subsets: ["latin"],
    variable: "--font-body",
});

const spaceGrotesk = Space_Grotesk({
    subsets: ["latin"],
    variable: "--font-display",
});

export const metadata: Metadata = {
    title: "IOAuto",
    description: "Micro-saas multi-tenant para operacao automotiva com atendimento humano e publicacao multicanal.",
    icons: {
        icon: "/favicon.ico",
        shortcut: "/favicon.ico",
    },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
    return (
        <html lang="pt-BR">
            <body className={`${manrope.variable} ${spaceGrotesk.variable}`}>{children}</body>
        </html>
    );
}
