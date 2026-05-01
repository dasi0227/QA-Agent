export type AuthRedirectLocation = {
    pathname: string;
    search?: string;
    hash?: string;
};

const AUTH_PATHS = new Set(["/login", "/register"]);

export function resolveAuthRedirectTarget(from: AuthRedirectLocation | null | undefined, fallback: string) {
    if (!from?.pathname || AUTH_PATHS.has(from.pathname)) {
        return fallback;
    }

    return `${from.pathname}${from.search ?? ""}${from.hash ?? ""}`;
}
