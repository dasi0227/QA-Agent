export type AuthRedirectLocation = {
    pathname: string;
    search?: string;
    hash?: string;
};

const AUTH_PATHS = new Set(["/login", "/register"]);

export function parseAuthRedirectTarget(value: string | null | undefined): AuthRedirectLocation | null {
    if (!value) {
        return null;
    }

    const trimmed = value.trim();
    if (!trimmed) {
        return null;
    }

    if (trimmed.startsWith("/")) {
        const [pathnameAndSearch, hashPart = ""] = trimmed.split("#");
        const [pathname, searchPart = ""] = pathnameAndSearch.split("?");
        return {
            pathname,
            search: searchPart ? `?${searchPart}` : "",
            hash: hashPart ? `#${hashPart}` : "",
        };
    }

    try {
        const url = new URL(trimmed, window.location.origin);
        return {
            pathname: url.pathname,
            search: url.search,
            hash: url.hash,
        };
    } catch {
        return null;
    }
}

export function resolveAuthRedirectTarget(from: AuthRedirectLocation | null | undefined, fallback: string) {
    if (!from?.pathname || AUTH_PATHS.has(from.pathname)) {
        return fallback;
    }

    return `${from.pathname}${from.search ?? ""}${from.hash ?? ""}`;
}
