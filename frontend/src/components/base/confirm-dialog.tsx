import type { ReactNode } from "react";
import { BaseButton } from "./button";

type ConfirmDialogProps = {
    open: boolean;
    title: string;
    message: ReactNode;
    confirmLabel?: string;
    cancelLabel?: string;
    variant?: "default" | "danger";
    loading?: boolean;
    onConfirm: () => void;
    onCancel: () => void;
};

export function ConfirmDialog({
    open,
    title,
    message,
    confirmLabel = "确认",
    cancelLabel = "取消",
    variant = "default",
    loading = false,
    onConfirm,
    onCancel,
}: ConfirmDialogProps) {
    if (!open) return null;

    return (
        <div className="quiz-action-sheet" role="presentation" onClick={onCancel}>
            <div
                className="modal-card"
                role="dialog"
                aria-modal="true"
                aria-label={title}
                style={{ width: "min(480px, 100%)", maxHeight: "none" }}
                onClick={(e) => e.stopPropagation()}
            >
                <div className="modal-card__header">
                    <h3 className="modal-card__title">{title}</h3>
                </div>
                <div
                    className="modal-card__body"
                    style={{ overflow: "visible" }}
                >
                    <div
                        style={{
                            fontFamily: "var(--font-sans)",
                            fontSize: 14,
                            lineHeight: 1.7,
                            color: variant === "danger" ? "#8f4c39" : "var(--ink-soft)",
                        }}
                    >
                        {message}
                    </div>
                </div>
                <div className="modal-card__footer">
                    <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                        <BaseButton
                            variant={variant === "danger" ? "primary" : "primary"}
                            type="button"
                            disabled={loading}
                            onClick={onConfirm}
                            style={
                                variant === "danger"
                                    ? { background: "#8f4c39", color: "#fff" }
                                    : undefined
                            }
                        >
                            {loading ? "处理中" : confirmLabel}
                        </BaseButton>
                        <BaseButton variant="ghost" type="button" onClick={onCancel}>
                            {cancelLabel}
                        </BaseButton>
                    </div>
                </div>
            </div>
        </div>
    );
}
