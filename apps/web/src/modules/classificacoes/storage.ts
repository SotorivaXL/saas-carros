"use client";

export type AtendimentoClassification = {
    id: string;
    title: string;
    categoryId: AtendimentoClassificationCategoryId;
    hasValue: boolean;
    value: number | null;
    system: boolean;
    createdAt: string;
    updatedAt: string;
};

export type AtendimentoClassificationCategoryId = "achieved" | "lost" | "questions" | "other";

export type AtendimentoClassificationCategory = {
    id: AtendimentoClassificationCategoryId;
    label: string;
};

export type ContactConclusion = {
    classificationIds: string[];
    concludedAt: string;
};

export type ContactConclusionMap = Record<string, ContactConclusion>;

const CUSTOM_CLASSIFICATIONS_STORAGE_KEY = "io.atendimento.classifications.custom";
const CONCLUSIONS_STORAGE_KEY = "io.atendimento.conclusions";

const CLASSIFICATION_CATEGORIES: AtendimentoClassificationCategory[] = [
    { id: "achieved", label: "Objetivo atingido" },
    { id: "lost", label: "Objetivo perdido" },
    { id: "questions", label: "Dúvidas" },
    { id: "other", label: "Outro" },
];

const DEFAULT_CLASSIFICATIONS: AtendimentoClassification[] = [
    { id: "default-objective-achieved", title: "Objetivo atingido", categoryId: "achieved", hasValue: false, value: null, system: true, createdAt: "system", updatedAt: "system" },
    { id: "default-objective-lost", title: "Objetivo perdido", categoryId: "lost", hasValue: false, value: null, system: true, createdAt: "system", updatedAt: "system" },
    { id: "default-questions", title: "Dúvidas", categoryId: "questions", hasValue: false, value: null, system: true, createdAt: "system", updatedAt: "system" },
    { id: "default-other", title: "Outro", categoryId: "other", hasValue: false, value: null, system: true, createdAt: "system", updatedAt: "system" },
];

function safeJsonParse<T>(value: string | null, fallback: T): T {
    if (!value) return fallback;
    try {
        return JSON.parse(value) as T;
    } catch {
        return fallback;
    }
}

function normalizeClassification(raw: Partial<AtendimentoClassification> | null | undefined): AtendimentoClassification | null {
    if (!raw) return null;
    const id = String(raw.id ?? "").trim();
    const title = String(raw.title ?? "").trim();
    if (!id || !title) return null;
    const now = new Date().toISOString();
    const hasValue = Boolean(raw.hasValue);
    const parsedValue = Number(raw.value);
    const value = hasValue && Number.isFinite(parsedValue) ? parsedValue : null;
    return {
        id,
        title,
        categoryId: resolveCategoryId(raw.categoryId),
        hasValue,
        value,
        system: false,
        createdAt: String(raw.createdAt ?? now),
        updatedAt: String(raw.updatedAt ?? now),
    };
}

function resolveCategoryId(value: unknown): AtendimentoClassificationCategoryId {
    const normalized = String(value ?? "").trim().toLowerCase();
    if (normalized === "achieved" || normalized === "lost" || normalized === "questions" || normalized === "other") {
        return normalized;
    }
    return "other";
}

export function listAtendimentoClassificationCategories() {
    return CLASSIFICATION_CATEGORIES;
}

export function listDefaultAtendimentoClassifications() {
    return DEFAULT_CLASSIFICATIONS;
}

export function listCustomAtendimentoClassifications(): AtendimentoClassification[] {
    if (typeof window === "undefined") return [];
    const parsed = safeJsonParse<Partial<AtendimentoClassification>[]>(window.localStorage.getItem(CUSTOM_CLASSIFICATIONS_STORAGE_KEY), []);
    return parsed
        .map((item) => normalizeClassification(item))
        .filter((item): item is AtendimentoClassification => Boolean(item));
}

export function saveCustomAtendimentoClassifications(classifications: AtendimentoClassification[]) {
    if (typeof window === "undefined") return;
    const normalized = classifications
        .map((item) => normalizeClassification(item))
        .filter((item): item is AtendimentoClassification => Boolean(item));
    window.localStorage.setItem(CUSTOM_CLASSIFICATIONS_STORAGE_KEY, JSON.stringify(normalized));
}

export function listAllAtendimentoClassifications(): AtendimentoClassification[] {
    return [...listDefaultAtendimentoClassifications(), ...listCustomAtendimentoClassifications()];
}

export function listContactConclusions(): ContactConclusionMap {
    if (typeof window === "undefined") return {};
    const parsed = safeJsonParse<Record<string, unknown>>(window.localStorage.getItem(CONCLUSIONS_STORAGE_KEY), {});
    const next: ContactConclusionMap = {};
    for (const key of Object.keys(parsed)) {
        const normalizedKey = key.trim();
        if (!normalizedKey) continue;
        const value = parsed[key] as Partial<ContactConclusion> | null | undefined;
        if (!value) continue;
        const ids = (value.classificationIds ?? [])
            .map((item) => String(item ?? "").trim())
            .filter(Boolean);
        if (!ids.length) continue;
        next[normalizedKey] = {
            classificationIds: Array.from(new Set(ids)),
            concludedAt: String(value.concludedAt ?? new Date().toISOString()),
        };
    }
    return next;
}

export function saveContactConclusions(map: ContactConclusionMap) {
    if (typeof window === "undefined") return;
    const next: ContactConclusionMap = {};
    for (const key of Object.keys(map)) {
        const normalizedKey = key.trim();
        if (!normalizedKey) continue;
        const value = map[key];
        if (!value) continue;
        const ids = (value.classificationIds ?? [])
            .map((item) => String(item ?? "").trim())
            .filter(Boolean);
        if (!ids.length) continue;
        next[normalizedKey] = {
            classificationIds: Array.from(new Set(ids)),
            concludedAt: String(value.concludedAt ?? new Date().toISOString()),
        };
    }
    window.localStorage.setItem(CONCLUSIONS_STORAGE_KEY, JSON.stringify(next));
}
