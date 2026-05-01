import { useSyncExternalStore } from "react";
import type { AuthUser } from "./api/types";
import { queryClient } from "./queryClient";

type AuthStatus = "loading" | "authenticated" | "unauthenticated";

type AuthSnapshot = {
    token: string;
    refreshToken: string;
    remember: boolean;
    user: AuthUser | null;
    status: AuthStatus;
};

const AUTH_TOKEN_KEY = "qa-agent.auth.token";
const AUTH_REFRESH_TOKEN_KEY = "qa-agent.auth.refresh-token";
const AUTH_REMEMBER_KEY = "qa-agent.auth.remember";

const listeners = new Set<() => void>();

function isBrowser() {
    return typeof window !== "undefined";
}

function sameUser(left: AuthUser | null, right: AuthUser | null) {
    if (left === right) {
        return true;
    }
    if (!left || !right) {
        return false;
    }
    return (
        left.id === right.id
        && left.username === right.username
        && left.email === right.email
        && left.displayName === right.displayName
        && left.status === right.status
        && left.profileCompleted === right.profileCompleted
    );
}

function readToken(storage: Storage | undefined) {
    if (!storage) {
        return "";
    }
    return storage.getItem(AUTH_TOKEN_KEY) ?? "";
}

function readRefreshToken(storage: Storage | undefined) {
    if (!storage) {
        return "";
    }
    return storage.getItem(AUTH_REFRESH_TOKEN_KEY) ?? "";
}

function readRememberPreference() {
    if (!isBrowser()) {
        return true;
    }
    const value = window.localStorage.getItem(AUTH_REMEMBER_KEY);
    if (value === null) {
        return true;
    }
    return value === "true";
}

function readStoredSession() {
    if (!isBrowser()) {
        return { token: "", refreshToken: "", remember: true };
    }

    const sessionToken = readToken(window.sessionStorage);
    const sessionRefreshToken = readRefreshToken(window.sessionStorage);
    if (sessionToken) {
        return { token: sessionToken, refreshToken: sessionRefreshToken, remember: false };
    }

    const localToken = readToken(window.localStorage);
    const localRefreshToken = readRefreshToken(window.localStorage);
    if (localToken) {
        return { token: localToken, refreshToken: localRefreshToken, remember: true };
    }

    return { token: "", refreshToken: "", remember: readRememberPreference() };
}

function persistStoredSession(token: string, refreshToken: string, remember: boolean) {
    if (!isBrowser()) {
        return;
    }

    window.sessionStorage.removeItem(AUTH_TOKEN_KEY);
    window.sessionStorage.removeItem(AUTH_REFRESH_TOKEN_KEY);
    window.localStorage.removeItem(AUTH_TOKEN_KEY);
    window.localStorage.removeItem(AUTH_REFRESH_TOKEN_KEY);
    window.localStorage.setItem(AUTH_REMEMBER_KEY, String(remember));

    if (!token) {
        return;
    }

    const storage = remember ? window.localStorage : window.sessionStorage;
    storage.setItem(AUTH_TOKEN_KEY, token);
    if (refreshToken) {
        storage.setItem(AUTH_REFRESH_TOKEN_KEY, refreshToken);
    }
}

function clearStoredSession() {
    if (!isBrowser()) {
        return;
    }

    window.sessionStorage.removeItem(AUTH_TOKEN_KEY);
    window.sessionStorage.removeItem(AUTH_REFRESH_TOKEN_KEY);
    window.localStorage.removeItem(AUTH_TOKEN_KEY);
    window.localStorage.removeItem(AUTH_REFRESH_TOKEN_KEY);
}

const initialSession = readStoredSession();

let snapshot: AuthSnapshot = {
    token: initialSession.token,
    refreshToken: initialSession.refreshToken,
    remember: initialSession.remember,
    user: null,
    status: initialSession.token ? "loading" : "unauthenticated",
};

function emit(next: Partial<AuthSnapshot>) {
    const updated: AuthSnapshot = {
        token: next.token ?? snapshot.token,
        refreshToken: next.refreshToken ?? snapshot.refreshToken,
        remember: next.remember ?? snapshot.remember,
        user: next.user === undefined ? snapshot.user : next.user,
        status: next.status ?? snapshot.status,
    };

    const hasChanged =
        updated.token !== snapshot.token
        || updated.refreshToken !== snapshot.refreshToken
        || updated.remember !== snapshot.remember
        || updated.status !== snapshot.status
        || !sameUser(updated.user, snapshot.user);

    if (!hasChanged) {
        return;
    }

    snapshot = updated;
    listeners.forEach((listener) => listener());
}

export function subscribeAuthState(listener: () => void) {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
}

export function getAuthState() {
    return snapshot;
}

export function useAuthState() {
    return useSyncExternalStore(subscribeAuthState, getAuthState, getAuthState);
}

export function getAccessToken() {
    return snapshot.token;
}

export function getRefreshToken() {
    return snapshot.refreshToken;
}

export function getRememberPreference() {
    return snapshot.remember;
}

export function setAuthSession({
    token,
    refreshToken,
    remember = snapshot.remember,
    user = snapshot.user,
}: {
    token: string;
    refreshToken: string;
    remember?: boolean;
    user?: AuthUser | null;
}) {
    persistStoredSession(token, refreshToken, remember);
    emit({
        token,
        refreshToken,
        remember,
        user,
        status: token ? "authenticated" : "unauthenticated",
    });
}

export function setAccessToken(token: string, remember = snapshot.remember, user: AuthUser | null = snapshot.user) {
    setAuthSession({
        token,
        refreshToken: snapshot.refreshToken,
        remember,
        user,
    });
}

export function setCurrentUser(user: AuthUser | null) {
    emit({
        user,
        status: snapshot.token ? "authenticated" : "unauthenticated",
    });
}

export function setAuthStatus(status: AuthStatus) {
    emit({ status });
}

export function clearAccessToken() {
    clearStoredSession();
    emit({
        token: "",
        refreshToken: "",
        user: null,
        status: "unauthenticated",
    });
    queryClient.clear();
}
