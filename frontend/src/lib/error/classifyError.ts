import { ApiError } from "@/lib/api/client";
import type { ErrorClassification, ErrorContext } from "./types";

const QUERY_INLINE_CODES = new Set(["40000", "40010", "40011", "40020", "40200", "40400"]);
const GLOBAL_DIALOG_CODES = new Set(["40030", "40300", "40301", "40910", "50000", "50001", "50002", "50300"]);

function extractCode(error: unknown) {
    if (error instanceof ApiError && error.code) {
        return String(error.code);
    }
    return undefined;
}

function extractMessage(error: unknown, fallbackMessage?: string) {
    if (error instanceof Error && error.message.trim()) {
        return error.message.trim();
    }
    return fallbackMessage || "请求失败，请稍后重试";
}

export function classifyError(error: unknown, context: ErrorContext): ErrorClassification {
    const code = extractCode(error);
    const message = extractMessage(error, context.fallbackMessage);
    const title = context.errorTitle || context.fallbackTitle || "请求失败";

    if (context.errorMode) {
        return { mode: context.errorMode, code, title, message };
    }

    if (code === "40100" || (error instanceof ApiError && error.status === 401)) {
        return {
            mode: "redirect_login",
            code,
            title: "登录已失效",
            message: "登录状态已过期，请重新登录后继续操作。",
        };
    }

    if (context.source === "query") {
        if (code && GLOBAL_DIALOG_CODES.has(code)) {
            return { mode: "global_dialog", code, title, message };
        }
        if (code && QUERY_INLINE_CODES.has(code)) {
            return { mode: "inline_status_card", code, title, message };
        }
        return { mode: "inline_status_card", code, title, message };
    }

    if (code && GLOBAL_DIALOG_CODES.has(code)) {
        return { mode: "global_dialog", code, title, message };
    }

    return { mode: "global_dialog", code, title, message };
}
