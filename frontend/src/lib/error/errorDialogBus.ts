import type { ErrorDialogPayload } from "./types";

type GlobalErrorHandler = (payload: ErrorDialogPayload) => void;

let handler: GlobalErrorHandler | null = null;

export function registerGlobalErrorHandler(nextHandler: GlobalErrorHandler) {
    handler = nextHandler;
    return () => {
        if (handler === nextHandler) {
            handler = null;
        }
    };
}

export function emitGlobalError(payload: ErrorDialogPayload) {
    handler?.(payload);
}
