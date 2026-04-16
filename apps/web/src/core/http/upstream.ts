const DEFAULT_UPSTREAM_TIMEOUT_MS = 15_000;

function resolveUpstreamTimeoutMs() {
    const configured = Number.parseInt(process.env.UPSTREAM_FETCH_TIMEOUT_MS ?? "", 10);
    if (Number.isFinite(configured) && configured >= 1_000) return configured;
    return DEFAULT_UPSTREAM_TIMEOUT_MS;
}

export async function fetchUpstream(input: string, init: RequestInit = {}) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), resolveUpstreamTimeoutMs());

    try {
        return await fetch(input, {
            ...init,
            signal: controller.signal,
        });
    } finally {
        clearTimeout(timeoutId);
    }
}

export async function readJsonSafely<T>(response: Response) {
    const text = await response.text().catch(() => "");
    if (!text) return null as T | null;

    try {
        return JSON.parse(text) as T;
    } catch {
        return null as T | null;
    }
}
