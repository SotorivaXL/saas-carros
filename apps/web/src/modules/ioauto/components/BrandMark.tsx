import Image from "next/image";
import Link from "next/link";
import logoIoAuto from "@/assets/LOGO-IOAUTO.png";

type BrandMarkProps = {
    href?: string;
    compact?: boolean;
};

export function BrandMark({ href = "/", compact = false }: BrandMarkProps) {
    return (
        <Link href={href} className="inline-flex items-center">
            <Image
                src={logoIoAuto}
                alt="IOAuto"
                priority
                className={`w-auto object-contain ${compact ? "h-8 max-w-8" : "h-9 max-w-[144px]"}`}
            />
        </Link>
    );
}
