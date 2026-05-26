import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ErrorDialog } from "@/components/base/error-dialog";
import {
    emitGlobalAuthSessionWarning,
    registerGlobalAuthSessionWarningHandler,
    type AuthSessionWarningPayload,
} from "@/lib/authSessionWarningBus";

const DEFAULT_COUNTDOWN_SECONDS = 3;
const AUTH_PATHS = new Set(["/login", "/register"]);

function isPublicAuthPath(pathname: string) {
    return pathname === "/" || AUTH_PATHS.has(pathname);
}

function buildLoginTarget(from: string) {
    return `/login?from=${encodeURIComponent(from)}`;
}

export function triggerAuthSessionWarning(payload: Omit<AuthSessionWarningPayload, "from">) {
    if (typeof window === "undefined") {
        return;
    }

    if (isPublicAuthPath(window.location.pathname)) {
        return;
    }

    emitGlobalAuthSessionWarning({
        ...payload,
        from: `${window.location.pathname}${window.location.search}${window.location.hash}`,
    });
}

export function AuthSessionWarningDialog() {
    const [activeWarning, setActiveWarning] = useState<AuthSessionWarningPayload | null>(null);
    const [countdown, setCountdown] = useState(DEFAULT_COUNTDOWN_SECONDS);
    const hasActiveWarningRef = useRef(false);

    const redirectToLogin = useCallback(() => {
        if (typeof window === "undefined") {
            return;
        }
        const from = activeWarning?.from || `${window.location.pathname}${window.location.search}${window.location.hash}`;
        window.location.assign(buildLoginTarget(from));
    }, [activeWarning?.from]);

    const enqueueWarning = useCallback((payload: AuthSessionWarningPayload) => {
        if (hasActiveWarningRef.current) {
            return;
        }
        hasActiveWarningRef.current = true;
        setActiveWarning(payload);
        setCountdown(payload.countdownSeconds ?? DEFAULT_COUNTDOWN_SECONDS);
    }, []);

    useEffect(() => registerGlobalAuthSessionWarningHandler(enqueueWarning), [enqueueWarning]);

    useEffect(() => {
        if (!activeWarning) {
            return;
        }

        if (countdown <= 0) {
            redirectToLogin();
            return;
        }

        const timer = window.setTimeout(() => {
            setCountdown((current) => Math.max(0, current - 1));
        }, 1000);

        return () => window.clearTimeout(timer);
    }, [activeWarning, countdown, redirectToLogin]);

    const message = useMemo(() => {
        if (!activeWarning) {
            return "";
        }

        return `${activeWarning.message} ${countdown} 秒后自动跳转到登录页。`;
    }, [activeWarning, countdown]);

    if (!activeWarning) {
        return null;
    }

    return (
        <ErrorDialog
            open
            title={activeWarning.title}
            message={message}
            confirmLabel="立即登录"
            onConfirm={redirectToLogin}
        />
    );
}
