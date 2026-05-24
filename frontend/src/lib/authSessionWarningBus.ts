export type AuthSessionWarningReason = "unauthenticated" | "expired";

export type AuthSessionWarningPayload = {
    title: string;
    message: string;
    reason: AuthSessionWarningReason;
    from: string;
    countdownSeconds?: number;
};

type AuthSessionWarningHandler = (payload: AuthSessionWarningPayload) => void;

let handler: AuthSessionWarningHandler | null = null;

export function registerGlobalAuthSessionWarningHandler(nextHandler: AuthSessionWarningHandler) {
    handler = nextHandler;
    return () => {
        if (handler === nextHandler) {
            handler = null;
        }
    };
}

export function emitGlobalAuthSessionWarning(payload: AuthSessionWarningPayload) {
  handler?.(payload);
}
