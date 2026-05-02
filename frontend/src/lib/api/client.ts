import type { ApiResult } from "./types";
import { clearAccessToken, getAccessToken, getRefreshToken, getRememberPreference, setAuthSession } from "../auth";

const DEFAULT_API_BASE_URL = "http://localhost:8080/qa-agent/api/v1";

export class ApiError extends Error {
    status?: number;
    code?: string;
    details?: unknown;

    constructor(message: string, options?: { status?: number; code?: string; details?: unknown }) {
        super(message);
        this.name = "ApiError";
        this.status = options?.status;
        this.code = options?.code;
        this.details = options?.details;
    }
}

export function getApiBaseUrl() {
    return (import.meta.env.VITE_API_BASE_URL?.trim() || DEFAULT_API_BASE_URL).replace(/\/+$/, "");
}

type ApiRequestOptions = {
    method?: string;
    body?: BodyInit | Record<string, unknown> | FormData | null;
    query?: Record<string, string | number | boolean | null | undefined>;
    headers?: HeadersInit;
    signal?: AbortSignal;
    auth?: boolean;
    retryOnAuthFailure?: boolean;
};

function buildUrl(path: string, query?: ApiRequestOptions["query"]) {
    const normalizedPath = path.replace(/^\/+/, "");
    const url = new URL(`${getApiBaseUrl()}/${normalizedPath}`);
    if (query) {
        Object.entries(query).forEach(([key, value]) => {
            if (value === null || value === undefined || value === "") {
                return;
            }
            url.searchParams.set(key, String(value));
        });
    }
    return url;
}

function isApiResult(value: unknown): value is ApiResult<unknown> {
    return typeof value === "object" && value !== null && "code" in value && "msg" in value;
}

function parseResponseBody(text: string) {
    if (!text) {
        return null;
    }
    try {
        return JSON.parse(text);
    } catch {
        return text;
    }
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
    const headers = new Headers(options.headers);
    const token = options.auth === false ? "" : getAccessToken();
    if (token) {
        headers.set("Authorization", `Bearer ${token}`);
    }

    let body = options.body as BodyInit | undefined;
    if (options.body && !(options.body instanceof FormData) && typeof options.body === "object" && !(options.body instanceof Blob)) {
        headers.set("Content-Type", "application/json");
        body = JSON.stringify(options.body);
    }

    const response = await fetch(buildUrl(path, options.query), {
        method: options.method ?? "GET",
        headers,
        body,
        signal: options.signal,
    });

    const parsed = parseResponseBody(await response.text());
    if (!response.ok) {
        if (response.status === 401 && options.auth !== false) {
            const refreshed = options.retryOnAuthFailure === false ? false : await tryRefreshAuthSession();
            if (refreshed) {
                return apiRequest<T>(path, {
                    ...options,
                    retryOnAuthFailure: false,
                });
            }
            clearAccessToken();
        }

        const message = typeof parsed === "string" && parsed ? parsed : `请求失败（${response.status}）`;
        throw new ApiError(message, { status: response.status, details: parsed });
    }

    if (isApiResult(parsed)) {
        if (parsed.code !== 0) {
            throw new ApiError(parsed.msg || "请求失败", {
                status: response.status,
                code: String(parsed.code),
                details: parsed,
            });
        }
        return parsed.data as T;
    }

    return parsed as T;
}

let refreshingPromise: Promise<boolean> | null = null;

async function tryRefreshAuthSession() {
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
        return false;
    }
    if (!refreshingPromise) {
        refreshingPromise = (async () => {
            try {
                const refreshed = await apiRequest<{ accessToken?: string; refreshToken?: string }>(
                    "/auth/refresh",
                    {
                        method: "POST",
                        body: { refreshToken },
                        auth: false,
                        retryOnAuthFailure: false,
                    },
                );
                const nextAccessToken = refreshed.accessToken ?? refreshed.token ?? "";
                const nextRefreshToken = refreshed.refreshToken ?? refreshToken;
                if (!nextAccessToken || !nextRefreshToken) {
                    clearAccessToken();
                    return false;
                }
                setAuthSession({
                    token: nextAccessToken,
                    refreshToken: nextRefreshToken,
                    remember: getRememberPreference(),
                });
                return true;
            } catch {
                clearAccessToken();
                return false;
            } finally {
                refreshingPromise = null;
            }
        })();
    }
    return refreshingPromise;
}
