import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { AlertTriangle, Plus, X } from "lucide-react";
import { BaseButton } from "@/components/base/button";
import { GlassCard } from "@/components/base/card";
import { Field, Select, TextArea } from "@/components/base/field";
import {
    parseDelimitedValues,
    useDocumentChunksQuery,
    useQuestionItemQuery,
    useQuestionSetItemsQuery,
    useUpdateQuestionItemMutation,
} from "@/lib/api/hooks";
import type { QuestionItem, QuestionItemDraft } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { useGlobalErrorDialog } from "@/lib/error/ErrorDialogProvider";

const MODULE_OPTIONS = [
    "JavaSE", "OOP", "JVM", "IO", "JUC", "JCF", "MCP", "SKILL", "AGENT", "Harness",
    "SpringAI", "LangChain4J", "SpringFramework", "SpringMVC", "SpringBoot", "SpringCloud",
    "MyBatis", "MySQL", "PostgreSQL", "Redis", "MQ", "Linux", "Docker", "Maven", "Git",
    "Zookeeper", "Elasticsearch", "K8s", "Grafana", "分布式", "高并发", "微服务", "设计模式",
    "数据结构与算法", "计算机网络", "操作系统", "测试", "运维", "安全",
] as const;

const emptyItemDraft: QuestionItemDraft = {
    question: "",
    knowledgeNote: "",
    answer: "",
    moduleTag: "",
    difficulty: "MEDIUM",
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
        difficulty: item.difficulty || "MEDIUM",
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
    const { showUnimplemented } = useGlobalErrorDialog();

    const itemList = selectedSetItemsQuery.data ?? [];
    const fallbackItemId = itemList[0]?.id ?? "";
    const hasValidItemId = itemList.some((item) => item.id === itemIdParam);
    const activeItemId = hasValidItemId ? itemIdParam : fallbackItemId;

    const activeItemQuery = useQuestionItemQuery(activeItemId);
    const activeItem = activeItemQuery.data ?? null;

    const [itemDraft, setItemDraft] = useState<QuestionItemDraft>(emptyItemDraft);
    const [editDialogOpen, setEditDialogOpen] = useState(false);
    const [selectedModuleDraft, setSelectedModuleDraft] = useState<string[]>([]);
    const [selectedKeywordsDraft, setSelectedKeywordsDraft] = useState<string[]>([]);
    const [keywordInput, setKeywordInput] = useState("");
    const [selectedEvidenceChunkId, setSelectedEvidenceChunkId] = useState("");

    const keywordList = useMemo(() => parseDelimitedValues(activeItem?.keywords), [activeItem?.keywords]);
    const moduleTagList = useMemo(() => parseDelimitedValues(activeItem?.moduleTag), [activeItem?.moduleTag]);
    const sourceChunkIdList = useMemo(() => parseDelimitedValues(activeItem?.sourceChunkIdsJson), [activeItem?.sourceChunkIdsJson]);
    const evidenceChunksQuery = useDocumentChunksQuery(sourceChunkIdList);
    const evidenceChunkMap = useMemo(() => {
        const map = new Map<string, NonNullable<typeof evidenceChunksQuery.data>[number]>();
        (evidenceChunksQuery.data ?? []).forEach((chunk) => {
            map.set(chunk.id, chunk);
        });
        return map;
    }, [evidenceChunksQuery.data]);
    const selectedEvidenceChunk = selectedEvidenceChunkId ? evidenceChunkMap.get(selectedEvidenceChunkId) ?? null : null;
    const availableModules = useMemo(
        () => MODULE_OPTIONS.filter((moduleTag) => !selectedModuleDraft.includes(moduleTag)),
        [selectedModuleDraft],
    );

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
        setEditDialogOpen(false);
        setSelectedEvidenceChunkId("");
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

    const openEditDialog = () => {
        if (!activeItem) return;
        const draft = toQuestionItemDraft(activeItem);
        setItemDraft(draft);
        setSelectedModuleDraft(parseDelimitedValues(draft.moduleTag));
        setSelectedKeywordsDraft(parseDelimitedValues(draft.keywords));
        setKeywordInput("");
        setEditDialogOpen(true);
    };

    const closeEditDialog = () => {
        setKeywordInput("");
        setEditDialogOpen(false);
        setItemDraft(activeItem ? toQuestionItemDraft(activeItem) : emptyItemDraft);
    };

    const addKeyword = () => {
        const value = keywordInput.trim();
        if (!value || selectedKeywordsDraft.includes(value)) {
            setKeywordInput("");
            return;
        }
        setSelectedKeywordsDraft((current) => [...current, value]);
        setKeywordInput("");
    };

    const saveItemEditor = async () => {
        if (!activeItemId) return;
        await updateQuestionItemMutation.mutateAsync({
            ...itemDraft,
            qaSetId,
            questionItemId: activeItemId,
            moduleTag: selectedModuleDraft.join(","),
            keywords: selectedKeywordsDraft.join(","),
        });
        setEditDialogOpen(false);
    };

    if (!qaSetId) {
        return null;
    }

    const showMainLoading = selectedSetItemsQuery.isLoading || (Boolean(activeItemId) && activeItemQuery.isLoading);
    const showMainError = Boolean(activeItemId) && activeItemQuery.isError;

    return (
        <>
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
                                    onClick={() => showUnimplemented("手动新增题目功能尚未开放。")}
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
                                    <div className="status-card">
                                        <strong>正在加载题目详情...</strong>
                                    </div>
                                ) : null}

                                {showMainError ? (
                                    <div className="status-card">
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
                                <div className="question-detail-layout">
                                    <div className="question-detail-main">
                                        <div className="repository-detail-view__title">
                                            <div className="question-detail-header-row">
                                                <div className="question-detail-header-copy">
                                                    <h1 className="hero-title question-detail-title">{activeItem.question}</h1>
                                                </div>
                                            </div>
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
                                                <h2>知识点</h2>
                                            </div>
                                            <div className="question-detail-section__body">
                                                <p>{activeItem.knowledgeNote || "暂无知识点"}</p>
                                            </div>
                                        </section>

                                        <section className="question-detail-section">
                                            <div className="question-detail-section__header">
                                                <h2>证据原文</h2>
                                            </div>
                                            <div className="question-evidence-panel__body question-evidence-panel__body--expanded">
                                                {sourceChunkIdList.length && evidenceChunksQuery.isLoading ? (
                                                    <div className="question-evidence-panel__empty">证据摘要加载中...</div>
                                                ) : null}
                                                {sourceChunkIdList.length && evidenceChunksQuery.isError ? (
                                                    <div className="question-evidence-panel__empty">
                                                        {evidenceChunksQuery.error instanceof Error ? evidenceChunksQuery.error.message : "证据加载失败"}
                                                    </div>
                                                ) : null}
                                                {sourceChunkIdList.length ? (
                                                    sourceChunkIdList.map((sourceChunkId) => {
                                                        const chunk = evidenceChunkMap.get(sourceChunkId);
                                                        const summaryText = (chunk?.summary || chunk?.content || "暂无摘要").trim();
                                                        return (
                                                            <button
                                                                key={sourceChunkId}
                                                                type="button"
                                                                className="question-evidence-panel__item question-evidence-panel__item--button"
                                                                onClick={() => setSelectedEvidenceChunkId(sourceChunkId)}
                                                            >
                                                                <span className="question-evidence-panel__summary">{summaryText}</span>
                                                            </button>
                                                        );
                                                    })
                                                ) : (
                                                    <div className="question-evidence-panel__empty">暂无证据片段 ID</div>
                                                )}
                                            </div>
                                        </section>
                                    </div>

                                    <aside className="question-side-rail">
                                        <div className="question-side-rail__panel">
                                            <div className="question-info-card">
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
                                                        <span>难度</span>
                                                        <strong>{activeItem.difficulty || "未标注"}</strong>
                                                    </div>
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
                                                        <span>关键字</span>
                                                        {keywordList.length ? (
                                                            <ul className="question-info-card__keyword-list">
                                                                {keywordList.map((keyword) => (
                                                                    <li key={keyword}>{keyword}</li>
                                                                ))}
                                                            </ul>
                                                        ) : <span className="question-info-card__empty">暂无关键字</span>}
                                                    </div>
                                                </div>
                                            </div>
                                            <div className="question-side-rail__divider" />
                                            <div className="question-side-rail__actions">
                                                <BaseButton
                                                    variant="primary"
                                                    type="button"
                                                    className="question-side-rail__action-btn"
                                                    onClick={() => navigate(`/quiz?questionSetId=${qaSetId}`)}
                                                >
                                                    开始测试
                                                </BaseButton>
                                                <BaseButton
                                                    variant="soft"
                                                    type="button"
                                                    className="question-side-rail__action-btn"
                                                    onClick={openEditDialog}
                                                >
                                                    编辑题目
                                                </BaseButton>
                                                <BaseButton
                                                    variant="soft"
                                                    type="button"
                                                    className="question-side-rail__action-btn"
                                                    onClick={() => navigate(`/repository/qa-set/${qaSetId}`)}
                                                >
                                                    返回问答集
                                                </BaseButton>
                                            </div>
                                        </div>
                                    </aside>
                                </div>
                            ) : null}

                                {!showMainLoading && !showMainError && !activeItemId && !itemList.length ? (
                                    <div className="status-card">
                                        <strong>当前题集暂无题目</strong>
                                        <div className="qa-text">后端暂未开放手动新增题目接口，题目主要由问答集生成任务自动创建。</div>
                                    </div>
                                ) : null}
                            </div>
                        </div>
                    </GlassCard>
                </div>
            </div>

            {editDialogOpen ? (
                <div className="doc-select-dialog" role="presentation" onClick={closeEditDialog}>
                    <div className="question-edit-dialog" role="dialog" aria-modal="true" aria-label="编辑题目" onClick={(event) => event.stopPropagation()}>
                        <div className="doc-select-dialog__header">
                            <h3 className="doc-select-dialog__title">编辑题目</h3>
                            <button type="button" className="doc-select-dialog__close" onClick={closeEditDialog}>
                                <X size={16} />
                            </button>
                        </div>

                        <div className="question-edit-dialog__body">
                            <Field label="问题">
                                <TextArea
                                    className="question-edit-dialog__textarea"
                                    value={itemDraft.question}
                                    onChange={(event) => setItemDraft((current) => ({ ...current, question: event.target.value }))}
                                    rows={3}
                                />
                            </Field>

                            <Field label="标准回答">
                                <TextArea
                                    className="question-edit-dialog__textarea"
                                    value={itemDraft.answer}
                                    onChange={(event) => setItemDraft((current) => ({ ...current, answer: event.target.value }))}
                                    rows={6}
                                />
                            </Field>

                            <Field label="知识点分析">
                                <TextArea
                                    className="question-edit-dialog__textarea"
                                    value={itemDraft.knowledgeNote}
                                    onChange={(event) => setItemDraft((current) => ({ ...current, knowledgeNote: event.target.value }))}
                                    rows={5}
                                />
                            </Field>

                            <section className="tag-dialog__section">
                                <div className="tag-dialog__section-head">
                                    <strong>模块</strong>
                                    <span>{selectedModuleDraft.length} 个</span>
                                </div>
                                <div className="tag-dialog__selected-list">
                                    {selectedModuleDraft.length ? selectedModuleDraft.map((moduleTag) => (
                                        <div key={moduleTag} className="tag-dialog__selected-item">
                                            <span>{moduleTag}</span>
                                            <button
                                                type="button"
                                                className="tag-dialog__selected-remove"
                                                aria-label={`移除模块 ${moduleTag}`}
                                                onClick={() => setSelectedModuleDraft((current) => current.filter((item) => item !== moduleTag))}
                                            >
                                                <X size={10} />
                                            </button>
                                        </div>
                                    )) : <div className="tag-dialog__empty">还没有选择模块</div>}
                                </div>
                                <div className="tag-dialog__pool">
                                    {availableModules.map((moduleTag) => (
                                        <button
                                            key={moduleTag}
                                            type="button"
                                            className="tag-dialog__pool-item"
                                            onClick={() => setSelectedModuleDraft((current) => [...current, moduleTag])}
                                        >
                                            {moduleTag}
                                        </button>
                                    ))}
                                </div>
                            </section>

                            <Field label="难度">
                                <Select
                                    value={itemDraft.difficulty}
                                    onChange={(event) => setItemDraft((current) => ({ ...current, difficulty: event.target.value }))}
                                >
                                    <option value="MEDIUM">MEDIUM</option>
                                    <option value="EASY">EASY</option>
                                    <option value="HARD">HARD</option>
                                </Select>
                            </Field>

                            <section className="tag-dialog__section">
                                <div className="tag-dialog__section-head">
                                    <strong>关键字</strong>
                                    <span>{selectedKeywordsDraft.length} 个</span>
                                </div>
                                <div className="tag-dialog__selected-list">
                                    {selectedKeywordsDraft.length ? selectedKeywordsDraft.map((keyword) => (
                                        <div key={keyword} className="tag-dialog__selected-item">
                                            <span>{keyword}</span>
                                            <button
                                                type="button"
                                                className="tag-dialog__selected-remove"
                                                aria-label={`移除关键字 ${keyword}`}
                                                onClick={() => setSelectedKeywordsDraft((current) => current.filter((item) => item !== keyword))}
                                            >
                                                <X size={10} />
                                            </button>
                                        </div>
                                    )) : <div className="tag-dialog__empty">还没有添加关键字</div>}
                                </div>
                                <div className="question-edit-keyword-input">
                                    <input
                                        className="input"
                                        value={keywordInput}
                                        onChange={(event) => setKeywordInput(event.target.value)}
                                        onKeyDown={(event) => {
                                            if (event.key === "Enter") {
                                                event.preventDefault();
                                                addKeyword();
                                            }
                                        }}
                                        placeholder="输入一个关键短语"
                                    />
                                    <BaseButton variant="soft" type="button" leadingIcon={<Plus size={14} />} onClick={addKeyword}>
                                        添加
                                    </BaseButton>
                                </div>
                            </section>
                        </div>

                        <div className="modal-card__footer">
                            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                                <BaseButton
                                    variant="primary"
                                    type="button"
                                    disabled={
                                        updateQuestionItemMutation.isPending
                                        || !itemDraft.question.trim()
                                        || !itemDraft.answer.trim()
                                        || !itemDraft.knowledgeNote.trim()
                                        || !selectedModuleDraft.length
                                        || !itemDraft.difficulty.trim()
                                    }
                                    onClick={saveItemEditor}
                                >
                                    {updateQuestionItemMutation.isPending ? "保存中" : "保存修改"}
                                </BaseButton>
                                <BaseButton variant="ghost" type="button" onClick={closeEditDialog}>
                                    取消
                                </BaseButton>
                            </div>
                        </div>
                    </div>
                </div>
            ) : null}

            {selectedEvidenceChunkId ? (
                <div className="doc-select-dialog" role="presentation" onClick={() => setSelectedEvidenceChunkId("")}>
                    <div
                        className="question-evidence-dialog"
                        role="dialog"
                        aria-modal="true"
                        aria-label="证据详情"
                        onClick={(event) => event.stopPropagation()}
                    >
                        <div className="doc-select-dialog__header">
                            <h3 className="doc-select-dialog__title">证据详情</h3>
                            <button type="button" className="doc-select-dialog__close" onClick={() => setSelectedEvidenceChunkId("")}>
                                <X size={16} />
                            </button>
                        </div>
                        <div className="question-evidence-dialog__body">
                            {selectedEvidenceChunk ? (
                                <>
                                    <div className="question-evidence-dialog__meta">
                                        <span className="question-evidence-dialog__label">资料</span>
                                        <button
                                            type="button"
                                            className="question-evidence-dialog__file-link"
                                            onClick={() => navigate(`/repository/document?documentId=${selectedEvidenceChunk.documentId}`)}
                                        >
                                            {selectedEvidenceChunk.fileName || "未命名资料"}
                                        </button>
                                    </div>
                                    <div className="question-evidence-dialog__meta">
                                        <span className="question-evidence-dialog__label">标题路径</span>
                                        <div className="question-evidence-dialog__value">{selectedEvidenceChunk.titlePath || "暂无标题路径"}</div>
                                    </div>
                                    <div className="question-evidence-dialog__meta">
                                        <span className="question-evidence-dialog__label">正文</span>
                                        <div className="question-evidence-dialog__content">{selectedEvidenceChunk.content || "暂无正文"}</div>
                                    </div>
                                </>
                            ) : (
                                <div className="question-evidence-dialog__content">未找到该证据详情。</div>
                            )}
                        </div>
                    </div>
                </div>
            ) : null}
        </>
    );
}
