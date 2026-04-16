"use client";

import {
    CircleHelp,
    type LucideIcon,
    PartyPopper,
    ShieldBan,
    Sparkles,
} from "lucide-react";
import type { AtendimentoClassificationCategoryId } from "@/modules/classificacoes/storage";

const CATEGORY_ICON_BY_ID: Record<AtendimentoClassificationCategoryId, LucideIcon> = {
    achieved: PartyPopper,
    lost: ShieldBan,
    questions: CircleHelp,
    other: Sparkles,
};

export function AtendimentoClassificationCategoryIcon({
    categoryId,
    className,
    strokeWidth = 1.9,
}: {
    categoryId: AtendimentoClassificationCategoryId;
    className?: string;
    strokeWidth?: number;
}) {
    const Icon = CATEGORY_ICON_BY_ID[categoryId];
    return <Icon className={className} strokeWidth={strokeWidth} aria-hidden="true" />;
}
