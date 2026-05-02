import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { ArrowLeft, Plus } from "lucide-react";
import { BaseButton, LinkButton } from "@/components/base/button";
import { GlassCard } from "@/components/base/card";
import { Field, TextArea, TextInput } from "@/components/base/field";
import { Tag } from "@/components/base/tag";
import {
    useCreateQuestionItemMutation,
    useDeleteDocumentMutation,
    useDeleteQuestionItemMutation,
    useDeleteQuestionSetMutation,
    useDocumentQuery,
    useDocumentsQuery,
    useUpdateQuestionItemMutation,
    useUpdateQuestionSetMutation,
    useQuestionSetItemsQuery,
    useQuestionSetQuery,
    useQuestionSetsQuery,
    useUpdateDocumentMutation,
    parseModuleTags,
} from "@/lib/api/hooks";
import { cn } from "@/lib/cn";
import { MarkdownRenderer } from "@/lib/markdown";

const emptyItemDraft = {
    question: "",
    knowledgeNote: "",
    answer: "",
    moduleTag: "",
    difficulty: "",
    conflictTip: "",
    sourceChunkIdsJson: "",
};

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

export function RepositoryPage() {
    const params = useParams();
    const navigate = useNavigate();
    const [activeMode, setActiveMode] = useState<"qa" | "table" | "library">("qa");
    const [questionTableUnlocked, setQuestionTableUnlocked] = useState(false);
    const [activeDocumentId, setActiveDocumentId] = useState("");
    const [documentEditorMode, setDocumentEditorMode] = useState<"view" | "edit">("view");
    const [documentDraft, setDocumentDraft] = useState("");
    const [activeItemId, setActiveItemId] = useState("");
    const [editingSetMeta, setEditingSetMeta] = useState(false);
    const [setTitleDraft, setSetTitleDraft] = useState("");
    const [setDescriptionDraft, setSetDescriptionDraft] = useState("");
    const [itemDraft, setItemDraft] = useState(emptyItemDraft);
    const [itemEditorMode, setItemEditorMode] = useState<"create" | "edit" | null>(null);

    const questionSetsQuery = useQuestionSetsQuery();
    const documentsQuery = useDocumentsQuery();

    const selectedSetId = params.id ?? questionSetsQuery.data?.[0]?.id ?? "";
    const selectedSetQuery = useQuestionSetQuery(selectedSetId);
    const selectedSetItemsQuery = useQuestionSetItemsQuery(selectedSetId);
    const deleteQuestionSetMutation = useDeleteQuestionSetMutation();
    const updateQuestionSetMutation = useUpdateQuestionSetMutation();
    const updateDocumentMutation = useUpdateDocumentMutation();
    const createQuestionItemMutation = useCreateQuestionItemMutation();
    const updateQuestionItemMutation = useUpdateQuestionItemMutation();
    const deleteQuestionItemMutation = useDeleteQuestionItemMutation();

    const activeDocumentIdValue = activeDocumentId || documentsQuery.data?.[0]?.id || "";
    const selectedDocumentQuery = useDocumentQuery(activeDocumentIdValue);
    const deleteDocumentMutation = useDeleteDocumentMutation();
    const selectedDocumentId = selectedDocumentQuery.data?.id ?? "";

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
        setDocumentDraft(selectedDocumentQuery.data.rawContent || selectedDocumentQuery.data.normalizedContent || "");
        setDocumentEditorMode("view");
    }, [selectedDocumentId]);

    useEffect(() => {
        if (!selectedSetQuery.data) {
            setSetTitleDraft("");
            setSetDescriptionDraft("");
            return;
        }
        setSetTitleDraft(selectedSetQuery.data.title);
        setSetDescriptionDraft(selectedSetQuery.data.description);
    }, [selectedSetQuery.data]);

    useEffect(() => {
        if (itemEditorMode === "create") {
            return;
        }
        const items = selectedSetItemsQuery.data ?? [];
        if (items.length === 0) {
            setActiveItemId("");
            setItemDraft(emptyItemDraft);
            return;
        }
        const nextActiveId = items.some((item) => item.id === activeItemId) ? activeItemId : items[0].id;
        setActiveItemId(nextActiveId);
        const activeItem = items.find((item) => item.id === nextActiveId);
        if (activeItem) {
            setItemDraft({
                question: activeItem.question,
                knowledgeNote: activeItem.knowledgeNote,
                answer: activeItem.answer,
                moduleTag: activeItem.moduleTag,
                difficulty: activeItem.difficulty || "",
                conflictTip: activeItem.conflictTip || "",
                sourceChunkIdsJson: activeItem.sourceChunkIdsJson || "",
            });
        }
    }, [activeItemId, itemEditorMode, selectedSetItemsQuery.data]);

    const hasQuestionSets = (questionSetsQuery.data?.length ?? 0) > 0;
    const hasDocuments = (documentsQuery.data?.length ?? 0) > 0;
    const setErrorMessage = questionSetsQuery.error instanceof Error ? questionSetsQuery.error.message : "";
    const documentErrorMessage = documentsQuery.error instanceof Error ? documentsQuery.error.message : "";
    const activeItem = (selectedSetItemsQuery.data ?? []).find((item) => item.id === activeItemId) ?? null;
    const showItemEditor = itemEditorMode !== null;
    const itemList = selectedSetItemsQuery.data ?? [];
    const showQuestionTableView = activeMode === "table";
    const showItemDetailView = showItemEditor;
    const sidebarTitle = showQuestionTableView ? "题目表" : activeMode === "qa" ? "问答集" : "资料文件";
    const selectedDocumentUpdatedAt = selectedDocumentQuery.data?.updatedAt || selectedDocumentQuery.data?.createdAt || "";
    const selectedDocumentUseCount = selectedDocumentQuery.data?.referenceCount ?? 0;
    const documentBody = documentEditorMode === "edit"
        ? documentDraft
        : (selectedDocumentQuery.data?.rawContent || selectedDocumentQuery.data?.normalizedContent || "");
    const handleStartDocumentEdit = () => {
        if (!selectedDocumentQuery.data) {
            return;
        }
        setDocumentDraft(selectedDocumentQuery.data.rawContent || selectedDocumentQuery.data.normalizedContent || "");
        setDocumentEditorMode("edit");
    };
    const handleCancelDocumentEdit = () => {
        if (!selectedDocumentQuery.data) {
            return;
        }
        setDocumentDraft(selectedDocumentQuery.data.rawContent || selectedDocumentQuery.data.normalizedContent || "");
        setDocumentEditorMode("view");
    };
    const handleSaveDocumentEdit = async () => {
        if (!selectedDocumentQuery.data) {
            return;
        }
        await updateDocumentMutation.mutateAsync({
            ...selectedDocumentQuery.data,
            rawContent: documentDraft,
            normalizedContent: documentDraft,
        });
        setDocumentEditorMode("view");
    };
    const openQuestionTable = () => {
        if (!questionTableUnlocked) {
            return;
        }
        setActiveMode("table");
        const nextItem = itemList.find((item) => item.id === activeItemId) ?? itemList[0];
        if (nextItem) {
            openEditItemEditor(nextItem);
        } else {
            setActiveItemId("");
            setItemEditorMode(null);
        }
    };
    const openCreateItemEditor = () => {
        setQuestionTableUnlocked(true);
        setActiveMode("table");
        setActiveItemId("");
        setItemDraft({
            ...emptyItemDraft,
            moduleTag: parseModuleTags(selectedSetQuery.data?.moduleTagsJson)[0] || "",
        });
        setItemEditorMode("create");
    };
    const openEditItemEditor = (item: NonNullable<typeof activeItem>) => {
        setQuestionTableUnlocked(true);
        setActiveMode("table");
        setActiveItemId(item.id);
        setItemDraft({
            question: item.question,
            knowledgeNote: item.knowledgeNote,
            answer: item.answer,
            moduleTag: item.moduleTag,
            difficulty: item.difficulty || "",
            conflictTip: item.conflictTip || "",
            sourceChunkIdsJson: item.sourceChunkIdsJson || "",
        });
        setItemEditorMode("edit");
    };
    const closeItemEditor = () => {
        setItemEditorMode(null);
        setItemDraft(emptyItemDraft);
        setQuestionTableUnlocked(false);
        setActiveMode("qa");
    };
    const saveItemEditor = async () => {
        if (!selectedSetQuery.data) {
            return;
        }

        if (itemEditorMode === "create") {
            const created = await createQuestionItemMutation.mutateAsync({
                qaSetId: selectedSetQuery.data.id,
                ...itemDraft,
            });
            setActiveItemId(created.id);
            closeItemEditor();
            return;
        }

        if (!activeItem) {
            return;
        }

        await updateQuestionItemMutation.mutateAsync({
            qaSetId: selectedSetQuery.data.id,
            questionItemId: activeItem.id,
            ...itemDraft,
        });
        closeItemEditor();
    };
    const deleteActiveItem = async () => {
        if (!selectedSetQuery.data || !activeItem) {
            return;
        }
        if (window.confirm(`确认删除题目“${activeItem.question}”吗？`)) {
            await deleteQuestionItemMutation.mutateAsync({
                qaSetId: selectedSetQuery.data.id,
                questionItemId: activeItem.id,
            });
            setActiveItemId("");
            closeItemEditor();
        }
    };
    const selectedSetDescription = selectedSetQuery.data?.description?.trim();
    const selectedSetPracticeTotal = selectedSetQuery.data
        ? selectedSetQuery.data.questionCount * selectedSetQuery.data.practiceCount
        : 0;

    return (
        <div className="page-frame">
            <div className="layout-two-col repository-layout">
            <aside className="sidebar">
                <div className="repository-mode-switch" style={{ marginBottom: 18 }}>
                    <button
                        className={activeMode === "qa" ? "choice-btn choice-btn--active" : "choice-btn"}
                        onClick={() => setActiveMode("qa")}
                        type="button"
                    >
                        问答集
                    </button>
                    <button
                        className={activeMode === "library" ? "choice-btn choice-btn--active" : "choice-btn"}
                        onClick={() => setActiveMode("library")}
                        type="button"
                    >
                        资料库
                    </button>
                    <button
                        className={activeMode === "table" ? "choice-btn choice-btn--active" : "choice-btn"}
                        onClick={openQuestionTable}
                        type="button"
                        disabled={!questionTableUnlocked}
                        aria-disabled={!questionTableUnlocked}
                        title={!questionTableUnlocked ? "请先在问答集中选择题集" : undefined}
                    >
                        题目表
                    </button>
                </div>

                {activeMode === "qa" ? (
                    <div className="tree">
                        <div className="tree-item">
                            <span className="tree-item__label">{sidebarTitle}</span>
                            <span className="tree-item__meta">{questionSetsQuery.data?.length ?? 0}</span>
                        </div>
                        <div className="subtree tree">
                            {questionSetsQuery.isLoading ? (
                                <div className="tree-item">加载中...</div>
                            ) : null}
                            {questionSetsQuery.isError ? (
                                <div className="tree-item" style={{ color: "var(--ink)" }}>
                                    {setErrorMessage || "问答集加载失败"}
                                </div>
                            ) : null}
                            {questionSetsQuery.data?.map((item) => {
                                const isActive = item.id === selectedSetId;
                                return (
                                    <Link
                                        key={item.id}
                                        to={`/repository/${item.id}`}
                                        className={cn("tree-item", "tree-item--entry", isActive && "tree-item--active")}
                                    >
                                        <span className="tree-item__label">{item.title}</span>
                                    </Link>
                                );
                            })}
                            {!questionSetsQuery.isLoading && !questionSetsQuery.isError && !hasQuestionSets ? (
                                <div className="tree-item">暂无问答集</div>
                            ) : null}
                        </div>
                    </div>
                ) : activeMode === "table" ? (
                    <div className="tree">
                        <div className="tree-item">
                            <span className="tree-item__label">{sidebarTitle}</span>
                            <span className="tree-item__meta">{itemList.length}</span>
                        </div>
                        <div className="subtree tree">
                            {selectedSetItemsQuery.isLoading ? (
                                <div className="tree-item">加载中...</div>
                            ) : null}
                            {selectedSetItemsQuery.isError ? (
                                <div className="tree-item" style={{ color: "var(--ink)" }}>
                                    {selectedSetItemsQuery.error instanceof Error
                                        ? selectedSetItemsQuery.error.message
                                        : "题目列表加载失败"}
                                </div>
                            ) : null}
                            {itemList.map((item) => {
                                const isActive = item.id === activeItemId;
                                return (
                                    <button
                                        key={item.id}
                                        type="button"
                                        className={cn("tree-item", "tree-item--entry", isActive && "tree-item--active")}
                                        onClick={() => openEditItemEditor(item)}
                                    >
                                        <span className="tree-item__label">{item.question}</span>
                                    </button>
                                );
                            })}
                            {!selectedSetItemsQuery.isLoading && !selectedSetItemsQuery.isError && !itemList.length ? (
                                <div className="tree-item">暂无题目</div>
                            ) : null}
                        </div>
                    </div>
                ) : (
                    <div className="tree">
                        <div className="tree-item">
                            <span className="tree-item__label">{sidebarTitle}</span>
                            <span className="tree-item__meta">{documentsQuery.data?.length ?? 0}</span>
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
                )}
            </aside>

            <GlassCard className="panel repository-main-panel" style={{ padding: 24 }}>
                {activeMode !== "library" ? (
                    <div className="fade-in">
                        {selectedSetQuery.isLoading ? (
                            <div className="qa-feedback">
                                <strong>正在加载问答集</strong>
                                <div className="qa-text">从真实接口读取当前问答集详情和题目列表。</div>
                            </div>
                        ) : null}

                        {selectedSetQuery.isError ? (
                            <div className="qa-feedback">
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
                        {deleteQuestionSetMutation.isError ? (
                            <div className="qa-feedback">
                                <strong>删除失败</strong>
                                <div className="qa-text">
                                    {deleteQuestionSetMutation.error instanceof Error
                                        ? deleteQuestionSetMutation.error.message
                                        : "请稍后重试"}
                                </div>
                            </div>
                        ) : null}

                        {selectedSetQuery.data ? (
                            showItemDetailView ? (
                                <div className="repository-detail-view fade-in">
                                    <div className="repository-detail-view__header document-detail-view__header">
                                        <BaseButton
                                            variant="ghost"
                                            type="button"
                                            leadingIcon={<ArrowLeft size={14} />}
                                            onClick={closeItemEditor}
                                        >
                                            返回目录
                                        </BaseButton>
                                        <div className="repository-detail-view__meta">
                                            <span className="eyebrow" style={{ marginBottom: 0 }}>
                                                {selectedSetQuery.data.title}
                                            </span>
                                        </div>
                                    </div>

                                    <div className="repository-detail-view__title">
                                        <h1 className="hero-title" style={{ fontSize: 34, margin: 0 }}>
                                            {itemEditorMode === "create" ? "新增题目" : activeItem?.question || "题目详情"}
                                        </h1>
                                        {itemEditorMode === "edit" && activeItem ? (
                                            <div className="qa-feedback">
                                                <div className="sidebar__split">
                                                    <strong>题目结构</strong>
                                                    <span>{activeItem.difficulty || "未标注难度"}</span>
                                                </div>
                                                {activeItem.sourceChunkIdsJson ? (
                                                    <div className="qa-text" style={{ marginTop: 12 }}>
                                                        已绑定证据片段：{activeItem.sourceChunkIdsJson}
                                                    </div>
                                                ) : null}
                                            </div>
                                        ) : null}
                                    </div>

                                    <div className="repository-editor-inline__body">
                                        <Field label="问题">
                                            <TextArea
                                                value={itemDraft.question}
                                                onChange={(event) => setItemDraft((current) => ({ ...current, question: event.target.value }))}
                                                rows={3}
                                            />
                                        </Field>
                                        <div className="result-grid" style={{ gridTemplateColumns: "repeat(2, minmax(0, 1fr))" }}>
                                            <Field label="模块">
                                                <TextInput
                                                    value={itemDraft.moduleTag}
                                                    onChange={(event) => setItemDraft((current) => ({ ...current, moduleTag: event.target.value }))}
                                                />
                                            </Field>
                                            <Field label="难度" hint="EASY / MEDIUM / HARD">
                                                <TextInput
                                                    value={itemDraft.difficulty}
                                                    onChange={(event) => setItemDraft((current) => ({ ...current, difficulty: event.target.value }))}
                                                />
                                            </Field>
                                        </div>
                                        <Field label="知识笔记">
                                            <TextArea
                                                value={itemDraft.knowledgeNote}
                                                onChange={(event) => setItemDraft((current) => ({ ...current, knowledgeNote: event.target.value }))}
                                                rows={5}
                                            />
                                        </Field>
                                        <Field label="标准回答">
                                            <TextArea
                                                value={itemDraft.answer}
                                                onChange={(event) => setItemDraft((current) => ({ ...current, answer: event.target.value }))}
                                                rows={6}
                                            />
                                        </Field>
                                        <Field label="冲突提示" hint="可选">
                                            <TextArea
                                                value={itemDraft.conflictTip}
                                                onChange={(event) => setItemDraft((current) => ({ ...current, conflictTip: event.target.value }))}
                                                rows={3}
                                            />
                                        </Field>
                                        <Field label="证据片段 ID" hint="JSON 数组或逗号分隔，选填">
                                            <TextArea
                                                value={itemDraft.sourceChunkIdsJson}
                                                onChange={(event) => setItemDraft((current) => ({ ...current, sourceChunkIdsJson: event.target.value }))}
                                                rows={3}
                                            />
                                        </Field>
                                    </div>

                                    <div className="repository-editor-inline__footer">
                                        <div className="page-copy">
                                            {itemEditorMode === "create"
                                                ? "新增题目会直接进入当前问答集，后续练习默认使用最新内容。"
                                                : "保存后会覆盖当前题目的默认训练版本。"}
                                        </div>
                                        <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                                            {itemEditorMode === "edit" && activeItem ? (
                                                <BaseButton
                                                    variant="outline"
                                                    type="button"
                                                    disabled={deleteQuestionItemMutation.isPending}
                                                    onClick={deleteActiveItem}
                                                >
                                                    {deleteQuestionItemMutation.isPending ? "删除中" : "删除题目"}
                                                </BaseButton>
                                            ) : null}
                                            <BaseButton variant="ghost" type="button" onClick={closeItemEditor}>
                                                取消
                                            </BaseButton>
                                            <BaseButton
                                                variant="primary"
                                                type="button"
                                                disabled={
                                                    createQuestionItemMutation.isPending
                                                    || updateQuestionItemMutation.isPending
                                                    || !itemDraft.question.trim()
                                                    || !itemDraft.knowledgeNote.trim()
                                                    || !itemDraft.answer.trim()
                                                    || !itemDraft.moduleTag.trim()
                                                }
                                                onClick={saveItemEditor}
                                            >
                                                {createQuestionItemMutation.isPending || updateQuestionItemMutation.isPending
                                                    ? "保存中"
                                                    : itemEditorMode === "create"
                                                        ? "保存新题"
                                                        : "保存修改"}
                                            </BaseButton>
                                        </div>
                                    </div>

                                    {createQuestionItemMutation.isError ? (
                                        <div className="page-copy" style={{ color: "var(--ink)" }}>
                                            新增失败：{createQuestionItemMutation.error instanceof Error ? createQuestionItemMutation.error.message : "请稍后重试"}
                                        </div>
                                    ) : null}
                                    {updateQuestionItemMutation.isError ? (
                                        <div className="page-copy" style={{ color: "var(--ink)" }}>
                                            保存失败：{updateQuestionItemMutation.error instanceof Error ? updateQuestionItemMutation.error.message : "请稍后重试"}
                                        </div>
                                    ) : null}
                                    {deleteQuestionItemMutation.isError ? (
                                        <div className="page-copy" style={{ color: "var(--ink)" }}>
                                            删除失败：{deleteQuestionItemMutation.error instanceof Error ? deleteQuestionItemMutation.error.message : "请稍后重试"}
                                        </div>
                                    ) : null}
                                </div>
                            ) : (
                                <>
                                    <div className="repository-header">
                                        <div>
                                            {editingSetMeta ? (
                                                <div className="repository-title-editor">
                                                    <TextInput
                                                        value={setTitleDraft}
                                                        onChange={(event) => setSetTitleDraft(event.target.value)}
                                                        placeholder="输入问答集标题"
                                                    />
                                                    <BaseButton
                                                        variant="primary"
                                                        type="button"
                                                        disabled={updateQuestionSetMutation.isPending || !setTitleDraft.trim()}
                                                        onClick={async () => {
                                                            await updateQuestionSetMutation.mutateAsync({
                                                                questionSetId: selectedSetQuery.data.id,
                                                                title: setTitleDraft.trim(),
                                                                description: setDescriptionDraft.trim(),
                                                            });
                                                            setEditingSetMeta(false);
                                                        }}
                                                    >
                                                        {updateQuestionSetMutation.isPending ? "保存中" : "保存标题"}
                                                    </BaseButton>
                                                    <BaseButton
                                                        variant="ghost"
                                                        type="button"
                                                        onClick={() => {
                                                            setEditingSetMeta(false);
                                                            setSetTitleDraft(selectedSetQuery.data.title);
                                                            setSetDescriptionDraft(selectedSetQuery.data.description);
                                                        }}
                                                    >
                                                        取消
                                                    </BaseButton>
                                                    <TextArea
                                                        value={setDescriptionDraft}
                                                        onChange={(event) => setSetDescriptionDraft(event.target.value)}
                                                        rows={4}
                                                        aria-label="问答集描述"
                                                    />
                                                </div>
                                            ) : (
                                                <>
                                                    <h1 className="hero-title" style={{ fontSize: 34 }}>
                                                        {selectedSetQuery.data.title}
                                                    </h1>
                                                    <p className="page-copy" style={{ maxWidth: 680, marginTop: 12 }}>
                                                        {selectedSetDescription || "本问答集用于维护训练资产，支持逐题打磨问题、知识笔记和标准答案。"}
                                                    </p>
                                                    {parseModuleTags(selectedSetQuery.data.moduleTagsJson).length ? (
                                                        <div className="repository-header__tags">
                                                            {parseModuleTags(selectedSetQuery.data.moduleTagsJson).map((tag) => (
                                                                <Tag key={tag}>{tag}</Tag>
                                                            ))}
                                                        </div>
                                                    ) : null}
                                                </>
                                            )}
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

                                        <div style={{ marginTop: 24, marginBottom: 28, display: "flex", gap: 12, flexWrap: "wrap" }}>
                                        <LinkButton to={`/quiz?questionSetId=${selectedSetQuery.data.id}`} variant="primary">
                                            开始练习
                                        </LinkButton>
                                        <BaseButton variant="ghost" type="button" onClick={() => setEditingSetMeta(true)}>
                                            编辑信息
                                        </BaseButton>
                                        <BaseButton
                                            variant="outline"
                                            type="button"
                                            disabled={deleteQuestionSetMutation.isPending}
                                            onClick={async () => {
                                                if (window.confirm(`确认删除问答集 ${selectedSetQuery.data?.title} 吗？`)) {
                                                    await deleteQuestionSetMutation.mutateAsync(selectedSetQuery.data.id);
                                                    navigate("/repository", { replace: true });
                                                }
                                            }}
                                        >
                                            {deleteQuestionSetMutation.isPending ? "删除中" : "删除问答集"}
                                        </BaseButton>
                                    </div>

                                    <div className="repository-workspace">
                                        <section className="repository-items-panel">
                                            <div className="repository-panel__header">
                                                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                                                    <h3 style={{ margin: 0, fontSize: 18 }}>题目目录</h3>
                                                    <button
                                                        type="button"
                                                        onClick={openCreateItemEditor}
                                                        aria-label="新增题目"
                                                        title="新增题目"
                                                        style={{
                                                            display: "inline-flex",
                                                            alignItems: "center",
                                                            justifyContent: "center",
                                                            width: 32,
                                                            height: 32,
                                                            border: "1px solid var(--line)",
                                                            borderRadius: 999,
                                                            background: "var(--bg-pill)",
                                                            color: "var(--ink-soft)",
                                                            cursor: "pointer",
                                                        }}
                                                    >
                                                        <Plus size={16} />
                                                    </button>
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
                                                <div className="qa-text">当前问答集还没有题目，先新增一题。</div>
                                            ) : null}
                                            {selectedSetItemsQuery.data?.length ? (
                                                <div className="repository-items-scroll">
                                                    <div className="repository-item-list">
                                                        {selectedSetItemsQuery.data.map((item) => (
                                                            <button
                                                                key={item.id}
                                                                type="button"
                                                                className={cn(
                                                                    "repository-item-card",
                                                                    item.id === activeItemId && "repository-item-card--active",
                                                                )}
                                                                onClick={() => openEditItemEditor(item)}
                                                            >
                                                                <strong>{item.question}</strong>
                                                                <span>{item.moduleTag}</span>
                                                                <small>{item.difficulty || "未标注难度"}</small>
                                                            </button>
                                                        ))}
                                                    </div>
                                                </div>
                                            ) : null}
                                        </section>
                                    </div>
                                </>
                            )
                        ) : null}
                    </div>
                ) : (
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
                                                <span>使用次数 {selectedDocumentUseCount} 次</span>
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
                                            onClick={async () => {
                                                const confirmMessage = `确认删除 ${selectedDocumentQuery.data?.fileName} 吗？`;
                                                if (window.confirm(confirmMessage)) {
                                                    await deleteDocumentMutation.mutateAsync(selectedDocumentQuery.data.id);
                                                    setActiveDocumentId("");
                                                    setDocumentDraft("");
                                                    setDocumentEditorMode("view");
                                                }
                                            }}
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
                                        ) : selectedDocumentQuery.data.fileType === "markdown" ? (
                                            <MarkdownRenderer content={documentBody} className="document-markdown--doc" />
                                        ) : (
                                            <pre className="document-plain-text">{documentBody || "暂无正文"}</pre>
                                        )}
                                    </div>
                                </div>
                            </>
                        ) : null}

                        {!selectedDocumentQuery.isLoading && !selectedDocumentQuery.data ? (
                                <div className="qa-feedback">
                                    <strong>暂无资料可预览</strong>
                                    <div className="qa-text">
                                        {hasDocuments ? "请从左侧选择一个资料文件。" : "资料上传功能尚未接入。"}
                                    </div>
                                </div>
                            ) : null}
                    </div>
                )}
            </GlassCard>
            </div>
        </div>
    );
}
