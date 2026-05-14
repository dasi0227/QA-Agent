import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { ArrowLeft } from "lucide-react";
import { BaseButton } from "@/components/base/button";
import { GlassCard } from "@/components/base/card";
import { Field, TextArea, TextInput } from "@/components/base/field";
import {
    useDeleteQuestionItemMutation,
    useQuestionSetItemsQuery,
    useQuestionSetQuery,
    useQuestionSetsQuery,
    useUpdateQuestionItemMutation,
    parseModuleTags,
} from "@/lib/api/hooks";
import { cn } from "@/lib/cn";

const emptyItemDraft = {
    question: "",
    knowledgeNote: "",
    answer: "",
    moduleTag: "",
    difficulty: "",
    tip: "",
    sourceChunkIdsJson: "",
};

export function QuestionPage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const qaSetId = searchParams.get("qaSetId") || "";
    const itemIdParam = searchParams.get("itemId") || "";

    const questionSetsQuery = useQuestionSetsQuery();
    const selectedSetQuery = useQuestionSetQuery(qaSetId);
    const selectedSetItemsQuery = useQuestionSetItemsQuery(qaSetId);
    const updateQuestionItemMutation = useUpdateQuestionItemMutation();
    const deleteQuestionItemMutation = useDeleteQuestionItemMutation();

    const [activeItemId, setActiveItemId] = useState(itemIdParam);
    const [itemEditorMode, setItemEditorMode] = useState<"edit" | null>(null);
    const [itemDraft, setItemDraft] = useState(emptyItemDraft);

    const itemList = selectedSetItemsQuery.data ?? [];
    const activeItem = itemList.find((item) => item.id === activeItemId) ?? null;

    // Redirect if no qaSetId
    useEffect(() => {
        if (!qaSetId) {
            navigate("/repository/qa-set", { replace: true });
        }
    }, [qaSetId, navigate]);

    // Sync activeItemId from URL param
    useEffect(() => {
        if (itemIdParam && itemIdParam !== activeItemId) {
            setActiveItemId(itemIdParam);
        }
    }, [itemIdParam]);

    // Load item draft when switching items
    useEffect(() => {
        if (itemList.length === 0) {
            setActiveItemId("");
            setItemDraft(emptyItemDraft);
            return;
        }
        const nextActiveId = itemList.some((item) => item.id === activeItemId) ? activeItemId : itemList[0].id;
        setActiveItemId(nextActiveId);
        const found = itemList.find((item) => item.id === nextActiveId);
        if (found) {
            setItemDraft({
                question: found.question,
                knowledgeNote: found.knowledgeNote,
                answer: found.answer,
                moduleTag: found.moduleTag,
                difficulty: found.difficulty || "",
                tip: found.tip || "",
                sourceChunkIdsJson: found.sourceChunkIdsJson || "",
            });
        }
    }, [activeItemId, itemEditorMode, itemList]);

    const openEditItemEditor = (item: NonNullable<typeof activeItem>) => {
        navigate(`/repository/question?qaSetId=${qaSetId}&itemId=${item.id}`, { replace: true });
        setActiveItemId(item.id);
        setItemDraft({
            question: item.question,
            knowledgeNote: item.knowledgeNote,
            answer: item.answer,
            moduleTag: item.moduleTag,
            difficulty: item.difficulty || "",
            tip: item.tip || "",
            sourceChunkIdsJson: item.sourceChunkIdsJson || "",
        });
        setItemEditorMode("edit");
    };

    const closeItemEditor = () => {
        navigate(`/repository/qa-set/${qaSetId}`, { replace: true });
    };

    const saveItemEditor = async () => {
        if (!selectedSetQuery.data || !activeItem) return;

        await updateQuestionItemMutation.mutateAsync({
            qaSetId: selectedSetQuery.data.id,
            questionItemId: activeItem.id,
            ...itemDraft,
        });
        closeItemEditor();
    };

    const deleteActiveItem = async () => {
        if (!selectedSetQuery.data || !activeItem) return;
        if (window.confirm(`确认删除题目“${activeItem.question}”吗？`)) {
            await deleteQuestionItemMutation.mutateAsync({
                qaSetId: selectedSetQuery.data.id,
                questionItemId: activeItem.id,
            });
            setActiveItemId("");
            closeItemEditor();
        }
    };

    if (!qaSetId) {
        return null;
    }

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
                        <div className="tree-item" style={{ cursor: "default", opacity: 0.6 }}>
                            <span className="tree-item__label">{selectedSetQuery.data?.title || "加载中..."}</span>
                        </div>
                        <div className="tree-item">
                            <span className="tree-item__label">题目表</span>
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
                </aside>

                <GlassCard className="panel repository-main-panel" style={{ padding: 24 }}>
                    <div className="fade-in">
                        <div className="repository-detail-view fade-in">
                            <div className="repository-detail-view__header document-detail-view__header">
                                <BaseButton
                                    variant="ghost"
                                    type="button"
                                    leadingIcon={<ArrowLeft size={14} />}
                                    onClick={closeItemEditor}
                                >
                                    返回问答集
                                </BaseButton>
                            </div>

                            <div className="repository-detail-view__title">
                                <h1 className="hero-title" style={{ fontSize: 34, margin: 0 }}>
                                    {activeItem?.question || "题目详情"}
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
                                        value={itemDraft.tip}
                                        onChange={(event) => setItemDraft((current) => ({ ...current, tip: event.target.value }))}
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
                                        {updateQuestionItemMutation.isPending
                                            ? "保存中"
                                            : "保存修改"}
                                    </BaseButton>
                                    <BaseButton variant="ghost" type="button" onClick={closeItemEditor}>
                                        取消
                                    </BaseButton>
                                </div>
                            </div>

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
