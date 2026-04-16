export function getServerApiBase() {
    const internalBase = process.env.API_INTERNAL_BASE?.trim();
    if (internalBase) return internalBase;

    const publicBase = process.env["NEXT_PUBLIC_API_BASE"]?.trim();
    if (publicBase) return publicBase;

    throw new Error("API_INTERNAL_BASE or NEXT_PUBLIC_API_BASE must be configured");
}
