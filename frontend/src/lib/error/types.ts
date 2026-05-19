export type ErrorDisplayMode = "inline_status_card" | "global_dialog" | "silent" | "redirect_login";

export type ErrorSource = "query" | "mutation" | "action" | "unimplemented";

export type ErrorContext = {
    source: ErrorSource;
    fallbackTitle?: string;
    fallbackMessage?: string;
    errorMode?: ErrorDisplayMode;
    errorTitle?: string;
};

export type ErrorClassification = {
    mode: ErrorDisplayMode;
    code?: string;
    title: string;
    message: string;
};

export type ErrorHandlingMeta = {
    errorMode?: ErrorDisplayMode;
    errorTitle?: string;
};

export type ErrorDialogPayload = {
    code?: string;
    title: string;
    message: string;
};
