import type { ReactNode } from "react";
import { BaseButton } from "./button";

type ErrorDialogProps = {
    open: boolean;
    title?: string;
    message: ReactNode;
    confirmLabel?: string;
    onConfirm: () => void;
};

export function ErrorDialog({
    open,
    title = "错误",
    message,
    confirmLabel = "确定",
    onConfirm,
}: ErrorDialogProps) {
    if (!open) return null;

    return (
        <div className="quiz-action-sheet" role="presentation" onClick={onConfirm}>
            <div
                className="modal-card error-dialog"
                role="dialog"
                aria-modal="true"
                aria-label={title}
                style={{ width: "min(480px, 100%)", maxHeight: "none" }}
                onClick={(e) => e.stopPropagation()}
            >
                <div className="modal-card__header">
                    <h3 className="modal-card__title">{title}</h3>
                </div>
                <div className="modal-card__body" style={{ overflow: "visible" }}>
                    <div className="error-dialog__message">
                        {message}
                    </div>
                </div>
                <div className="modal-card__footer">
                    <BaseButton variant="primary" type="button" onClick={onConfirm}>
                        {confirmLabel}
                    </BaseButton>
                </div>
            </div>
        </div>
    );
}
