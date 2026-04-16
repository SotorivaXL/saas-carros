"use client";

import type { CrmFollowUp, CrmFollowUpDelayUnit, CrmFollowUpDirection, CrmFollowUpNotification } from "@/modules/crm/storage";

export type FollowUpConversationSnapshot = {
    id: string;
    phone: string;
    displayName?: string | null;
    assignedUserId?: string | null;
    assignedUserName?: string | null;
    lastMessage?: string | null;
    lastAt?: string | null;
    lastMessageFromMe?: boolean | null;
};

export function toTimestamp(value?: string | null) {
    if (!value) return 0;
    const parsed = new Date(value).getTime();
    return Number.isFinite(parsed) ? parsed : 0;
}

export function getFollowUpDelayMinutes(delayAmount: number, delayUnit: CrmFollowUpDelayUnit) {
    const safeAmount = Math.max(1, Math.round(Number(delayAmount) || 0));
    if (delayUnit === "days") return safeAmount * 24 * 60;
    if (delayUnit === "hours") return safeAmount * 60;
    return safeAmount;
}

export function formatFollowUpDelay(delayAmount: number, delayUnit: CrmFollowUpDelayUnit) {
    const safeAmount = Math.max(1, Math.round(Number(delayAmount) || 0));
    if (delayUnit === "days") return `${safeAmount} dia${safeAmount > 1 ? "s" : ""}`;
    if (delayUnit === "hours") return `${safeAmount} hora${safeAmount > 1 ? "s" : ""}`;
    return `${safeAmount} minuto${safeAmount > 1 ? "s" : ""}`;
}

export function formatElapsedTime(from: string | null | undefined, now = new Date()) {
    const diffMs = Math.max(0, now.getTime() - toTimestamp(from));
    const diffMinutes = Math.max(1, Math.floor(diffMs / 60000));
    if (diffMinutes < 60) return `${diffMinutes} min`;
    if (diffMinutes < 24 * 60) {
        const hours = Math.floor(diffMinutes / 60);
        return `${hours} h`;
    }
    const days = Math.floor(diffMinutes / (24 * 60));
    return `${days} d`;
}

export function resolveFollowUpDirection(lastMessageFromMe?: boolean | null): CrmFollowUpDirection | null {
    if (lastMessageFromMe === true) return "without_reply";
    if (lastMessageFromMe === false) return "without_response";
    return null;
}

export function getFollowUpDirectionLabel(direction: CrmFollowUpDirection) {
    return direction === "without_reply" ? "Sem responder" : "Sem resposta";
}

function buildCycleKey(ruleId: string, conversation: FollowUpConversationSnapshot, direction: CrmFollowUpDirection) {
    return [
        ruleId,
        conversation.id,
        conversation.assignedUserId ?? "",
        conversation.lastAt ?? "",
        direction,
    ].join(":");
}

function shouldResolveNotification(
    notification: CrmFollowUpNotification,
    ruleById: Map<string, CrmFollowUp>,
    conversationById: Map<string, FollowUpConversationSnapshot>,
) {
    if (notification.resolvedAt) return false;
    const rule = ruleById.get(notification.followUpId);
    if (!rule || !rule.isActive) return true;

    const conversation = conversationById.get(notification.conversationId);
    if (!conversation) return true;
    if (!conversation.assignedUserId || conversation.assignedUserId !== notification.assignedUserId) return true;
    if ((conversation.lastAt ?? "") !== notification.lastMessageAt) return true;

    const direction = resolveFollowUpDirection(conversation.lastMessageFromMe);
    return direction !== notification.direction;
}

export function evaluateFollowUpNotifications({
    followUps,
    notifications,
    conversations,
    now = new Date(),
}: {
    followUps: CrmFollowUp[];
    notifications: CrmFollowUpNotification[];
    conversations: FollowUpConversationSnapshot[];
    now?: Date;
}) {
    const activeRules = followUps.filter((rule) => rule.isActive);
    const ruleById = new Map(activeRules.map((rule) => [rule.id, rule]));
    const conversationById = new Map(conversations.map((conversation) => [conversation.id, conversation]));
    const resolvedAt = now.toISOString();

    const nextNotifications = notifications.map((notification) =>
        shouldResolveNotification(notification, ruleById, conversationById)
            ? { ...notification, resolvedAt }
            : notification
    );

    const existingCycleKeys = new Set(nextNotifications.map((notification) => notification.cycleKey));
    const created: CrmFollowUpNotification[] = [];

    for (const rule of activeRules) {
        const thresholdMinutes = getFollowUpDelayMinutes(rule.delayAmount, rule.delayUnit);
        for (const conversation of conversations) {
            if (!conversation.assignedUserId || !conversation.lastAt) continue;
            const lastMessageAt = toTimestamp(conversation.lastAt);
            if (!lastMessageAt) continue;

            const direction = resolveFollowUpDirection(conversation.lastMessageFromMe);
            if (!direction) continue;
            if (now.getTime() - lastMessageAt < thresholdMinutes * 60000) continue;

            const cycleKey = buildCycleKey(rule.id, conversation, direction);
            if (existingCycleKeys.has(cycleKey)) continue;

            const contactName = (conversation.displayName ?? "").trim() || conversation.phone;
            const notification: CrmFollowUpNotification = {
                id: cycleKey,
                cycleKey,
                followUpId: rule.id,
                followUpTitle: rule.title,
                followUpMessage: rule.message,
                conversationId: conversation.id,
                contactName,
                contactPhone: conversation.phone,
                assignedUserId: conversation.assignedUserId,
                assignedUserName: (conversation.assignedUserName ?? "").trim(),
                direction,
                lastMessageAt: conversation.lastAt,
                lastMessagePreview: String(conversation.lastMessage ?? "").trim(),
                thresholdMinutes,
                createdAt: resolvedAt,
                readAt: null,
                resolvedAt: null,
            };

            created.push(notification);
            nextNotifications.push(notification);
            existingCycleKeys.add(cycleKey);
        }
    }

    nextNotifications.sort((a, b) => toTimestamp(b.createdAt) - toTimestamp(a.createdAt));

    return {
        notifications: nextNotifications,
        created,
    };
}

export function listOpenNotificationsForUser(notifications: CrmFollowUpNotification[], userId?: string | null) {
    const normalizedUserId = String(userId ?? "").trim();
    if (!normalizedUserId) return [];
    return notifications
        .filter((notification) => notification.assignedUserId === normalizedUserId && !notification.resolvedAt)
        .sort((a, b) => toTimestamp(b.createdAt) - toTimestamp(a.createdAt));
}
