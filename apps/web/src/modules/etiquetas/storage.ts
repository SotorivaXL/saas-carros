"use client";

export type ContactLabel = {
    id: string;
    title: string;
    color: string;
    createdAt: string;
    updatedAt: string;
};

export type ContactLabelAssignments = Record<string, string[]>;

const LABELS_STORAGE_KEY = "io.contact.labels";
const ASSIGNMENTS_STORAGE_KEY = "io.contact.label.assignments";

function safeJsonParse<T>(value: string | null, fallback: T): T {
    if (!value) return fallback;
    try {
        return JSON.parse(value) as T;
    } catch {
        return fallback;
    }
}

function isHexColor(value: string) {
    return /^#([0-9a-fA-F]{6})$/.test(value);
}

export function normalizeHexColor(value: string) {
    const normalized = value.trim();
    if (!normalized) return "#64748b";
    if (isHexColor(normalized)) return normalized.toUpperCase();
    if (/^[0-9a-fA-F]{6}$/.test(normalized)) return `#${normalized.toUpperCase()}`;
    return "#64748b";
}

function normalizeLabel(raw: Partial<ContactLabel> | null | undefined): ContactLabel | null {
    if (!raw) return null;
    const id = String(raw.id ?? "").trim();
    const title = String(raw.title ?? "").trim();
    if (!id || !title) return null;
    const now = new Date().toISOString();
    return {
        id,
        title,
        color: normalizeHexColor(String(raw.color ?? "#64748b")),
        createdAt: String(raw.createdAt ?? now),
        updatedAt: String(raw.updatedAt ?? now),
    };
}

export function listContactLabels(): ContactLabel[] {
    if (typeof window === "undefined") return [];
    const parsed = safeJsonParse<Partial<ContactLabel>[]>(window.localStorage.getItem(LABELS_STORAGE_KEY), []);
    return parsed
        .map((item) => normalizeLabel(item))
        .filter((item): item is ContactLabel => Boolean(item));
}

export function saveContactLabels(labels: ContactLabel[]) {
    if (typeof window === "undefined") return;
    const normalized = labels
        .map((label) => normalizeLabel(label))
        .filter((item): item is ContactLabel => Boolean(item));
    window.localStorage.setItem(LABELS_STORAGE_KEY, JSON.stringify(normalized));
}

export function listContactLabelAssignments(): ContactLabelAssignments {
    if (typeof window === "undefined") return {};
    const parsed = safeJsonParse<Record<string, unknown>>(window.localStorage.getItem(ASSIGNMENTS_STORAGE_KEY), {});
    const next: ContactLabelAssignments = {};
    for (const key of Object.keys(parsed)) {
        const value = parsed[key];
        if (!Array.isArray(value)) continue;
        const ids = value
            .map((item) => String(item ?? "").trim())
            .filter(Boolean);
        if (ids.length) next[key] = Array.from(new Set(ids));
    }
    return next;
}

export function saveContactLabelAssignments(assignments: ContactLabelAssignments) {
    if (typeof window === "undefined") return;
    const next: ContactLabelAssignments = {};
    for (const key of Object.keys(assignments)) {
        const normalizedKey = key.trim();
        if (!normalizedKey) continue;
        const ids = (assignments[key] ?? [])
            .map((item) => String(item ?? "").trim())
            .filter(Boolean);
        if (ids.length) next[normalizedKey] = Array.from(new Set(ids));
    }
    window.localStorage.setItem(ASSIGNMENTS_STORAGE_KEY, JSON.stringify(next));
}

export function getLabelTextColor(backgroundHex: string) {
    const color = normalizeHexColor(backgroundHex).slice(1);
    const r = parseInt(color.slice(0, 2), 16);
    const g = parseInt(color.slice(2, 4), 16);
    const b = parseInt(color.slice(4, 6), 16);
    const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
    return luminance > 0.65 ? "#111827" : "#FFFFFF";
}

