export type PublicLeadTrackingParams = {
    sourceType: string | null;
    sourceReference: string | null;
};

export function readPublicLeadTracking(searchParams: URLSearchParams | ReadonlyURLSearchParamsLike | null) {
    if (!searchParams) {
        return {
            sourceType: null,
            sourceReference: null,
        };
    }

    const sourceType = searchParams.get("source") || searchParams.get("origin");
    const sourceReference = searchParams.get("ref") || searchParams.get("campaign");

    return {
        sourceType: sourceType ? sourceType.trim() : null,
        sourceReference: sourceReference ? sourceReference.trim() : null,
    };
}

export function withPublicLeadTracking(path: string, params: PublicLeadTrackingParams) {
    if (!params.sourceReference) return path;

    const url = new URL(path, "http://localhost");
    url.searchParams.set("source", params.sourceType || "influencer");
    url.searchParams.set("ref", params.sourceReference);

    return `${url.pathname}${url.search}`;
}

export function buildTrackedWhatsappHref(
    phone: string | null | undefined,
    message: string,
    _params: PublicLeadTrackingParams
) {
    const digits = String(phone ?? "").replace(/\D/g, "");
    if (!digits) return null;

    const sanitizedMessage = message
        .split("\n")
        .filter((line) => !line.trim().toLowerCase().startsWith("origem"))
        .join("\n")
        .trim();

    return `https://wa.me/${digits}?text=${encodeURIComponent(sanitizedMessage)}`;
}

export function getPublicLeadSessionId() {
    if (typeof window === "undefined") return null;

    const storageKey = "ioauto-public-lead-session-id";
    const existing = window.sessionStorage.getItem(storageKey);
    if (existing) return existing;

    const generated = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    window.sessionStorage.setItem(storageKey, generated);
    return generated;
}

export function trackPublicLeadEvent(
    companyId: string,
    payload: {
        vehicleId?: string | null;
        eventType: string;
        sourceType?: string | null;
        sourceReference?: string | null;
        pagePath?: string | null;
        sourceUrl?: string | null;
    }
) {
    if (!payload.sourceReference || typeof window === "undefined") return;

    const body = JSON.stringify({
        vehicleId: payload.vehicleId ?? null,
        eventType: payload.eventType,
        sourceType: payload.sourceType ?? "influencer",
        sourceReference: payload.sourceReference,
        pagePath: payload.pagePath ?? window.location.pathname,
        sourceUrl: payload.sourceUrl ?? window.location.href,
        sessionId: getPublicLeadSessionId(),
    });

    fetch(`/api/public/estoque/${companyId}/track`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body,
        keepalive: true,
    }).catch(() => undefined);
}

type ReadonlyURLSearchParamsLike = {
    get(name: string): string | null;
};
