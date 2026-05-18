import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { BaseButton } from "@/components/base/button";
import { GlassCard } from "@/components/base/card";
import { TextArea } from "@/components/base/field";
import { ConfirmDialog } from "@/components/base/confirm-dialog";
import {
    useDeleteDocumentMutation,
    useDocumentQuery,
    useDocumentsQuery,
    useUpdateDocumentMutation,
    useUploadDocumentMutation,
} from "@/lib/api/hooks";
import { MarkdownRenderer } from "@/lib/markdown";
import { cn } from "@/lib/cn";

const compactDateFormatter = new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
});

function formatCompactDateTime(value?: string) {
    if (!value) {
        return "暂无";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }
    return compactDateFormatter.format(date).split("/").join("-");
}

export function DocumentPage() {
    const navigate = useNavigate();
    const [activeDocumentId, setActiveDocumentId] = useState("");
    const [documentEditorMode, setDocumentEditorMode] = useState<"view" | "edit">("view");
    const [documentDraft, setDocumentDraft] = useState("");
    const [deleteDocDialogOpen, setDeleteDocDialogOpen] = useState(false);

    const documentsQuery = useDocumentsQuery();
    const uploadDocumentMutation = useUploadDocumentMutation();
    const uploadFileInputRef = useRef<HTMLInputElement>(null);

    const activeDocumentIdValue = activeDocumentId || documentsQuery.data?.[0]?.id || "";
    const selectedDocumentQuery = useDocumentQuery(activeDocumentIdValue);
    const deleteDocumentMutation = useDeleteDocumentMutation();
    const updateDocumentMutation = useUpdateDocumentMutation();
    const selectedDocumentId = selectedDocumentQuery.data?.id ?? "";

    const hasDocuments = (documentsQuery.data?.length ?? 0) > 0;
    const documentErrorMessage = documentsQuery.error instanceof Error ? documentsQuery.error.message : "";

    useEffect(() => {
        if (!activeDocumentId && documentsQuery.data?.[0]?.id) {
            setActiveDocumentId(documentsQuery.data[0].id);
        }
    }, [activeDocumentId, documentsQuery.data]);

    useEffect(() => {
        if (!selectedDocumentQuery.data) {
            setDocumentDraft("");
            setDocumentEditorMode("view");
            return;
        }
        setDocumentDraft(selectedDocumentQuery.data.rawContent || "");
        setDocumentEditorMode("view");
    }, [selectedDocumentId]);

    const selectedDocumentUpdatedAt = selectedDocumentQuery.data?.updatedAt || selectedDocumentQuery.data?.createdAt || "";
    const selectedDocumentUseCount = selectedDocumentQuery.data?.referenceCount ?? 0;
    const documentBody = documentEditorMode === "edit"
        ? documentDraft
        : (selectedDocumentQuery.data?.rawContent || "");

    const handleStartDocumentEdit = () => {
        if (!selectedDocumentQuery.data) return;
        setDocumentDraft(selectedDocumentQuery.data.rawContent || "");
        setDocumentEditorMode("edit");
    };

    const handleCancelDocumentEdit = () => {
        if (!selectedDocumentQuery.data) return;
        setDocumentDraft(selectedDocumentQuery.data.rawContent || "");
        setDocumentEditorMode("view");
    };

    const handleSaveDocumentEdit = async () => {
        if (!selectedDocumentQuery.data) return;
        await updateDocumentMutation.mutateAsync({
            ...selectedDocumentQuery.data,
            rawContent: documentDraft,
        });
        setDocumentEditorMode("view");
    };

    return (
        <div className="page-frame">
            <div className="layout-two-col repository-layout">
                <aside className="sidebar">
                    <div className="repository-mode-switch" style={{ marginBottom: 18 }}>
                        <button className="choice-btn" type="button" onClick={() => navigate("/repository/qa-set")}>问答集</button>
                        <button className="choice-btn choice-btn--active" type="button">资料库</button>
                        <button className="choice-btn" type="button" disabled title="题目详情页自动切换">题目表</button>
                    </div>
                    <div className="tree">
                        <div className="sidebar__upload-area sidebar__action-area">
                            <input
                                ref={uploadFileInputRef}
                                type="file"
                                accept=".md,.txt,.json,.csv,.yaml,.yml,.xml,.html"
                                style={{ display: "none" }}
                                onChange={async (e) => {
                                    const file = e.target.files?.[0];
                                    if (!file) return;
                                    try {
                                        await uploadDocumentMutation.mutateAsync(file);
                                    } catch { /* handled by mutation */ }
                                    e.target.value = "";
                                }}
                            />
                            <button
                                type="button"
                                className="sidebar__upload-btn"
                                onClick={() => uploadFileInputRef.current?.click()}
                                disabled={uploadDocumentMutation.isPending}
                            >
                                {uploadDocumentMutation.isPending ? "上传中..." : "新增资料"}
                            </button>
                        </div>
                        <div className="subtree tree">
                            {documentsQuery.isLoading ? <div className="tree-item">加载中...</div> : null}
                            {documentsQuery.isError ? (
                                <div className="tree-item" style={{ color: "var(--ink)" }}>
                                    {documentErrorMessage || "资料加载失败"}
                                </div>
                            ) : null}
                            {documentsQuery.data?.map((item) => {
                                const isActive = item.id === activeDocumentIdValue;
                                return (
                                    <button
                                        key={item.id}
                                        className={cn("tree-item", "tree-item--entry", isActive && "tree-item--active")}
                                        type="button"
                                        onClick={() => setActiveDocumentId(item.id)}
                                    >
                                        <span className="tree-item__label">{item.fileName}</span>
                                    </button>
                                );
                            })}
                            {!documentsQuery.isLoading && !documentsQuery.isError && !hasDocuments ? (
                                <div className="tree-item">暂无资料</div>
                            ) : null}
                        </div>
                    </div>
                </aside>

                <GlassCard className="panel repository-main-panel" style={{ padding: 24 }}>
                    <div className="fade-in">
                        {selectedDocumentQuery.isLoading ? (
                            <div className="qa-feedback">
                                <strong>正在加载资料</strong>
                                <div className="qa-text">从真实接口读取当前资料详情。</div>
                            </div>
                        ) : null}

                        {selectedDocumentQuery.isError ? (
                            <div className="qa-feedback">
                                <strong>资料加载失败</strong>
                                <div className="qa-text">
                                    {selectedDocumentQuery.error instanceof Error
                                        ? selectedDocumentQuery.error.message
                                        : "请稍后重试"}
                                </div>
                                <div>
                                    <BaseButton variant="soft" type="button" onClick={() => selectedDocumentQuery.refetch()}>
                                        重试
                                    </BaseButton>
                                </div>
                            </div>
                        ) : null}

                        {selectedDocumentQuery.data ? (
                            <>
                                <div className="document-detail-view fade-in">
                                    <div className="repository-detail-view__header document-detail-view__header">
                                        <div className="document-detail-view__identity">
                                            <h1 className="hero-title document-detail-view__title">
                                                {selectedDocumentQuery.data.fileName}
                                            </h1>
                                            <div className="document-detail-view__meta">
                                                <span>添加于 {formatCompactDateTime(selectedDocumentQuery.data.createdAt || selectedDocumentUpdatedAt)}</span>
                                                <span>更新于 {formatCompactDateTime(selectedDocumentUpdatedAt)}</span>
                                                <span>引用次数 {selectedDocumentUseCount} 次</span>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="document-detail-view__actions">
                                        {documentEditorMode === "edit" ? (
                                            <>
                                                <BaseButton variant="primary" type="button" onClick={handleSaveDocumentEdit}>
                                                    {updateDocumentMutation.isPending ? "保存中" : "保存更改"}
                                                </BaseButton>
                                                <BaseButton variant="soft" type="button" onClick={handleCancelDocumentEdit}>
                                                    取消
                                                </BaseButton>
                                            </>
                                        ) : (
                                            <BaseButton variant="primary" type="button" onClick={handleStartDocumentEdit}>
                                                更改
                                            </BaseButton>
                                        )}
                                        <BaseButton
                                            variant="outline"
                                            type="button"
                                            disabled={deleteDocumentMutation.isPending}
                                            onClick={() => setDeleteDocDialogOpen(true)}
                                        >
                                            {deleteDocumentMutation.isPending ? "删除中" : "删除资料"}
                                        </BaseButton>
                                    </div>

                                    <div className="document-detail-view__body">
                                        {documentEditorMode === "edit" ? (
                                            <div className="document-editor">
                                                <TextArea
                                                    className="document-editor__textarea"
                                                    value={documentDraft}
                                                    onChange={(event) => setDocumentDraft(event.target.value)}
                                                    aria-label="资料正文编辑"
                                                />
                                            </div>
                                        ) : (
                                            <MarkdownRenderer content={documentBody} className="document-markdown--doc" />
                                        )}
                                    </div>
                                </div>
                            </>
                        ) : null}

                        {!selectedDocumentQuery.isLoading && !selectedDocumentQuery.data ? (
                            <div className="qa-feedback">
                                <strong>暂无资料可预览</strong>
                            </div>
                        ) : null}
                    </div>
                </GlassCard>
            </div>

            <ConfirmDialog
                open={deleteDocDialogOpen}
                title="⚠️ 删除资料"
                variant="danger"
                message={
                    <>
                        <p style={{ margin: 0 }}>确定要删除资料「{selectedDocumentQuery.data?.fileName}」吗？</p>
                        <p style={{ margin: "10px 0 0", color: "#8f4c39", fontSize: 13, fontWeight: 600 }}>
                            删除后资料将从系统中移除，关联的问答集可能受到影响。
                        </p>
                    </>
                }
                confirmLabel="删除"
                loading={deleteDocumentMutation.isPending}
                onConfirm={async () => {
                    if (!selectedDocumentQuery.data) return;
                    await deleteDocumentMutation.mutateAsync(selectedDocumentQuery.data.id);
                    setDeleteDocDialogOpen(false);
                    setActiveDocumentId("");
                    setDocumentDraft("");
                    setDocumentEditorMode("view");
                }}
                onCancel={() => setDeleteDocDialogOpen(false)}
            />
        </div>
    );
}
