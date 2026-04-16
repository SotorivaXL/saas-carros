"use client";

export type CrmStageKind = "initial" | "intermediate" | "final";

export type CrmStage = {
    id: string;
    title: string;
    kind: CrmStageKind;
    order: number;
    createdAt: string;
    updatedAt: string;
};

export type CrmCustomFieldType = "text" | "textarea" | "number" | "date";

export type CrmCustomField = {
    id: string;
    label: string;
    type: CrmCustomFieldType;
    order: number;
    createdAt: string;
    updatedAt: string;
};

export type CrmFollowUpDelayUnit = "minutes" | "hours" | "days";
export type CrmFollowUpDirection = "without_response" | "without_reply";

export type CrmFollowUp = {
    id: string;
    title: string;
    message: string;
    delayAmount: number;
    delayUnit: CrmFollowUpDelayUnit;
    isActive: boolean;
    createdAt: string;
    updatedAt: string;
};

export type CrmFollowUpNotification = {
    id: string;
    cycleKey: string;
    followUpId: string;
    followUpTitle: string;
    followUpMessage: string;
    conversationId: string;
    contactName: string;
    contactPhone: string;
    assignedUserId: string;
    assignedUserName: string;
    direction: CrmFollowUpDirection;
    lastMessageAt: string;
    lastMessagePreview: string;
    thresholdMinutes: number;
    createdAt: string;
    readAt?: string | null;
    resolvedAt?: string | null;
};

export type CrmLeadStageMap = Record<string, string>;
export type CrmLeadCustomFieldValueMap = Record<string, Record<string, string>>;
export type CrmLeadDefaultFieldValueMap = Record<string, { name?: string; description?: string; phone?: string; lastAt?: string }>;
export type CrmLeadFieldOrder = string[];
export type CrmState = {
    stages: CrmStage[];
    leadStageMap: CrmLeadStageMap;
    customFields: CrmCustomField[];
    leadFieldValues: CrmLeadCustomFieldValueMap;
    leadFieldOrder: CrmLeadFieldOrder;
    followUps: CrmFollowUp[];
    followUpNotifications: CrmFollowUpNotification[];
};

const CRM_STAGES_STORAGE_KEY = "io.crm.stages";
const CRM_LEAD_STAGE_STORAGE_KEY = "io.crm.lead.stage";
const CRM_CUSTOM_FIELDS_STORAGE_KEY = "io.crm.customFields";
const CRM_LEAD_FIELD_VALUES_STORAGE_KEY = "io.crm.lead.fieldValues";
const CRM_LEAD_DEFAULT_VALUES_STORAGE_KEY = "io.crm.lead.defaultValues";
const CRM_LEAD_FIELDS_ORDER_STORAGE_KEY = "io.crm.lead.fieldsOrder";
const CRM_FOLLOW_UPS_STORAGE_KEY = "io.crm.followUps";
const CRM_FOLLOW_UP_NOTIFICATIONS_STORAGE_KEY = "io.crm.followUpNotifications";
const CRM_STAGE_COLOR = "#6b00e3";
export const CRM_VALUE_FIELD_ID = "crm_field_value";
export const CRM_VALUE_FIELD_KEY = `custom:${CRM_VALUE_FIELD_ID}`;
export const CRM_VALUE_FIELD_LABEL = "Valor";
const CRM_VALUE_FIELD_TYPE: CrmCustomFieldType = "number";
export const EMPTY_CRM_STATE: CrmState = {
    stages: [],
    leadStageMap: {},
    customFields: [],
    leadFieldValues: {},
    leadFieldOrder: [],
    followUps: [],
    followUpNotifications: [],
};

function safeJsonParse<T>(value: string | null, fallback: T): T {
    if (!value) return fallback;
    try {
        return JSON.parse(value) as T;
    } catch {
        return fallback;
    }
}

function resolveStageKind(value: unknown): CrmStageKind {
    const normalized = String(value ?? "").trim().toLowerCase();
    if (normalized === "initial" || normalized === "intermediate" || normalized === "final") return normalized;
    return "intermediate";
}

function resolveFieldType(value: unknown): CrmCustomFieldType {
    const normalized = String(value ?? "").trim().toLowerCase();
    if (normalized === "text" || normalized === "textarea" || normalized === "number" || normalized === "date") return normalized;
    return "text";
}

function resolveFollowUpDelayUnit(value: unknown): CrmFollowUpDelayUnit {
    const normalized = String(value ?? "").trim().toLowerCase();
    if (normalized === "minutes" || normalized === "hours" || normalized === "days") return normalized;
    return "hours";
}

function normalizeFieldLabelKey(value: string) {
    return value
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .trim()
        .toLowerCase();
}

function normalizeStage(raw: Partial<CrmStage> | null | undefined, index = 0): CrmStage | null {
    if (!raw) return null;
    const id = String(raw.id ?? "").trim();
    const title = String(raw.title ?? "").trim();
    if (!id || !title) return null;
    const now = new Date().toISOString();
    const parsedOrder = Number(raw.order);
    return {
        id,
        title,
        kind: resolveStageKind(raw.kind),
        order: Number.isFinite(parsedOrder) ? parsedOrder : index,
        createdAt: String(raw.createdAt ?? now),
        updatedAt: String(raw.updatedAt ?? now),
    };
}

function normalizeCustomField(raw: Partial<CrmCustomField> | null | undefined, index = 0): CrmCustomField | null {
    if (!raw) return null;
    const id = String(raw.id ?? "").trim();
    const label = String(raw.label ?? "").trim();
    if (!id || !label) return null;
    const now = new Date().toISOString();
    return {
        id,
        label,
        type: resolveFieldType(raw.type),
        order: Number.isFinite(Number(raw.order)) ? Number(raw.order) : index,
        createdAt: String(raw.createdAt ?? now),
        updatedAt: String(raw.updatedAt ?? now),
    };
}

function normalizeFollowUp(raw: Partial<CrmFollowUp> | null | undefined, index = 0): CrmFollowUp | null {
    if (!raw) return null;
    const id = String(raw.id ?? "").trim();
    const title = String(raw.title ?? "").trim();
    const message = String(raw.message ?? "").trim();
    if (!id || !title) return null;
    const now = new Date().toISOString();
    const parsedDelayAmount = Math.round(Number(raw.delayAmount));
    return {
        id,
        title,
        message,
        delayAmount: Number.isFinite(parsedDelayAmount) && parsedDelayAmount > 0 ? parsedDelayAmount : index + 1,
        delayUnit: resolveFollowUpDelayUnit(raw.delayUnit),
        isActive: raw.isActive !== false,
        createdAt: String(raw.createdAt ?? now),
        updatedAt: String(raw.updatedAt ?? now),
    };
}

function normalizeFollowUpNotification(raw: Partial<CrmFollowUpNotification> | null | undefined): CrmFollowUpNotification | null {
    if (!raw) return null;
    const id = String(raw.id ?? "").trim();
    const cycleKey = String(raw.cycleKey ?? "").trim();
    const followUpId = String(raw.followUpId ?? "").trim();
    const followUpTitle = String(raw.followUpTitle ?? "").trim();
    const followUpMessage = String(raw.followUpMessage ?? "").trim();
    const conversationId = String(raw.conversationId ?? "").trim();
    const contactName = String(raw.contactName ?? "").trim();
    const contactPhone = String(raw.contactPhone ?? "").trim();
    const assignedUserId = String(raw.assignedUserId ?? "").trim();
    const assignedUserName = String(raw.assignedUserName ?? "").trim();
    const lastMessageAt = String(raw.lastMessageAt ?? "").trim();
    const lastMessagePreview = String(raw.lastMessagePreview ?? "").trim();
    const createdAt = String(raw.createdAt ?? "").trim();
    if (!id || !cycleKey || !followUpId || !followUpTitle || !conversationId || !assignedUserId || !lastMessageAt || !createdAt) return null;
    return {
        id,
        cycleKey,
        followUpId,
        followUpTitle,
        followUpMessage,
        conversationId,
        contactName,
        contactPhone,
        assignedUserId,
        assignedUserName,
        direction: raw.direction === "without_reply" ? "without_reply" : "without_response",
        lastMessageAt,
        lastMessagePreview,
        thresholdMinutes: Math.max(1, Math.round(Number(raw.thresholdMinutes) || 0)),
        createdAt,
        readAt: raw.readAt ? String(raw.readAt) : null,
        resolvedAt: raw.resolvedAt ? String(raw.resolvedAt) : null,
    };
}

function sortFollowUps(items: CrmFollowUp[]) {
    return [...items].sort((a, b) => {
        if (a.isActive !== b.isActive) return a.isActive ? -1 : 1;
        return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
    });
}

function sortFollowUpNotifications(items: CrmFollowUpNotification[]) {
    return [...items].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
}

function getFollowUpVersion(item: CrmFollowUp) {
    return new Date(item.updatedAt || item.createdAt).getTime() || 0;
}

function getFollowUpNotificationVersion(item: CrmFollowUpNotification) {
    return Math.max(
        new Date(item.createdAt).getTime() || 0,
        new Date(item.readAt ?? "").getTime() || 0,
        new Date(item.resolvedAt ?? "").getTime() || 0,
    );
}

function mergeFollowUps(primary: CrmFollowUp[], secondary: CrmFollowUp[]) {
    const map = new Map<string, CrmFollowUp>();
    for (const item of [...secondary, ...primary]) {
        const current = map.get(item.id);
        if (!current || getFollowUpVersion(item) >= getFollowUpVersion(current)) {
            map.set(item.id, item);
        }
    }
    return sortFollowUps(Array.from(map.values()));
}

function mergeFollowUpNotifications(primary: CrmFollowUpNotification[], secondary: CrmFollowUpNotification[]) {
    const map = new Map<string, CrmFollowUpNotification>();
    for (const item of [...secondary, ...primary]) {
        const current = map.get(item.id);
        if (!current || getFollowUpNotificationVersion(item) >= getFollowUpNotificationVersion(current)) {
            map.set(item.id, item);
        }
    }
    return sortFollowUpNotifications(Array.from(map.values()));
}

function loadShadowFollowUps() {
    if (typeof window === "undefined") return [] as CrmFollowUp[];
    const parsed = safeJsonParse<Partial<CrmFollowUp>[]>(window.localStorage.getItem(CRM_FOLLOW_UPS_STORAGE_KEY), []);
    return sortFollowUps(
        parsed
            .map((item, index) => normalizeFollowUp(item, index))
            .filter((item): item is CrmFollowUp => Boolean(item))
    );
}

function saveShadowFollowUps(items: CrmFollowUp[]) {
    if (typeof window === "undefined") return;
    window.localStorage.setItem(CRM_FOLLOW_UPS_STORAGE_KEY, JSON.stringify(sortFollowUps(items)));
}

function loadShadowFollowUpNotifications() {
    if (typeof window === "undefined") return [] as CrmFollowUpNotification[];
    const parsed = safeJsonParse<Partial<CrmFollowUpNotification>[]>(window.localStorage.getItem(CRM_FOLLOW_UP_NOTIFICATIONS_STORAGE_KEY), []);
    return sortFollowUpNotifications(
        parsed
            .map((item) => normalizeFollowUpNotification(item))
            .filter((item): item is CrmFollowUpNotification => Boolean(item))
    );
}

function saveShadowFollowUpNotifications(items: CrmFollowUpNotification[]) {
    if (typeof window === "undefined") return;
    window.localStorage.setItem(CRM_FOLLOW_UP_NOTIFICATIONS_STORAGE_KEY, JSON.stringify(sortFollowUpNotifications(items)));
}

function mergeCrmShadowState(state: CrmState) {
    const shadowFollowUps = loadShadowFollowUps();
    const shadowFollowUpNotifications = loadShadowFollowUpNotifications();
    return {
        ...state,
        followUps: mergeFollowUps(state.followUps, shadowFollowUps),
        followUpNotifications: mergeFollowUpNotifications(state.followUpNotifications, shadowFollowUpNotifications),
    } satisfies CrmState;
}

function saveCrmShadowState(state: Pick<CrmState, "followUps" | "followUpNotifications">) {
    saveShadowFollowUps(state.followUps);
    saveShadowFollowUpNotifications(state.followUpNotifications);
}

export function listCrmStages(): CrmStage[] {
    if (typeof window === "undefined") return [];
    const parsed = safeJsonParse<Partial<CrmStage>[]>(window.localStorage.getItem(CRM_STAGES_STORAGE_KEY), []);
    return parsed
        .map((item, index) => normalizeStage(item, index))
        .filter((item): item is CrmStage => Boolean(item))
        .sort((a, b) => a.order - b.order);
}

export function saveCrmStages(stages: CrmStage[]) {
    if (typeof window === "undefined") return;
    const normalized = stages
        .map((item, index) => normalizeStage(item, index))
        .filter((item): item is CrmStage => Boolean(item))
        .map((item, index) => ({ ...item, order: index }));
    window.localStorage.setItem(CRM_STAGES_STORAGE_KEY, JSON.stringify(normalized));
}

export function getCrmStageColor() {
    return CRM_STAGE_COLOR;
}

export function listCrmLeadStageMap(): CrmLeadStageMap {
    if (typeof window === "undefined") return {};
    const parsed = safeJsonParse<Record<string, unknown>>(window.localStorage.getItem(CRM_LEAD_STAGE_STORAGE_KEY), {});
    const next: CrmLeadStageMap = {};
    for (const key of Object.keys(parsed)) {
        const leadId = key.trim();
        const stageId = String(parsed[key] ?? "").trim();
        if (!leadId || !stageId) continue;
        next[leadId] = stageId;
    }
    return next;
}

export function saveCrmLeadStageMap(map: CrmLeadStageMap) {
    if (typeof window === "undefined") return;
    const next: CrmLeadStageMap = {};
    for (const key of Object.keys(map)) {
        const leadId = key.trim();
        const stageId = String(map[key] ?? "").trim();
        if (!leadId || !stageId) continue;
        next[leadId] = stageId;
    }
    window.localStorage.setItem(CRM_LEAD_STAGE_STORAGE_KEY, JSON.stringify(next));
}

export function listCrmCustomFields(): CrmCustomField[] {
    if (typeof window === "undefined") return [];
    const parsed = safeJsonParse<Partial<CrmCustomField>[]>(window.localStorage.getItem(CRM_CUSTOM_FIELDS_STORAGE_KEY), []);
    return parsed
        .map((item, index) => normalizeCustomField(item, index))
        .filter((item): item is CrmCustomField => Boolean(item))
        .sort((a, b) => a.order - b.order);
}

export function saveCrmCustomFields(fields: CrmCustomField[]) {
    if (typeof window === "undefined") return;
    const normalized = fields
        .map((item, index) => normalizeCustomField(item, index))
        .filter((item): item is CrmCustomField => Boolean(item))
        .map((item, index) => ({ ...item, order: index }));
    window.localStorage.setItem(CRM_CUSTOM_FIELDS_STORAGE_KEY, JSON.stringify(normalized));
}

export function listCrmLeadCustomFieldValues(): CrmLeadCustomFieldValueMap {
    if (typeof window === "undefined") return {};
    const parsed = safeJsonParse<Record<string, unknown>>(window.localStorage.getItem(CRM_LEAD_FIELD_VALUES_STORAGE_KEY), {});
    const next: CrmLeadCustomFieldValueMap = {};
    for (const leadId of Object.keys(parsed)) {
        const normalizedLeadId = leadId.trim();
        if (!normalizedLeadId) continue;
        const rawFields = parsed[leadId];
        if (!rawFields || typeof rawFields !== "object") continue;
        const values: Record<string, string> = {};
        for (const fieldId of Object.keys(rawFields as Record<string, unknown>)) {
            const normalizedFieldId = fieldId.trim();
            const value = String((rawFields as Record<string, unknown>)[fieldId] ?? "").trim();
            if (!normalizedFieldId) continue;
            values[normalizedFieldId] = value;
        }
        next[normalizedLeadId] = values;
    }
    return next;
}

export function saveCrmLeadCustomFieldValues(map: CrmLeadCustomFieldValueMap) {
    if (typeof window === "undefined") return;
    const next: CrmLeadCustomFieldValueMap = {};
    for (const leadId of Object.keys(map)) {
        const normalizedLeadId = leadId.trim();
        if (!normalizedLeadId) continue;
        const rawFields = map[leadId] ?? {};
        const values: Record<string, string> = {};
        for (const fieldId of Object.keys(rawFields)) {
            const normalizedFieldId = fieldId.trim();
            if (!normalizedFieldId) continue;
            values[normalizedFieldId] = String(rawFields[fieldId] ?? "");
        }
        next[normalizedLeadId] = values;
    }
    window.localStorage.setItem(CRM_LEAD_FIELD_VALUES_STORAGE_KEY, JSON.stringify(next));
}

export function listCrmLeadDefaultFieldValues(): CrmLeadDefaultFieldValueMap {
    if (typeof window === "undefined") return {};
    const parsed = safeJsonParse<Record<string, unknown>>(window.localStorage.getItem(CRM_LEAD_DEFAULT_VALUES_STORAGE_KEY), {});
    const next: CrmLeadDefaultFieldValueMap = {};
    for (const leadId of Object.keys(parsed)) {
        const normalizedLeadId = leadId.trim();
        if (!normalizedLeadId) continue;
        const raw = parsed[leadId];
        if (!raw || typeof raw !== "object") continue;
        const value = raw as Record<string, unknown>;
        next[normalizedLeadId] = {
            name: typeof value.name === "string" ? value.name : undefined,
            description: typeof value.description === "string" ? value.description : undefined,
            phone: typeof value.phone === "string" ? value.phone : undefined,
            lastAt: typeof value.lastAt === "string" ? value.lastAt : undefined,
        };
    }
    return next;
}

export function saveCrmLeadDefaultFieldValues(map: CrmLeadDefaultFieldValueMap) {
    if (typeof window === "undefined") return;
    const next: CrmLeadDefaultFieldValueMap = {};
    for (const leadId of Object.keys(map)) {
        const normalizedLeadId = leadId.trim();
        if (!normalizedLeadId) continue;
        const value = map[leadId] ?? {};
        next[normalizedLeadId] = {
            name: typeof value.name === "string" ? value.name : undefined,
            description: typeof value.description === "string" ? value.description : undefined,
            phone: typeof value.phone === "string" ? value.phone : undefined,
            lastAt: typeof value.lastAt === "string" ? value.lastAt : undefined,
        };
    }
    window.localStorage.setItem(CRM_LEAD_DEFAULT_VALUES_STORAGE_KEY, JSON.stringify(next));
}

export function listCrmLeadFieldOrder(): CrmLeadFieldOrder {
    if (typeof window === "undefined") return [];
    const parsed = safeJsonParse<unknown[]>(window.localStorage.getItem(CRM_LEAD_FIELDS_ORDER_STORAGE_KEY), []);
    return parsed
        .map((item) => String(item ?? "").trim())
        .filter(Boolean);
}

export function saveCrmLeadFieldOrder(order: CrmLeadFieldOrder) {
    if (typeof window === "undefined") return;
    const normalized = order
        .map((item) => String(item ?? "").trim())
        .filter(Boolean);
    window.localStorage.setItem(CRM_LEAD_FIELDS_ORDER_STORAGE_KEY, JSON.stringify(normalized));
}

function ensureValueFieldState(
    fields: CrmCustomField[],
    order: CrmLeadFieldOrder,
    leadFieldValues: CrmLeadCustomFieldValueMap
): Pick<CrmState, "customFields" | "leadFieldOrder" | "leadFieldValues"> {
    const now = new Date().toISOString();
    const hasDefaultValueField = fields.some((field) => field.id === CRM_VALUE_FIELD_ID);
    const legacyValueFieldId = hasDefaultValueField
        ? null
        : fields.find((field) => normalizeFieldLabelKey(field.label) === normalizeFieldLabelKey(CRM_VALUE_FIELD_LABEL))?.id ?? null;

    const fieldById = new Map<string, CrmCustomField>();
    for (const field of fields) {
        const normalizedId = field.id === legacyValueFieldId ? CRM_VALUE_FIELD_ID : field.id;
        fieldById.set(normalizedId, normalizedId === CRM_VALUE_FIELD_ID
            ? {
                ...field,
                id: CRM_VALUE_FIELD_ID,
                label: CRM_VALUE_FIELD_LABEL,
                type: CRM_VALUE_FIELD_TYPE,
            }
            : field);
    }

    if (!fieldById.has(CRM_VALUE_FIELD_ID)) {
        fieldById.set(CRM_VALUE_FIELD_ID, {
            id: CRM_VALUE_FIELD_ID,
            label: CRM_VALUE_FIELD_LABEL,
            type: CRM_VALUE_FIELD_TYPE,
            order: 0,
            createdAt: now,
            updatedAt: now,
        });
    }

    const nextLeadFieldValues: CrmLeadCustomFieldValueMap = {};
    for (const leadId of Object.keys(leadFieldValues)) {
        const currentValues = leadFieldValues[leadId] ?? {};
        const nextValues: Record<string, string> = {};
        for (const fieldId of Object.keys(currentValues)) {
            const normalizedFieldId = fieldId === legacyValueFieldId ? CRM_VALUE_FIELD_ID : fieldId;
            nextValues[normalizedFieldId] = String(currentValues[fieldId] ?? "");
        }
        nextLeadFieldValues[leadId] = nextValues;
    }

    const validKeys = new Set(Array.from(fieldById.keys()).map((fieldId) => `custom:${fieldId}`));
    const migratedOrder = order
        .map((item) => item === `custom:${legacyValueFieldId}` ? CRM_VALUE_FIELD_KEY : item)
        .filter((item, index, items) => validKeys.has(item) && items.indexOf(item) === index);
    const nextOrder = [CRM_VALUE_FIELD_KEY, ...migratedOrder.filter((item) => item !== CRM_VALUE_FIELD_KEY)];

    const orderedIds = [
        ...nextOrder.map((item) => item.replace(/^custom:/, "")).filter((fieldId, index, items) => fieldById.has(fieldId) && items.indexOf(fieldId) === index),
        ...Array.from(fieldById.keys()).filter((fieldId) => !nextOrder.includes(`custom:${fieldId}`)),
    ];
    const nextFields = orderedIds.map((fieldId, index) => {
        const field = fieldById.get(fieldId)!;
        return {
            ...field,
            label: fieldId === CRM_VALUE_FIELD_ID ? CRM_VALUE_FIELD_LABEL : field.label,
            type: fieldId === CRM_VALUE_FIELD_ID ? CRM_VALUE_FIELD_TYPE : field.type,
            order: index,
        };
    });

    return {
        customFields: nextFields,
        leadFieldOrder: nextOrder,
        leadFieldValues: nextLeadFieldValues,
    };
}

export function sanitizeCrmState(raw: unknown): CrmState {
    const source = (raw ?? {}) as Record<string, unknown>;
    const rawStages = Array.isArray(source.stages) ? source.stages as Partial<CrmStage>[] : [];
    const rawCustomFields = Array.isArray(source.customFields) ? source.customFields as Partial<CrmCustomField>[] : [];
    const rawFollowUps = Array.isArray(source.followUps) ? source.followUps as Partial<CrmFollowUp>[] : [];
    const rawFollowUpNotifications = Array.isArray(source.followUpNotifications) ? source.followUpNotifications as Partial<CrmFollowUpNotification>[] : [];
    const stages = rawStages
        .map((item, index) => normalizeStage(item, index))
        .filter((item): item is CrmStage => Boolean(item))
        .sort((a, b) => a.order - b.order);
    const customFields = rawCustomFields
        .map((item, index) => normalizeCustomField(item, index))
        .filter((item): item is CrmCustomField => Boolean(item))
        .sort((a, b) => a.order - b.order);
    const followUps = rawFollowUps
        .map((item, index) => normalizeFollowUp(item, index))
        .filter((item): item is CrmFollowUp => Boolean(item))
        .sort((a, b) => {
            if (a.isActive !== b.isActive) return a.isActive ? -1 : 1;
            return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
        });
    const followUpNotifications = rawFollowUpNotifications
        .map((item) => normalizeFollowUpNotification(item))
        .filter((item): item is CrmFollowUpNotification => Boolean(item))
        .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

    const leadStageMap = safeJsonParse<Record<string, unknown>>(JSON.stringify(source.leadStageMap ?? {}), {});
    const leadFieldValuesRaw = safeJsonParse<Record<string, unknown>>(JSON.stringify(source.leadFieldValues ?? {}), {});
    const leadFieldOrderRaw = Array.isArray(source.leadFieldOrder) ? source.leadFieldOrder : [];

    const normalizedStageMap: CrmLeadStageMap = {};
    for (const key of Object.keys(leadStageMap)) {
        const leadId = key.trim();
        const stageId = String(leadStageMap[key] ?? "").trim();
        if (!leadId || !stageId) continue;
        normalizedStageMap[leadId] = stageId;
    }

    const normalizedFieldValues: CrmLeadCustomFieldValueMap = {};
    for (const leadId of Object.keys(leadFieldValuesRaw)) {
        const normalizedLeadId = leadId.trim();
        if (!normalizedLeadId) continue;
        const valuesRaw = leadFieldValuesRaw[leadId];
        if (!valuesRaw || typeof valuesRaw !== "object") continue;
        const valuesObj = valuesRaw as Record<string, unknown>;
        const values: Record<string, string> = {};
        for (const fieldId of Object.keys(valuesObj)) {
            const normalizedFieldId = fieldId.trim();
            if (!normalizedFieldId) continue;
            values[normalizedFieldId] = String(valuesObj[fieldId] ?? "");
        }
        normalizedFieldValues[normalizedLeadId] = values;
    }

    const normalizedOrder = leadFieldOrderRaw
        .map((item) => String(item ?? "").trim())
        .filter(Boolean);
    const ensuredState = ensureValueFieldState(customFields, normalizedOrder, normalizedFieldValues);

    return {
        stages,
        leadStageMap: normalizedStageMap,
        customFields: ensuredState.customFields,
        leadFieldValues: ensuredState.leadFieldValues,
        leadFieldOrder: ensuredState.leadFieldOrder,
        followUps,
        followUpNotifications,
    };
}

export async function loadCrmStateFromApi(): Promise<CrmState> {
    const res = await fetch("/api/crm/state", { cache: "no-store" });
    if (!res.ok) throw new Error("Falha ao carregar estado do CRM.");
    const data = await res.json().catch(() => ({}));
    const merged = mergeCrmShadowState(sanitizeCrmState(data));
    saveCrmShadowState(merged);
    return merged;
}

export async function saveCrmStateToApi(state: CrmState): Promise<CrmState> {
    const sanitized = sanitizeCrmState(state);
    saveCrmShadowState(sanitized);
    const res = await fetch("/api/crm/state", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(sanitized),
    });
    if (!res.ok) throw new Error("Falha ao salvar estado do CRM.");
    const data = await res.json().catch(() => sanitized);
    const responseState = sanitizeCrmState(data);
    const merged = mergeCrmShadowState({
        ...responseState,
        followUps: mergeFollowUps(sanitized.followUps, responseState.followUps),
        followUpNotifications: mergeFollowUpNotifications(sanitized.followUpNotifications, responseState.followUpNotifications),
    });
    saveCrmShadowState(merged);
    return merged;
}

export async function mergeCrmStatePatchToApi(patch: Partial<CrmState>): Promise<CrmState> {
    const current = await loadCrmStateFromApi();
    return saveCrmStateToApi({
        ...current,
        ...patch,
    });
}
