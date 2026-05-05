import { useCallback, useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { ArrowUp, Plus, Settings, X } from "lucide-react";
import { TextArea } from "@/components/base/field";
import { useDocumentsQuery } from "@/lib/api/hooks";
import type { DocumentRecord } from "@/lib/api/types";

const STORAGE_KEY = "create-page-draft";

function loadDraft(): { selectedIds: string[]; note: string } {
    try {
        return JSON.parse(localStorage.getItem(STORAGE_KEY) || "null") || { selectedIds: [], note: "" };
    } catch {
        return { selectedIds: [], note: "" };
    }
}

function saveDraft(selectedIds: string[], note: string) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ selectedIds, note }));
}

function clearDraft() {
    localStorage.removeItem(STORAGE_KEY);
}

export function CreatePage() {
    const [draft] = useState(loadDraft);
    const documentsQuery = useDocumentsQuery();
    const [selectedDocumentIds, setSelectedDocumentIds] = useState<string[]>(draft.selectedIds);
    const [dialogOpen, setDialogOpen] = useState(false);

    const form = useForm({
        defaultValues: {
            note: draft.note,
        },
    });

    const uploadedDocuments = documentsQuery.data ?? [];
    const selectedDocuments = useMemo(
        () => uploadedDocuments.filter((item) => selectedDocumentIds.includes(item.id)),
        [selectedDocumentIds, uploadedDocuments],
    );

    const hasDocuments = selectedDocuments.length > 0;

    // Persist draft on changes
    useEffect(() => {
        const sub = form.watch((value) => {
            saveDraft(selectedDocumentIds, value.note || "");
        });
        return () => sub.unsubscribe();
    }, [selectedDocumentIds, form]);

    const handleDialogToggle = (id: string) => {
        setSelectedDocumentIds((current) => {
            if (current.includes(id)) {
                return current.filter((item) => item !== id);
            }
            return [...current, id];
        });
    };

    const openDialog = useCallback(() => setDialogOpen(true), []);
    const closeDialog = useCallback(() => setDialogOpen(false), []);

    return (
        <div className="page-frame">
            <div className="create-page">
                <div className="create-page__main">
                    <p className="create-page__empty">请添加笔记资料，并输入需求给 QA Agent</p>

                    {hasDocuments ? (
                        <div className="create-page__docs-grid">
                            {selectedDocuments.map((doc) => (
                                <div key={doc.id} className="create-page__doc-card">
                                    <button
                                        type="button"
                                        className="create-page__doc-card-remove"
                                        onClick={() => setSelectedDocumentIds((current) => current.filter((id) => id !== doc.id))}
                                        aria-label="移除资料"
                                    >
                                        <X size={14} />
                                    </button>
                                    <div className="create-page__doc-card-name">{doc.fileName}</div>
                                    <div className="create-page__doc-card-meta">{doc.fileType || "未知类型"}</div>
                                </div>
                            ))}
                        </div>
                    ) : null}
                </div>

                <div className="create-page__bottom">
                    <details className="create-page__preview-ref">
                        <summary>生成流程预览（参考）</summary>
                        <div className="timeline">
                            {stageSteps.map((activity) => (
                                <div key={activity.key} className="timeline__item">
                                    <div className="timeline__meta">待接入</div>
                                    <div className="timeline__title">{activity.label}</div>
                                    <div className="timeline__copy">{activity.copy}</div>
                                </div>
                            ))}
                        </div>
                    </details>

                    <form onSubmit={form.handleSubmit(() => {
                        if (selectedDocumentIds.length === 0) {
                            alert("请先添加本次要使用的资料。");
                            return;
                        }
                        alert("接口尚未实现");
                    })} style={{ width: "100%", maxWidth: 720 }}>
                        <div className="create-page__input-area">
                            <TextArea
                                {...form.register("note")}
                                placeholder="输入你的需求..."
                                className="create-page__input"
                            />
                        </div>

                        <div className="create-page__actions">
                            <div className="create-page__actions-left">
                                <button type="button" className="create-page__action-btn" onClick={openDialog}>
                                    <Plus size={18} />
                                    添加资料
                                </button>
                                <button type="button" className="create-page__action-btn create-page__action-btn--icon" onClick={() => alert("接口尚未实现")}>
                                    <Settings size={18} />
                                </button>
                            </div>
                            <button type="submit" className="create-page__send-btn" aria-label="发送">
                                <ArrowUp size={20} strokeWidth={2.5} />
                            </button>
                        </div>
                    </form>
                </div>
            </div>

            {dialogOpen ? (
                <DocumentSelectDialog
                    documents={uploadedDocuments}
                    selectedIds={selectedDocumentIds}
                    onToggle={handleDialogToggle}
                    onClose={closeDialog}
                />
            ) : null}
        </div>
    );
}

function DocumentSelectDialog({
    documents,
    selectedIds,
    onToggle,
    onClose,
}: {
    documents: DocumentRecord[];
    selectedIds: string[];
    onToggle: (id: string) => void;
    onClose: () => void;
}) {
    return (
        <div className="doc-select-dialog" onClick={onClose}>
            <div className="doc-select-dialog__card" onClick={(e) => e.stopPropagation()}>
                <div className="doc-select-dialog__header">
                    <h3 className="doc-select-dialog__title">选择资料</h3>
                    <button className="doc-select-dialog__close" onClick={onClose} aria-label="关闭">
                        <X size={18} />
                    </button>
                </div>

                <div className="doc-select-dialog__body">
                    {documents.length === 0 ? (
                        <p style={{ gridColumn: "1/-1", textAlign: "center", color: "var(--ink-faint)", fontSize: 14, margin: 0, padding: 24 }}>
                            资料库为空，请先上传资料
                        </p>
                    ) : (
                        documents.map((doc) => {
                            const selected = selectedIds.includes(doc.id);
                            return (
                                <button
                                    key={doc.id}
                                    type="button"
                                    className={`doc-select-dialog__item ${selected ? "doc-select-dialog__item--selected" : ""}`}
                                    onClick={() => onToggle(doc.id)}
                                >
                                    <span className="doc-select-dialog__check">
                                        {selected ? <CheckSmall /> : null}
                                    </span>
                                    <span className="doc-select-dialog__item-info">
                                        <span className="doc-select-dialog__item-name">{doc.fileName}</span>
                                        <span className="doc-select-dialog__item-meta">{doc.fileType || "未知类型"}</span>
                                    </span>
                                </button>
                            );
                        })
                    )}
                </div>
            </div>
        </div>
    );
}

function CheckSmall() {
    return (
        <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <path d="M2.5 6L5 8.5L9.5 3.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
    );
}

const stageSteps = [
    { key: "PARSING", label: "资料解析", copy: "确认本轮资料范围，准备进入生成流程。" },
    { key: "PLANNING", label: "规划模块", copy: "按模块和题量分配生成计划。" },
    { key: "GENERATING", label: "检索起草", copy: "基于 RAG 证据起草结构化问答项。" },
    { key: "VALIDATING", label: "结构校验", copy: "检查 schema、证据引用和字段完整性。" },
    { key: "OPTIMIZING", label: "结果收口", copy: "准备落库并生成正式问答集。" },
    { key: "COMPLETED", label: "生成完成", copy: "问答集已经可进入仓库和练习链路。" },
] as const;
