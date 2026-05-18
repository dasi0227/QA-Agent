import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { AlertTriangle } from "lucide-react";
import { BaseButton } from "@/components/base/button";
import { GlassCard } from "@/components/base/card";
import { Field, TextArea, TextInput, ToggleField } from "@/components/base/field";
import {
    parseDelimitedValues,
    useDeleteQuestionItemMutation,
    useQuestionItemQuery,
    useQuestionSetItemsQuery,
    useUpdateQuestionItemMutation,
} from "@/lib/api/hooks";
import type { QuestionItem, QuestionItemDraft } from "@/lib/api/types";
import { cn } from "@/lib/cn";

const emptyItemDraft: QuestionItemDraft = {
    question: "",
    knowledgeNote: "",
    answer: "",
    moduleTag: "",
    difficulty: "",
    keywords: "",
    sourceReliable: true,
    sourceChunkIdsJson: "",
};

function toQuestionItemDraft(item: QuestionItem): QuestionItemDraft {
    return {
        question: item.question,
        knowledgeNote: item.knowledgeNote,
        answer: item.answer,
        moduleTag: item.moduleTag,
        difficulty: item.difficulty || "",
        keywords: item.keywords || "",
        sourceReliable: item.sourceReliable,
        sourceChunkIdsJson: item.sourceChunkIdsJson || "",
    };
}

export function QuestionPage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const qaSetId = searchParams.get("qaSetId") || "";
    const itemIdParam = searchParams.get("itemId") || "";

    const selectedSetItemsQuery = useQuestionSetItemsQuery(qaSetId);
    const updateQuestionItemMutation = useUpdateQuestionItemMutation();
    const deleteQuestionItemMutation = useDeleteQuestionItemMutation();

    const itemList = selectedSetItemsQuery.data ?? [];
    const fallbackItemId = itemList[0]?.id ?? "";
    const hasValidItemId = itemList.some((item) => item.id === itemIdParam);
    const activeItemId = hasValidItemId ? itemIdParam : fallbackItemId;

    const activeItemQuery = useQuestionItemQuery(activeItemId);
    const activeItem = activeItemQuery.data ?? null;

    const [pageMode, setPageMode] = useState<"view" | "edit">("view");
    const [itemDraft, setItemDraft] = useState<QuestionItemDraft>(emptyItemDraft);
    const [evidenceExpanded, setEvidenceExpanded] = useState(false);

    const keywordList = useMemo(() => parseDelimitedValues(activeItem?.keywords), [activeItem?.keywords]);
    const moduleTagList = useMemo(() => parseDelimitedValues(activeItem?.moduleTag), [activeItem?.moduleTag]);
    const sourceChunkIdList = useMemo(() => parseDelimitedValues(activeItem?.sourceChunkIdsJson), [activeItem?.sourceChunkIdsJson]);
    const visibleSourceChunkIds = evidenceExpanded ? sourceChunkIdList : sourceChunkIdList.slice(0, 2);

    useEffect(() => {
        if (!qaSetId) {
            navigate("/repository/qa-set", { replace: true });
        }
    }, [qaSetId, navigate]);

    useEffect(() => {
        if (!qaSetId || selectedSetItemsQuery.isLoading || selectedSetItemsQuery.isError || itemList.length === 0) {
            return;
        }
        if (!itemIdParam || !hasValidItemId) {
            navigate(`/repository/question?qaSetId=${qaSetId}&itemId=${fallbackItemId}`, { replace: true });
        }
    }, [fallbackItemId, hasValidItemId, itemIdParam, itemList.length, navigate, qaSetId, selectedSetItemsQuery.isError, selectedSetItemsQuery.isLoading]);

    useEffect(() => {
        setPageMode("view");
        setEvidenceExpanded(false);
    }, [activeItemId]);

    useEffect(() => {
        if (activeItem) {
            setItemDraft(toQuestionItemDraft(activeItem));
        } else if (!activeItemId) {
            setItemDraft(emptyItemDraft);
        }
    }, [activeItem, activeItemId]);

    const handleSelectItem = (itemId: string) => {
        navigate(`/repository/question?qaSetId=${qaSetId}&itemId=${itemId}`, { replace: true });
    };

    const openEditMode = () => {
        if (!activeItem) return;
        setItemDraft(toQuestionItemDraft(activeItem));
        setPageMode("edit");
    };

    const cancelEditMode = () => {
        setItemDraft(activeItem ? toQuestionItemDraft(activeItem) : emptyItemDraft);
        setPageMode("view");
    };

    const saveItemEditor = async () => {
        if (!activeItemId) return;
        await updateQuestionItemMutation.mutateAsync({
            qaSetId,
            questionItemId: activeItemId,
            ...itemDraft,
        });
        setPageMode("view");
    };

    const deleteActiveItem = async () => {
        if (!activeItem) return;
        if (window.confirm(`确认删除题目“${activeItem.question}”吗？`)) {
            const nextItemId = itemList.find((item) => item.id !== activeItem.id)?.id || "";
            await deleteQuestionItemMutation.mutateAsync({
                qaSetId,
                questionItemId: activeItem.id,
            });
            setPageMode("view");
            navigate(
                nextItemId
                    ? `/repository/question?qaSetId=${qaSetId}&itemId=${nextItemId}`
                    : `/repository/question?qaSetId=${qaSetId}`,
                { replace: true },
            );
        }
    };

    if (!qaSetId) {
        return null;
    }

    const showMainLoading = selectedSetItemsQuery.isLoading || (Boolean(activeItemId) && activeItemQuery.isLoading);
    const showMainError = Boolean(activeItemId) && activeItemQuery.isError;

    return (
        <div className="page-frame">
            <div className="layout-two-col repository-layout">
                <aside className="sidebar">
                    <div className="repository-mode-switch" style={{ marginBottom: 18 }}>
                        <button className="choice-btn" type="button" onClick={() => navigate("/repository/qa-set")}>问答集</button>
                        <button className="choice-btn" type="button" onClick={() => navigate("/repository/document")}>资料库</button>
                        <button className="choice-btn choice-btn--active" type="button">题目表</button>
                    </div>
                    <div className="tree">
                        <div className="sidebar__upload-area sidebar__action-area">
                            <button
                                type="button"
                                className="sidebar__upload-btn"
                                onClick={() => window.alert("接口尚未实现")}
                            >
                                新增题目
                            </button>
                        </div>
                        <div className="subtree tree">
                            {selectedSetItemsQuery.isLoading ? <div className="tree-item">加载中...</div> : null}
                            {selectedSetItemsQuery.isError ? (
                                <div className="tree-item" style={{ color: "var(--ink)" }}>
                                    {selectedSetItemsQuery.error instanceof Error
                                        ? selectedSetItemsQuery.error.message
                                        : "题目列表加载失败"}
                                </div>
                            ) : null}
                            {itemList.map((item) => (
                                <button
                                    key={item.id}
                                    type="button"
                                    className={cn("tree-item", "tree-item--entry", item.id === activeItemId && "tree-item--active")}
                                    onClick={() => handleSelectItem(item.id)}
                                >
                                    <span className="tree-item__label">{item.question}</span>
                                </button>
                            ))}
                            {!selectedSetItemsQuery.isLoading && !selectedSetItemsQuery.isError && !itemList.length ? (
                                <div className="tree-item">暂无题目</div>
                            ) : null}
                        </div>
                    </div>
                </aside>

                <GlassCard className="panel repository-main-panel" style={{ padding: 24 }}>
                    <div className="fade-in question-detail-page">
                        <div className="repository-detail-view fade-in">
                            {showMainLoading ? (
                                <div className="qa-feedback">
                                    <strong>正在加载题目详情...</strong>
                                </div>
                            ) : null}

                            {showMainError ? (
                                <div className="qa-feedback">
                                    <strong>题目详情加载失败</strong>
                                    <div className="qa-text">
                                        {activeItemQuery.error instanceof Error ? activeItemQuery.error.message : "请稍后重试"}
                                    </div>
                                    <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                                        <BaseButton variant="soft" type="button" onClick={() => activeItemQuery.refetch()}>
                                            重新加载
                                        </BaseButton>
                                    </div>
                                </div>
                            ) : null}

                            {!showMainLoading && !showMainError && activeItem ? (
                                pageMode === "edit" ? (
                                    <>
                                        <div className="repository-detail-view__title">
                                            <div className="question-detail-header-row">
                                                <div className="question-detail-header-copy">
                                                    <h1 className="hero-title question-detail-title">编辑题目</h1>
                                                    <p className="page-copy" style={{ margin: 0 }}>
                                                        调整题目内容、知识点分析、关键字和资料可靠性。
                                                    </p>
                                                </div>
                                            </div>
                                        </div>

                                        <div className="repository-editor-inline__body">
                                            <Field label="问题">
                                                <TextArea
                                                    value={itemDraft.question}
                                                    onChange={(event) => setItemDraft((current) => ({ ...current, question: event.target.value }))}
                                                    rows={3}
                                                />
                                            </Field>
                                            <div className="result-grid question-detail-edit-grid">
                                                <Field label="模块">
                                                    <TextInput
                                                        value={itemDraft.moduleTag}
                                                        onChange={(event) => setItemDraft((current) => ({ ...current, moduleTag: event.target.value }))}
                                                    />
                                                </Field>
                                                <Field label="难度">
                                                    <TextInput
                                                        value={itemDraft.difficulty}
                                                        onChange={(event) => setItemDraft((current) => ({ ...current, difficulty: event.target.value }))}
                                                    />
                                                </Field>
                                            </div>
                                            <Field label="关键字" hint="逗号分隔的短语字符串">
                                                <TextArea
                                                    value={itemDraft.keywords}
                                                    onChange={(event) => setItemDraft((current) => ({ ...current, keywords: event.target.value }))}
                                                    rows={3}
                                                />
                                            </Field>
                                            <ToggleField
                                                label="资料可靠"
                                                checked={itemDraft.sourceReliable}
                                                onChange={(checked) => setItemDraft((current) => ({ ...current, sourceReliable: checked }))}
                                                hint={itemDraft.sourceReliable ? "资料可靠" : "资料存在明显错误或冲突"}
                                            />
                                            <Field label="知识点分析">
                                                <TextArea
                                                    value={itemDraft.knowledgeNote}
                                                    onChange={(event) => setItemDraft((current) => ({ ...current, knowledgeNote: event.target.value }))}
                                                    rows={6}
                                                />
                                            </Field>
                                            <Field label="标准回答">
                                                <TextArea
                                                    value={itemDraft.answer}
                                                    onChange={(event) => setItemDraft((current) => ({ ...current, answer: event.target.value }))}
                                                    rows={6}
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
                                            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                                                <BaseButton
                                                    variant="outline"
                                                    type="button"
                                                    disabled={deleteQuestionItemMutation.isPending}
                                                    onClick={deleteActiveItem}
                                                >
                                                    {deleteQuestionItemMutation.isPending ? "删除中" : "删除题目"}
                                                </BaseButton>
                                                <BaseButton
                                                    variant="primary"
                                                    type="button"
                                                    disabled={
                                                        updateQuestionItemMutation.isPending
                                                        || !itemDraft.question.trim()
                                                        || !itemDraft.knowledgeNote.trim()
                                                        || !itemDraft.answer.trim()
                                                        || !itemDraft.moduleTag.trim()
                                                    }
                                                    onClick={saveItemEditor}
                                                >
                                                    {updateQuestionItemMutation.isPending ? "保存中" : "保存修改"}
                                                </BaseButton>
                                                <BaseButton variant="ghost" type="button" onClick={cancelEditMode}>
                                                    取消
                                                </BaseButton>
                                            </div>
                                        </div>
                                    </>
                                ) : (
                                    <>
                                        <div className="repository-detail-view__title">
                                            <div className="question-detail-header-row question-detail-header-row--view">
                                                <div className="question-detail-header-copy">
                                                    <h1 className="hero-title question-detail-title">{activeItem.question}</h1>
                                                </div>
                                                <aside className="question-info-card">
                                                    <div className="question-info-card__header">
                                                        <h3 className="question-info-card__title">题目信息</h3>
                                                        {!activeItem.sourceReliable ? (
                                                            <div className="question-reliability-indicator" aria-label="资料存在明显错误或冲突">
                                                                <AlertTriangle size={14} />
                                                                <span className="question-reliability-indicator__tooltip">资料存在明显错误或冲突</span>
                                                            </div>
                                                        ) : null}
                                                    </div>
                                                    <div className="question-info-card__body">
                                                        <div className="question-info-card__item">
                                                            <span>模块</span>
                                                            {moduleTagList.length ? (
                                                                <div className="question-info-card__tags">
                                                                    {moduleTagList.map((moduleTag) => (
                                                                        <span key={moduleTag} className="question-info-card__tag">{moduleTag}</span>
                                                                    ))}
                                                                </div>
                                                            ) : (
                                                                <span className="question-info-card__empty">未标注</span>
                                                            )}
                                                        </div>
                                                        <div className="question-info-card__item">
                                                            <span>难度</span>
                                                            <strong>{activeItem.difficulty || "未标注"}</strong>
                                                        </div>
                                                        <div className="question-info-card__item">
                                                            <span>关键字</span>
                                                            <div className="question-info-card__tags">
                                                                {keywordList.length ? keywordList.map((keyword) => (
                                                                    <span key={keyword} className="question-info-card__tag">{keyword}</span>
                                                                )) : <span className="question-info-card__empty">暂无关键字</span>}
                                                            </div>
                                                        </div>
                                                    </div>
                                                </aside>
                                            </div>
                                        </div>

                                        <div className="question-detail-actions">
                                            <BaseButton variant="soft" type="button" onClick={openEditMode}>
                                                编辑题目
                                            </BaseButton>
                                        </div>

                                        <section className="question-detail-section">
                                            <div className="question-detail-section__header">
                                                <h2>标准回答</h2>
                                            </div>
                                            <div className="question-detail-section__body">
                                                <p>{activeItem.answer || "暂无标准回答"}</p>
                                            </div>
                                        </section>

                                        <section className="question-detail-section">
                                            <div className="question-detail-section__header">
                                                <h2>知识点分析</h2>
                                            </div>
                                            <div className="question-detail-section__body">
                                                <p>{activeItem.knowledgeNote || "暂无知识点分析"}</p>
                                            </div>
                                        </section>

                                        <section className="question-detail-section">
                                            <div className="question-detail-section__header">
                                                <h2>证据原文</h2>
                                                <BaseButton
                                                    variant="ghost"
                                                    type="button"
                                                    className="question-evidence-panel__toggle"
                                                    disabled={sourceChunkIdList.length <= 2}
                                                    onClick={() => setEvidenceExpanded((current) => !current)}
                                                >
                                                    {evidenceExpanded ? "收起" : "展开全部"}
                                                </BaseButton>
                                            </div>
                                            <div className={cn("question-evidence-panel__body", evidenceExpanded && "question-evidence-panel__body--expanded")}>
                                                {visibleSourceChunkIds.length ? (
                                                    visibleSourceChunkIds.map((sourceChunkId) => (
                                                        <div key={sourceChunkId} className="question-evidence-panel__item">
                                                            {sourceChunkId}
                                                        </div>
                                                    ))
                                                ) : (
                                                    <div className="question-evidence-panel__empty">暂无证据片段 ID</div>
                                                )}
                                            </div>
                                        </section>
                                    </>
                                )
                            ) : null}

                            {!showMainLoading && !showMainError && !activeItemId && !itemList.length ? (
                                <div className="qa-feedback">
                                    <strong>当前题集暂无题目</strong>
                                    <div className="qa-text">后端暂未开放手动新增题目接口，题目主要由问答集生成任务自动创建。</div>
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
                    </div>
                </GlassCard>
            </div>
        </div>
    );
}
