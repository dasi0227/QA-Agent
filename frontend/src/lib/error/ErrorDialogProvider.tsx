import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { ErrorDialog } from "@/components/base/error-dialog";
import { registerGlobalErrorHandler } from "./errorDialogBus";
import type { ErrorDialogPayload } from "./types";

type ErrorDialogContextValue = {
    showErrorDialog: (payload: ErrorDialogPayload) => void;
    showUnimplemented: (message?: string) => void;
};

const ErrorDialogContext = createContext<ErrorDialogContextValue | null>(null);

const DEDUP_WINDOW_MS = 1600;

export function ErrorDialogProvider({ children }: { children: ReactNode }) {
    const [activeError, setActiveError] = useState<ErrorDialogPayload | null>(null);
    const [queue, setQueue] = useState<ErrorDialogPayload[]>([]);
    const recentErrorMapRef = useRef<Map<string, number>>(new Map());

    const enqueueError = useCallback((payload: ErrorDialogPayload) => {
        const key = `${payload.code || ""}|${payload.title}|${payload.message}`;
        const now = Date.now();
        const lastShownAt = recentErrorMapRef.current.get(key) || 0;
        if (now - lastShownAt <= DEDUP_WINDOW_MS) {
            return;
        }
        recentErrorMapRef.current.set(key, now);

        if (!activeError) {
            setActiveError(payload);
            return;
        }
        setQueue((current) => [...current, payload]);
    }, [activeError]);

    useEffect(() => registerGlobalErrorHandler(enqueueError), [enqueueError]);

    const consumeNext = useCallback(() => {
        setQueue((current) => {
            if (!current.length) {
                setActiveError(null);
                return current;
            }
            const [next, ...rest] = current;
            setActiveError(next);
            return rest;
        });
    }, []);

    const contextValue = useMemo<ErrorDialogContextValue>(() => ({
        showErrorDialog: enqueueError,
        showUnimplemented: (message) => {
            enqueueError({
                title: "功能未开放",
                message: message || "该功能尚未实现，暂时无法使用。",
            });
        },
    }), [enqueueError]);

    return (
        <ErrorDialogContext.Provider value={contextValue}>
            {children}
            <ErrorDialog
                open={Boolean(activeError)}
                title={activeError?.title || "错误"}
                message={activeError?.message || ""}
                onConfirm={consumeNext}
            />
        </ErrorDialogContext.Provider>
    );
}

export function useGlobalErrorDialog() {
    const context = useContext(ErrorDialogContext);
    if (!context) {
        throw new Error("useGlobalErrorDialog must be used within ErrorDialogProvider");
    }
    return context;
}
