import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { BaseButton } from "@/components/base/button";
import { emitDasiBubble } from "@/components/dasi/DasiChatWidget";
import { GlassCard } from "@/components/base/card";
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
import { useGlobalErrorDialog } from "@/lib/error/ErrorDialogProvider";

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

function splitDocumentFileName(fileName: string) {
    const normalized = fileName.trim();
    const dotIndex = normalized.lastIndexOf(".");
    if (dotIndex <= 0) {
        return { baseName: normalized, extension: "" };
    }
    return {
        baseName: normalized.slice(0, dotIndex),
        extension: normalized.slice(dotIndex),
    };
}

export function DocumentPage() {
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const [activeDocumentId, setActiveDocumentId] = useState("");
    const [documentEditorMode, setDocumentEditorMode] = useState<"view" | "rename">("view");
    const [documentNameDraft, setDocumentNameDraft] = useState("");
    const [deleteDocDialogOpen, setDeleteDocDialogOpen] = useState(false);
    const { showErrorDialog } = useGlobalErrorDialog();

    const documentsQuery = useDocumentsQuery();
    const uploadDocumentMutation = useUploadDocumentMutation();
    const uploadFileInputRef = useRef<HTMLInputElement>(null);

    const requestedDocumentId = searchParams.get("documentId") ?? "";
    const activeDocumentIdValue = activeDocumentId || requestedDocumentId || documentsQuery.data?.[0]?.id || "";
    const selectedDocumentQuery = useDocumentQuery(activeDocumentIdValue);
    const deleteDocumentMutation = useDeleteDocumentMutation();
    const updateDocumentMutation = useUpdateDocumentMutation();
    const selectedDocumentId = selectedDocumentQuery.data?.id ?? "";

    const hasDocuments = (documentsQuery.data?.length ?? 0) > 0;
    const documentErrorMessage = documentsQuery.error instanceof Error ? documentsQuery.error.message : "";

    useEffect(() => {
        if (!documentsQuery.data?.length) {
            return;
        }
        const matchedRequestedDocument = requestedDocumentId
            ? documentsQuery.data.find((qaSetEntry) => qaSetEntry.id === requestedDocumentId)
            : null;
        if (matchedRequestedDocument && activeDocumentId !== matchedRequestedDocument.id) {
            setActiveDocumentId(matchedRequestedDocument.id);
            return;
        }
        if (!activeDocumentId && documentsQuery.data[0]?.id) {
            setActiveDocumentId(documentsQuery.data[0].id);
        }
    }, [activeDocumentId, documentsQuery.data, requestedDocumentId]);

    const handleSelectDocument = (documentId: string) => {
        setActiveDocumentId(documentId);
        setSearchParams({ documentId }, { replace: true });
    };

    useEffect(() => {
        if (!selectedDocumentQuery.data) {
            setDocumentNameDraft("");
            setDocumentEditorMode("view");
            return;
        }
        setDocumentNameDraft(splitDocumentFileName(selectedDocumentQuery.data.fileName).baseName);
        setDocumentEditorMode("view");
    }, [selectedDocumentId]);

    const selectedDocumentUpdatedAt = selectedDocumentQuery.data?.updatedAt || selectedDocumentQuery.data?.createdAt || "";
    const selectedDocumentUseCount = selectedDocumentQuery.data?.referenceCount ?? 0;
    const documentBody = selectedDocumentQuery.data?.rawContent || "";

    const handleStartDocumentEdit = () => {
        if (!selectedDocumentQuery.data) return;
        setDocumentNameDraft(splitDocumentFileName(selectedDocumentQuery.data.fileName).baseName);
        setDocumentEditorMode("rename");
    };

    const handleCancelDocumentEdit = () => {
        if (!selectedDocumentQuery.data) return;
        setDocumentNameDraft(splitDocumentFileName(selectedDocumentQuery.data.fileName).baseName);
        setDocumentEditorMode("view");
    };

    const handleSaveDocumentEdit = async () => {
        if (!selectedDocumentQuery.data) return;
        const nameOnly = documentNameDraft.trim();
        if (!nameOnly) {
            showErrorDialog({
                title: "文件名不能为空",
                message: "请输入有效的文件名后再保存。",
            });
            return;
        }
        const { baseName, extension } = splitDocumentFileName(selectedDocumentQuery.data.fileName);
        if (nameOnly === baseName) {
            setDocumentEditorMode("view");
            return;
        }
        await updateDocumentMutation.mutateAsync({
            id: selectedDocumentQuery.data.id,
            fileName: `${nameOnly}${extension}`,
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
                                accept=".md,.markdown"
                                style={{ display: "none" }}
                                onChange={async (e) => {
                                    const file = e.target.files?.[0];
                                    if (!file) return;
                                    const ext = file.name.split(".").pop()?.toLowerCase();
                                    if (ext !== "md" && ext !== "markdown") {
                                        showErrorDialog({
                                            title: "文件类型不支持",
                                            message: "仅支持上传 .md 或 .markdown 格式的资料文件，请转换后重新上传。",
                                        });
                                        e.target.value = "";
                                        return;
                                    }
                                    try {
                                        await uploadDocumentMutation.mutateAsync(file);
                                        emitDasiBubble("资料已添加，Dasi 正在后台建立索引 📄");
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
                            {documentsQuery.isLoading ? <div className="tree-qaSetEntry">加载中...</div> : null}
                            {documentsQuery.isError ? (
                                <div className="tree-qaSetEntry" style={{ color: "var(--ink)" }}>
                                    {documentErrorMessage || "资料加载失败"}
                                </div>
                            ) : null}
                            {documentsQuery.data?.map((qaSetEntry) => {
                                const isActive = qaSetEntry.id === activeDocumentIdValue;
                                const displayFileName = splitDocumentFileName(qaSetEntry.fileName).baseName || qaSetEntry.fileName;
                                return (
                                    <button
                                        key={qaSetEntry.id}
                                        className={cn("tree-qaSetEntry", "tree-qaSetEntry--entry", isActive && "tree-qaSetEntry--active")}
                                        type="button"
                                        onClick={() => handleSelectDocument(qaSetEntry.id)}
                                    >
                                        <span className="tree-item__label">{displayFileName}</span>
                                    </button>
                                );
                            })}
                            {!documentsQuery.isLoading && !documentsQuery.isError && !hasDocuments ? (
                                <div className="tree-qaSetEntry">暂无资料</div>
                            ) : null}
                        </div>
                    </div>
                </aside>

                <GlassCard className="panel repository-main-panel" style={{ padding: 24 }}>
                    <div className="fade-in">
                        {selectedDocumentQuery.isLoading ? (
                            <div className="status-card">
                                <strong>正在加载资料</strong>
                                <div className="qa-text">从真实接口读取当前资料详情。</div>
                            </div>
                        ) : null}

                        {selectedDocumentQuery.isError ? (
                            <div className="status-card">
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
                                            {documentEditorMode === "rename" ? (
                                                <input
                                                    className="input document-detail-view__title-input"
                                                    value={documentNameDraft}
                                                    onChange={(event) => setDocumentNameDraft(event.target.value)}
                                                    aria-label="资料名称"
                                                />
                                            ) : (
                                                <h1 className="hero-title document-detail-view__title">
                                                    {splitDocumentFileName(selectedDocumentQuery.data.fileName).baseName || selectedDocumentQuery.data.fileName}
                                                </h1>
                                            )}
                                            <div className="document-detail-view__meta">
                                                <span>添加于 {formatCompactDateTime(selectedDocumentQuery.data.createdAt || selectedDocumentUpdatedAt)}</span>
                                                <span>更新于 {formatCompactDateTime(selectedDocumentUpdatedAt)}</span>
                                                <span>引用次数 {selectedDocumentUseCount} 次</span>
                                                <span className={cn("document-index-status-tag", `document-index-status-tag--${(selectedDocumentQuery.data.indexStatus || "UNSOLVED").toLowerCase()}`)}>{(() => { const s = selectedDocumentQuery.data.indexStatus || "UNSOLVED"; return s === "FINISHED" ? "已索引" : s === "INDEXING" ? "索引中" : "未索引"; })()}</span>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="document-detail-view__actions">
                                        {documentEditorMode === "rename" ? (
                                            <>
                                                <BaseButton variant="primary" type="button" onClick={handleSaveDocumentEdit}>
                                                    {updateDocumentMutation.isPending ? "保存中" : "保存重命名"}
                                                </BaseButton>
                                                <BaseButton variant="soft" type="button" onClick={handleCancelDocumentEdit}>
                                                    取消
                                                </BaseButton>
                                            </>
                                        ) : (
                                            <BaseButton variant="primary" type="button" onClick={handleStartDocumentEdit}>
                                                重命名
                                            </BaseButton>
                                        )}
                                        <BaseButton
                                            variant="outline"
                                            type="button"
                                            disabled={deleteDocumentMutation.isPending}
                                            onClick={() => {
                                                if (selectedDocumentUseCount > 0) {
                                                    showErrorDialog({
                                                        title: "资料正在被引用",
                                                        message: `当前资料已被 ${selectedDocumentUseCount} 个问答集引用，暂不允许删除。请先移除引用后再删除。`,
                                                    });
                                                    return;
                                                }
                                                setDeleteDocDialogOpen(true);
                                            }}
                                        >
                                            {deleteDocumentMutation.isPending ? "删除中" : "删除资料"}
                                        </BaseButton>
                                    </div>

                                    <div className="document-detail-view__body">
                                        <MarkdownRenderer content={documentBody} className="document-markdown--doc" />
                                    </div>
                                </div>
                            </>
                        ) : null}

                        {!selectedDocumentQuery.isLoading && !selectedDocumentQuery.data ? (
                            <div className="status-card">
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
                        <p style={{ margin: 0 }}>
                            确定要删除资料「
                            {selectedDocumentQuery.data
                                ? (splitDocumentFileName(selectedDocumentQuery.data.fileName).baseName || selectedDocumentQuery.data.fileName)
                                : ""}
                            」吗？
                        </p>
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
                    emitDasiBubble("资料已移除，相关索引也会同步清理 🗑️");
                    setDeleteDocDialogOpen(false);
                    setActiveDocumentId("");
                    setDocumentNameDraft("");
                    setDocumentEditorMode("view");
                }}
                onCancel={() => setDeleteDocDialogOpen(false)}
            />
        </div>
    );
}
