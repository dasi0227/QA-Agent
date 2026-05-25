import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { FileUp, FolderPlus, Sparkles, X } from "lucide-react";
import { ConfirmDialog } from "@/components/base/confirm-dialog";
import { BaseButton, LinkButton } from "@/components/base/button";
import { GlassCard } from "@/components/base/card";
import { Field, TextArea } from "@/components/base/field";
import { Tag } from "@/components/base/tag";
import {
    useCreateEmptyQuestionSetMutation,
    useDeleteQuestionSetMutation,
    useExportQuestionSetMutation,
    useImportQuestionSetMutation,
    useQuestionSetItemsQuery,
    useQuestionSetQuery,
    useQuestionSetsQuery,
    useUpdateQuestionSetMutation,
    parseModuleTags,
} from "@/lib/api/hooks";
import { cn } from "@/lib/cn";

const TAG_OPTIONS = [
    "JavaSE", "OOP", "JVM", "IO", "JUC", "JCF", "MCP", "SKILL", "AGENT", "Harness",
    "SpringAI", "LangChain4J", "SpringFramework", "SpringMVC", "SpringBoot", "SpringCloud",
    "MyBatis", "MySQL", "PostgreSQL", "Redis", "MQ", "Linux", "Docker", "Maven", "Git",
    "Zookeeper", "Elasticsearch", "K8s", "Grafana", "分布式", "高并发", "微服务", "设计模式",
    "数据结构与算法", "计算机网络", "操作系统", "测试", "运维", "安全",
] as const;

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

export function QASetPage() {
    const params = useParams();
    const navigate = useNavigate();
    const [setTitleDraft, setSetTitleDraft] = useState("");
    const [setDescriptionDraft, setSetDescriptionDraft] = useState("");
    const [deleteSetDialogOpen, setDeleteSetDialogOpen] = useState(false);
    const [editDialogOpen, setEditDialogOpen] = useState(false);
    const [createSetDialogOpen, setCreateSetDialogOpen] = useState(false);
    const [emptySetFormOpen, setEmptySetFormOpen] = useState(false);
    const [emptySetTitleDraft, setEmptySetTitleDraft] = useState("");
    const [emptySetDescriptionDraft, setEmptySetDescriptionDraft] = useState("");
    const [importError, setImportError] = useState("");
    const [selectedTagsDraft, setSelectedTagsDraft] = useState<string[]>([]);
    const importFileInputRef = useRef<HTMLInputElement | null>(null);

    const questionSetsQuery = useQuestionSetsQuery();

    const selectedSetId = params.id ?? questionSetsQuery.data?.[0]?.id ?? "";
    const selectedSetQuery = useQuestionSetQuery(selectedSetId);
    const selectedSetItemsQuery = useQuestionSetItemsQuery(selectedSetId);
    const deleteQuestionSetMutation = useDeleteQuestionSetMutation();
    const updateQuestionSetMutation = useUpdateQuestionSetMutation();
    const importQuestionSetMutation = useImportQuestionSetMutation();
    const exportQuestionSetMutation = useExportQuestionSetMutation();
    const createEmptyQuestionSetMutation = useCreateEmptyQuestionSetMutation();

    const hasQuestionSets = (questionSetsQuery.data?.length ?? 0) > 0;
    const setErrorMessage = questionSetsQuery.error instanceof Error ? questionSetsQuery.error.message : "";

    useEffect(() => {
        if (!selectedSetQuery.data) {
            setSetTitleDraft("");
            setSetDescriptionDraft("");
            return;
        }
        setSetTitleDraft(selectedSetQuery.data.title);
        setSetDescriptionDraft(selectedSetQuery.data.description || "");
        setSelectedTagsDraft(parseModuleTags(selectedSetQuery.data.moduleTagsJson));
    }, [selectedSetQuery.data]);

    const selectedSetDescription = selectedSetQuery.data?.description?.trim() || "";
    const selectedSetDescriptionText = selectedSetDescription || "当前暂无描述";
    const selectedSetPracticeTotal = selectedSetQuery.data
        ? selectedSetQuery.data.questionCount * selectedSetQuery.data.practiceCount
        : 0;
    const selectedSetUpdatedAt = selectedSetQuery.data?.updatedAt || selectedSetQuery.data?.createdAt || "";
    const itemList = selectedSetItemsQuery.data ?? [];
    const selectedTags = parseModuleTags(selectedSetQuery.data?.moduleTagsJson);
    const availableTags = useMemo(
        () => TAG_OPTIONS.filter((tag) => !selectedTagsDraft.includes(tag)),
        [selectedTagsDraft],
    );
    const firstItemId = itemList[0]?.id ?? "";

    const openEditDialog = () => {
        if (!selectedSetQuery.data) return;
        setSetTitleDraft(selectedSetQuery.data.title);
        setSetDescriptionDraft(selectedSetQuery.data.description || "");
        setSelectedTagsDraft(parseModuleTags(selectedSetQuery.data.moduleTagsJson));
        setEditDialogOpen(true);
    };

    const closeEditDialog = () => {
        if (selectedSetQuery.data) {
            setSetTitleDraft(selectedSetQuery.data.title);
            setSetDescriptionDraft(selectedSetQuery.data.description || "");
            setSelectedTagsDraft(parseModuleTags(selectedSetQuery.data.moduleTagsJson));
        }
        setEditDialogOpen(false);
    };

    const saveEditDialog = async () => {
        if (!selectedSetQuery.data || !setTitleDraft.trim()) return;
        await updateQuestionSetMutation.mutateAsync({
            questionSetId: selectedSetQuery.data.id,
            title: setTitleDraft.trim(),
            description: setDescriptionDraft.trim(),
            moduleTagsJson: JSON.stringify(selectedTagsDraft),
        });
        setEditDialogOpen(false);
    };

    const closeCreateSetDialog = () => {
        if (importQuestionSetMutation.isPending || createEmptyQuestionSetMutation.isPending) return;
        setCreateSetDialogOpen(false);
        setEmptySetFormOpen(false);
        setEmptySetTitleDraft("");
        setEmptySetDescriptionDraft("");
        setImportError("");
    };

    const createEmptySet = async () => {
        const title = emptySetTitleDraft.trim();
        if (!title) {
            setImportError("请输入题集标题。");
            return;
        }
        setImportError("");
        try {
            const createdSet = await createEmptyQuestionSetMutation.mutateAsync({
                title,
                description: emptySetDescriptionDraft.trim(),
            });
            setCreateSetDialogOpen(false);
            setEmptySetFormOpen(false);
            setEmptySetTitleDraft("");
            setEmptySetDescriptionDraft("");
            navigate(`/repository/qa-set/${createdSet.id}`, { replace: true });
        } catch (error) {
            setImportError(error instanceof Error ? error.message : "空题集创建失败，请稍后重试。");
        }
    };

    const handleImportFile = async (file?: File) => {
        if (!file) return;
        setImportError("");
        if (!file.name.toLowerCase().endsWith(".dasi")) {
            setImportError("请选择 .dasi 问答集文件。");
            return;
        }
        try {
            const importedSet = await importQuestionSetMutation.mutateAsync({ file });
            setCreateSetDialogOpen(false);
            navigate(`/repository/qa-set/${importedSet.id}`, { replace: true });
        } catch (error) {
            setImportError(error instanceof Error ? error.message : "导入失败，请检查文件格式。");
        } finally {
            if (importFileInputRef.current) {
                importFileInputRef.current.value = "";
            }
        }
    };

    const exportSelectedSet = async () => {
        if (!selectedSetQuery.data) return;
        const exportedFile = await exportQuestionSetMutation.mutateAsync(selectedSetQuery.data.id);
        const url = window.URL.createObjectURL(exportedFile.blob);
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = exportedFile.fileName;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        window.URL.revokeObjectURL(url);
    };

    return (
        <div className="page-frame">
            <div className="layout-two-col repository-layout">
                <aside className="sidebar">
                    <div className="repository-mode-switch" style={{ marginBottom: 18 }}>
                        <button className="choice-btn choice-btn--active" type="button">问答集</button>
                        <button className="choice-btn" type="button" onClick={() => navigate("/repository/document")}>资料库</button>
                        <button className="choice-btn" type="button" disabled title="题目详情页自动切换">题目表</button>
                    </div>
                    <div className="tree">
                        <div className="sidebar__upload-area sidebar__action-area">
                            <BaseButton
                                variant="soft"
                                type="button"
                                className="sidebar__upload-btn"
                                onClick={() => {
                                    setImportError("");
                                    setCreateSetDialogOpen(true);
                                }}
                            >
                                新增题集
                            </BaseButton>
                        </div>
                        <div className="subtree tree">
                            {questionSetsQuery.isLoading ? (
                                <div className="tree-qaSetEntry">加载中...</div>
                            ) : null}
                            {questionSetsQuery.isError ? (
                                <div className="tree-qaSetEntry" style={{ color: "var(--ink)" }}>
                                    {setErrorMessage || "问答集加载失败"}
                                </div>
                            ) : null}
                            {questionSetsQuery.data?.map((qaSetEntry) => {
                                const isActive = qaSetEntry.id === selectedSetId;
                                return (
                                    <Link
                                        key={qaSetEntry.id}
                                        to={`/repository/qa-set/${qaSetEntry.id}`}
                                        className={cn("tree-qaSetEntry", "tree-qaSetEntry--entry", isActive && "tree-qaSetEntry--active")}
                                    >
                                        <span className="tree-item__label">{qaSetEntry.title}</span>
                                    </Link>
                                );
                            })}
                            {!questionSetsQuery.isLoading && !questionSetsQuery.isError && !hasQuestionSets ? (
                                <div className="tree-qaSetEntry">暂无问答集</div>
                            ) : null}
                        </div>
                    </div>
                </aside>

                <GlassCard className="panel repository-main-panel" style={{ padding: 24, overflow: "hidden" }}>
                    <div className="fade-in" style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
                        {selectedSetQuery.isLoading ? (
                            <div className="status-card">
                                <strong>正在加载问答集</strong>
                                <div className="qa-text">从真实接口读取当前问答集详情和题目列表。</div>
                            </div>
                        ) : null}

                        {selectedSetQuery.isError ? (
                            <div className="status-card">
                                <strong>问答集加载失败</strong>
                                <div className="qa-text">
                                    {selectedSetQuery.error instanceof Error ? selectedSetQuery.error.message : "请稍后重试"}
                                </div>
                                <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                                    <BaseButton variant="soft" type="button" onClick={() => selectedSetQuery.refetch()}>
                                        重试
                                    </BaseButton>
                                    <LinkButton to="/create" variant="ghost">
                                        去创建
                                    </LinkButton>
                                </div>
                            </div>
                        ) : null}
                        {!selectedSetQuery.isLoading && !selectedSetQuery.isError && !selectedSetQuery.data ? (
                            <div className="status-card">
                                <strong>暂无问答集可预览</strong>
                            </div>
                        ) : null}

                        {selectedSetQuery.data ? (
                            <>
                                <div className="repository-header">
                                    <div style={{ flex: 1, minWidth: 0 }}>
                                        <h1 className="hero-title" style={{ fontSize: 34, margin: 0 }}>
                                            {selectedSetQuery.data.title}
                                        </h1>
                                        <p className="page-copy" style={{ margin: "12px 0 0" }}>
                                            {selectedSetDescriptionText}
                                        </p>
                                        <div className="repository-header__tags" style={{ marginTop: 12 }}>
                                            {selectedTags.map((tag) => (
                                                <Tag key={tag}>{tag}</Tag>
                                            ))}
                                        </div>
                                        <div className="document-detail-view__meta" style={{ marginTop: 10, marginBottom: 0 }}>
                                            <span>
                                                添加于 {formatCompactDateTime(selectedSetQuery.data.createdAt || selectedSetUpdatedAt)}
                                            </span>
                                            <span>更新于 {formatCompactDateTime(selectedSetUpdatedAt)}</span>
                                            <span>使用资料 {selectedSetQuery.data.documentCount} 篇</span>
                                        </div>
                                    </div>
                                    <section className="repository-overview-card">
                                        <div className="repository-overview-card__grid">
                                            <div className="repository-overview-card__metric">
                                                <span>题目数量</span>
                                                <strong>{selectedSetQuery.data.questionCount}</strong>
                                            </div>
                                            <div className="repository-overview-card__metric">
                                                <span>刷题总数</span>
                                                <strong>{selectedSetPracticeTotal}</strong>
                                            </div>
                                            <div className="repository-overview-card__metric">
                                                <span>平均正确率</span>
                                                <strong>{selectedSetQuery.data.averageScore}%</strong>
                                            </div>
                                            <div className="repository-overview-card__metric">
                                                <span>练习轮数</span>
                                                <strong>{selectedSetQuery.data.practiceCount}</strong>
                                            </div>
                                        </div>
                                    </section>
                                </div>

                                <div className="repository-header__actions" style={{ marginTop: 24, marginBottom: 28 }}>
                                    <LinkButton to={`/quiz?questionSetId=${selectedSetQuery.data.id}`} variant="primary">
                                        开始练习
                                    </LinkButton>
                                    <LinkButton to={`/repository/qa-set/${selectedSetQuery.data.id}/history`} variant="soft">
                                        练习历史
                                    </LinkButton>
                                    <LinkButton
                                        to={firstItemId
                                            ? `/repository/question?qaSetId=${selectedSetQuery.data.id}&itemId=${firstItemId}`
                                            : `/repository/question?qaSetId=${selectedSetQuery.data.id}`}
                                        variant="soft"
                                        className="repository-detail-btn"
                                    >
                                        题目详情
                                    </LinkButton>
                                    <BaseButton variant="soft" type="button" onClick={openEditDialog}>
                                        编辑信息
                                    </BaseButton>
                                    <BaseButton
                                        variant="soft"
                                        type="button"
                                        disabled={deleteQuestionSetMutation.isPending}
                                        onClick={() => setDeleteSetDialogOpen(true)}
                                    >
                                        {deleteQuestionSetMutation.isPending ? "删除中" : "删除问答集"}
                                    </BaseButton>
                                    <BaseButton
                                        variant="soft"
                                        type="button"
                                        disabled={exportQuestionSetMutation.isPending}
                                        onClick={exportSelectedSet}
                                    >
                                        {exportQuestionSetMutation.isPending ? "导出中" : "导出问答集"}
                                    </BaseButton>
                                </div>
                                <div className="repository-workspace">
                                    <section className="repository-items-panel">
                                        <div className="repository-panel__header">
                                            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                                                <h3 style={{ margin: 0, fontSize: 18 }}>题目目录</h3>
                                            </div>
                                        </div>
                                        {selectedSetItemsQuery.isLoading ? <div className="qa-text">正在加载题目列表...</div> : null}
                                        {selectedSetItemsQuery.isError ? (
                                            <div className="qa-text">
                                                {selectedSetItemsQuery.error instanceof Error
                                                    ? selectedSetItemsQuery.error.message
                                                    : "题目列表加载失败"}
                                            </div>
                                        ) : null}
                                        {!selectedSetItemsQuery.isLoading && (selectedSetItemsQuery.data?.length ?? 0) === 0 ? (
                                            <div className="qa-text">当前问答集还没有题目。</div>
                                        ) : null}
                                        {selectedSetItemsQuery.data?.length ? (
                                            <div className="repository-items-scroll">
                                                <div className="repository-qaSetEntry-list">
                                                    {selectedSetItemsQuery.data.map((qaSetEntry) => (
                                                        <Link
                                                            key={qaSetEntry.id}
                                                            to={`/repository/question?qaSetId=${selectedSetQuery.data.id}&itemId=${qaSetEntry.id}`}
                                                            className={cn("repository-qaSetEntry-card")}
                                                        >
                                                            <strong>{qaSetEntry.question}</strong>
                                                            <div className="repository-qaSetEntry-card__meta">
                                                                {qaSetEntry.difficulty ? (
    <Tag className={`qaSetEntry__difficulty-tag qaSetEntry__difficulty--${qaSetEntry.difficulty.toLowerCase()}`}>
        {qaSetEntry.difficulty}
    </Tag>
) : (
    <small>未标注难度</small>
)}
                                                                {qaSetEntry.moduleTag?.trim() ? (
                                                                    <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
                                                                        {qaSetEntry.moduleTag.split(",").map((tag) => tag.trim()).filter(Boolean).map((tag) => (
                                                                            <Tag key={tag}>{tag}</Tag>
                                                                        ))}
                                                                    </div>
                                                                ) : null}
                                                            </div>
                                                        </Link>
                                                    ))}
                                                </div>
                                            </div>
                                        ) : null}
                                    </section>
                                </div>


                            </>
                        ) : null}
                    </div>
                </GlassCard>
            </div>

            <ConfirmDialog
                open={deleteSetDialogOpen}
                title="⚠️ 删除问答集"
                variant="danger"
                message={
                    <>
                        <p style={{ margin: 0 }}>确定要删除问答集「{selectedSetQuery.data?.title}」吗？</p>
                        <p style={{ margin: "10px 0 0", color: "#8f4c39", fontSize: 13, fontWeight: 600 }}>
                            会同步删除所有题目和做题记录，请考虑后谨慎删除。
                        </p>
                    </>
                }
                confirmLabel="删除"
                loading={deleteQuestionSetMutation.isPending}
                onConfirm={async () => {
                    if (!selectedSetQuery.data) return;
                    await deleteQuestionSetMutation.mutateAsync(selectedSetQuery.data.id);
                    setDeleteSetDialogOpen(false);
                    navigate("/repository/qa-set", { replace: true });
                }}
                onCancel={() => setDeleteSetDialogOpen(false)}
            />

            {createSetDialogOpen ? (
                <div className="doc-select-dialog" role="presentation" onClick={closeCreateSetDialog}>
                    <div className="qa-set-create-dialog" role="dialog" aria-modal="true" aria-label="新增题集" onClick={(event) => event.stopPropagation()}>
                        <div className="doc-select-dialog__header">
                            <h3 className="doc-select-dialog__title">新增题集</h3>
                            <button type="button" className="doc-select-dialog__close" onClick={closeCreateSetDialog}>
                                <X size={16} />
                            </button>
                        </div>

                        <div className="qa-set-create-dialog__body">
                            <button
                                type="button"
                                className="qa-set-create-option"
                                disabled={importQuestionSetMutation.isPending}
                                onClick={() => importFileInputRef.current?.click()}
                            >
                                <span className="qa-set-create-option__icon"><FileUp size={20} /></span>
                                <span className="qa-set-create-option__copy">
                                    <strong>{importQuestionSetMutation.isPending ? "正在导入" : "从文件导入"}</strong>
                                    <small>选择 .dasi 文件，恢复一份本地题集。</small>
                                </span>
                            </button>

                            <button
                                type="button"
                                className="qa-set-create-option"
                                disabled={createEmptyQuestionSetMutation.isPending}
                                onClick={() => setEmptySetFormOpen(true)}
                            >
                                <span className="qa-set-create-option__icon"><FolderPlus size={20} /></span>
                                <span className="qa-set-create-option__copy">
                                    <strong>创建空题集</strong>
                                    <small>只创建题集框架，之后手动新增题目。</small>
                                </span>
                            </button>

                            <button
                                type="button"
                                className="qa-set-create-option"
                                onClick={() => navigate("/create")}
                            >
                                <span className="qa-set-create-option__icon"><Sparkles size={20} /></span>
                                <span className="qa-set-create-option__copy">
                                    <strong>利用资料创建</strong>
                                    <small>选择资料，让系统生成新的问答集。</small>
                                </span>
                            </button>
                        </div>

                        {emptySetFormOpen ? (
                            <div className="qa-set-empty-form">
                                <Field label="题集标题">
                                    <input
                                        className="input"
                                        value={emptySetTitleDraft}
                                        onChange={(event) => setEmptySetTitleDraft(event.target.value)}
                                        placeholder="例如：操作系统面试题"
                                        maxLength={50}
                                    />
                                </Field>
                                <Field label="描述">
                                    <TextArea
                                        className="qa-set-empty-form__textarea"
                                        value={emptySetDescriptionDraft}
                                        onChange={(event) => setEmptySetDescriptionDraft(event.target.value)}
                                        placeholder="可选，说明这套题的用途或范围"
                                        maxLength={300}
                                        rows={3}
                                    />
                                </Field>
                                <div className="qa-set-empty-form__actions">
                                    <BaseButton
                                        variant="primary"
                                        type="button"
                                        disabled={createEmptyQuestionSetMutation.isPending || !emptySetTitleDraft.trim()}
                                        onClick={createEmptySet}
                                    >
                                        {createEmptyQuestionSetMutation.isPending ? "创建中" : "创建空题集"}
                                    </BaseButton>
                                    <BaseButton
                                        variant="ghost"
                                        type="button"
                                        onClick={() => {
                                            setEmptySetFormOpen(false);
                                            setImportError("");
                                        }}
                                    >
                                        收起
                                    </BaseButton>
                                </div>
                            </div>
                        ) : null}

                        <input
                            ref={importFileInputRef}
                            type="file"
                            accept=".dasi"
                            style={{ display: "none" }}
                            onChange={(event) => handleImportFile(event.target.files?.[0])}
                        />

                        {importError ? (
                            <div className="qa-set-create-dialog__error">{importError}</div>
                        ) : null}
                    </div>
                </div>
            ) : null}

            {editDialogOpen ? (
                <div className="doc-select-dialog" role="presentation" onClick={closeEditDialog}>
                    <div className="question-edit-dialog" role="dialog" aria-modal="true" aria-label="编辑问答集信息" onClick={(event) => event.stopPropagation()}>
                        <div className="doc-select-dialog__header">
                            <h3 className="doc-select-dialog__title">编辑信息</h3>
                            <button type="button" className="doc-select-dialog__close" onClick={closeEditDialog}>
                                <X size={16} />
                            </button>
                        </div>

                        <div className="question-edit-dialog__body">
                            <Field label="题目">
                                <input
                                    className="input"
                                    value={setTitleDraft}
                                    onChange={(event) => {
                                        if (event.target.value.length <= 50) setSetTitleDraft(event.target.value);
                                    }}
                                    placeholder="输入问答集标题"
                                    maxLength={50}
                                />
                            </Field>

                            <Field label="描述">
                                <TextArea
                                    className="question-edit-dialog__textarea"
                                    value={setDescriptionDraft}
                                    onChange={(event) => {
                                        if (event.target.value.length <= 300) setSetDescriptionDraft(event.target.value);
                                    }}
                                    placeholder="输入问答集描述"
                                    maxLength={300}
                                    rows={5}
                                />
                            </Field>

                            <section className="tag-dialog__section">
                                <div className="tag-dialog__section-head">
                                    <strong>模块标签</strong>
                                    <span>{selectedTagsDraft.length} 个</span>
                                </div>
                                <div className="tag-dialog__selected-list">
                                    {selectedTagsDraft.length ? selectedTagsDraft.map((tag) => (
                                        <div key={tag} className="tag-dialog__selected-qaSetEntry">
                                            <span>{tag}</span>
                                            <button
                                                type="button"
                                                className="tag-dialog__selected-remove"
                                                aria-label={`移除 ${tag}`}
                                                onClick={() => setSelectedTagsDraft((current) => current.filter((qaSetEntry) => qaSetEntry !== tag))}
                                            >
                                                <X size={12} />
                                            </button>
                                        </div>
                                    )) : (
                                        <div className="tag-dialog__empty">还没有选择标签</div>
                                    )}
                                </div>
                                <div className="tag-dialog__pool">
                                    {availableTags.map((tag) => (
                                        <button
                                            key={tag}
                                            type="button"
                                            className="tag-dialog__pool-qaSetEntry"
                                            onClick={() => setSelectedTagsDraft((current) => [...current, tag])}
                                        >
                                            {tag}
                                        </button>
                                    ))}
                                </div>
                            </section>
                        </div>
                        <div className="modal-card__footer">
                            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                                <BaseButton
                                    variant="primary"
                                    type="button"
                                    disabled={updateQuestionSetMutation.isPending || !setTitleDraft.trim()}
                                    onClick={saveEditDialog}
                                >
                                    {updateQuestionSetMutation.isPending ? "保存中" : "保存修改"}
                                </BaseButton>
                                <BaseButton
                                    variant="ghost"
                                    type="button"
                                    onClick={closeEditDialog}
                                >
                                    取消
                                </BaseButton>
                            </div>
                        </div>
                    </div>
                </div>
            ) : null}
        </div>
    );
}
