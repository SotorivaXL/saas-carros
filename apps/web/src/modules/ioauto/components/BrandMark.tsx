import Image from "next/image";
import Link from "next/link";
import logoIoAuto from "@/assets/LOGO-IOAUTO.png";
import logoIoAutoWhite from "@/assets/LOGO-IOAUTO-BRANCA.png";
type BrandMarkProps = {
    href?: string;
    compact?: boolean;
    variant?: "default" | "white";
};

export function BrandMark({ href = "/", compact = false, variant = "default" }: BrandMarkProps) {
    return (
        <Link href={href} className="inline-flex items-center">
            <Image
                src={variant === "white" ? logoIoAutoWhite : logoIoAuto}
                alt="IOAuto"
                priority
                className={`w-auto object-contain ${compact ? "h-8 max-w-8" : "h-9 max-w-[144px]"}`}
            />
        </Link>
    );
}
