import { useEffect, useRef, useState, type ReactNode } from "react";
import { X } from "lucide-react";
import { BaseButton } from "@/components/base/button";

export function TypeToConfirmDialog({
    open,
    title,
    message,
    confirmText,
    confirmLabel,
    loading = false,
    onConfirm,
    onCancel,
}: {
    open: boolean;
    title: string;
    message: ReactNode;
    confirmText: string;
    confirmLabel?: string;
    loading?: boolean;
    onConfirm: () => void;
    onCancel: () => void;
}) {
    const [draft, setDraft] = useState("");
    const inputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        if (open) {
            setDraft("");
            setTimeout(() => inputRef.current?.focus(), 50);
        }
    }, [open]);

    if (!open) return null;

    const matched = draft === confirmText;

    return (
        <div className="doc-select-dialog" onClick={onCancel}>
            <div className="type-to-confirm-card" onClick={(e) => e.stopPropagation()}>
                <div className="doc-select-dialog__header">
                    <h3 className="doc-select-dialog__title">{title}</h3>
                    <button className="doc-select-dialog__close" onClick={onCancel} aria-label="关闭">
                        <X size={18} />
                    </button>
                </div>
                <div className="type-to-confirm-card__body">
                    <p className="type-to-confirm-card__message">{message}</p>
                    <label className="type-to-confirm-card__field">
                        <span className="type-to-confirm-card__hint">
                            输入 <strong>{confirmText}</strong> 以确认
                        </span>
                        <input
                            ref={inputRef}
                            className="type-to-confirm-card__input"
                            value={draft}
                            onChange={(e) => setDraft(e.target.value)}
                            placeholder="请输入确认文本，注意字符间的空格"
                        />
                    </label>
                </div>
                <div className="modal-card__footer">
                    <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                        <BaseButton
                            variant="primary"
                            type="button"
                            disabled={!matched || loading}
                            onClick={onConfirm}
                            style={{ background: !matched ? undefined : "#8f4c39", color: !matched ? undefined : "#fff", borderColor: !matched ? undefined : "#8f4c39" }}
                        >
                            {loading ? "处理中" : (confirmLabel ?? "确认删除")}
                        </BaseButton>
                        <BaseButton variant="ghost" type="button" onClick={onCancel}>
                            取消
                        </BaseButton>
                    </div>
                </div>
            </div>
        </div>
    );
}
