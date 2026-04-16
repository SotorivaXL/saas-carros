function trimTrailingSlash(value: string) {
    return value.replace(/\/+$/, "");
}

export function getServerAppBase(request?: Request) {
    const configuredBase = process.env.NEXT_PUBLIC_APP_URL?.trim();
    if (configuredBase) return trimTrailingSlash(configuredBase);

    const forwardedProto = request?.headers.get("x-forwarded-proto")?.split(",")[0]?.trim();
    const forwardedHost = request?.headers.get("x-forwarded-host")?.split(",")[0]?.trim();
    const forwardedPort = request?.headers.get("x-forwarded-port")?.split(",")[0]?.trim();
    const host = request?.headers.get("host")?.trim();

    if (forwardedHost || host) {
        const protocol =
            forwardedProto ||
            (forwardedPort === "443" ? "https" : forwardedPort === "80" ? "http" : request ? new URL(request.url).protocol.replace(":", "") : "http");
        return `${protocol}://${forwardedHost || host}`;
    }

    if (request) return trimTrailingSlash(new URL(request.url).origin);

    throw new Error("NEXT_PUBLIC_APP_URL must be configured");
}
