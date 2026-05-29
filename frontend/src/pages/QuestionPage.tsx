import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { AlertTriangle, Info, Pencil, Plus, Trash2, X } from "lucide-react";
import { BaseButton, ChoiceButton } from "@/components/base/button";
import { TypeToConfirmDialog } from "@/components/base/type-to-confirm-dialog";
import { emitDasiBubble } from "@/components/dasi/DasiChatWidget";
import { useGlobalErrorDialog } from "@/lib/error/ErrorDialogProvider";
import { GlassCard } from "@/components/base/card";
import { Field, Select, TextArea, TextInput } from "@/components/base/field";
import { Tag } from "@/components/base/tag";
import {
    parseDelimitedValues,
    useCreateSmartQuestionItemMutation,
    useCreateSmartQuestionItemsBatchMutation,
    useDocumentChunksQuery,
    useQuestionItemQuery,
    useQuestionSetItemsQuery,
    useDeleteQuestionItemMutation,
    useLlmHealth,
    useRetryCompleteQuestionItemMutation,
    useRestartPracticeMutation,
    useUpdateQuestionItemMutation,
} from "@/lib/api/hooks";
import type { QuestionItem, QuestionItemDraft } from "@/lib/api/types";
import { cn } from "@/lib/cn";

const MODULE_OPTIONS = [
    "JavaSE", "OOP", "JVM", "IO", "JUC", "JCF", "MCP", "SKILL", "AGENT", "Harness",
    "SpringAI", "LangChain4J", "SpringFramework", "SpringMVC", "SpringBoot", "SpringCloud",
    "MyBatis", "MySQL", "PostgreSQL", "Redis", "MQ", "Linux", "Docker", "Maven", "Git",
    "Zookeeper", "Elasticsearch", "K8s", "Grafana", "分布式", "高并发", "微服务", "设计模式",
    "数据结构与算法", "计算机网络", "操作系统", "测试", "运维", "安全",
] as const;

const BATCH_POLL_LIMIT = 30;
const MAX_BATCH_QUESTIONS = 20;

const emptyItemDraft: QuestionItemDraft = {
    question: "",
    knowledgeNote: "",
    answer: "",
    moduleTag: "",
    difficulty: "MEDIUM",
    keywords: "",
    hint: "",
    sourceReliable: true,
    sourceChunkIdsJson: "",
};

function toQuestionItemDraft(qaSetEntry: QuestionItem): QuestionItemDraft {
    return {
        question: qaSetEntry.question,
        knowledgeNote: qaSetEntry.knowledgeNote,
        answer: qaSetEntry.answer,
        moduleTag: qaSetEntry.moduleTag,
        difficulty: qaSetEntry.difficulty || "MEDIUM",
        keywords: qaSetEntry.keywords || "",
        hint: qaSetEntry.hint || "",
        sourceReliable: qaSetEntry.sourceReliable,
        sourceChunkIdsJson: qaSetEntry.sourceChunkIdsJson || "",
    };
}

function sanitizeBatchQuestionDrafts(drafts: { question: string; answer: string }[]) {
    const filtered = drafts.filter((item) => item.question.trim());
    if (filtered.length === 0) {
        return [{ question: "", answer: "" }];
    }
    return filtered;
}

export function QuestionPage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const qaSetId = searchParams.get("qaSetId") || "";
    const itemIdParam = searchParams.get("itemId") || "";

    const selectedSetItemsQuery = useQuestionSetItemsQuery(qaSetId);
    const { showErrorDialog } = useGlobalErrorDialog();
    const refetchSelectedSetItems = selectedSetItemsQuery.refetch;
    const updateQuestionItemMutation = useUpdateQuestionItemMutation();
    const createSmartQuestionItemMutation = useCreateSmartQuestionItemMutation();
    const createSmartQuestionItemsBatchMutation = useCreateSmartQuestionItemsBatchMutation();
    const retryCompleteQuestionItemMutation = useRetryCompleteQuestionItemMutation();
    const deleteQuestionItemMutation = useDeleteQuestionItemMutation();
    const llmHealthQuery = useLlmHealth();
    const startSelectedPracticeMutation = useRestartPracticeMutation();

    const itemList = selectedSetItemsQuery.data ?? [];
    const fallbackItemId = itemList[0]?.id ?? "";
    const hasValidItemId = itemList.some((qaSetEntry) => qaSetEntry.id === itemIdParam);
    const activeItemId = hasValidItemId ? itemIdParam : fallbackItemId;

    const activeItemQuery = useQuestionItemQuery(activeItemId);
    const activeItem = activeItemQuery.data ?? null;

    const [itemDraft, setItemDraft] = useState<QuestionItemDraft>(emptyItemDraft);
    const [editDialogOpen, setEditDialogOpen] = useState(false);
    const [deleteItemDialogOpen, setDeleteItemDialogOpen] = useState(false);
    const [completeDialogOpen, setCompleteDialogOpen] = useState(false);
    const [completeQuestionDraft, setCompleteQuestionDraft] = useState("");
    const [completeAnswerDraft, setCompleteAnswerDraft] = useState("");
    const [createDialogOpen, setCreateDialogOpen] = useState(false);
    const [practiceDialogOpen, setPracticeDialogOpen] = useState(false);
    const [createMode, setCreateMode] = useState<"single" | "batch">("single");
    const [smartQuestionDraft, setSmartQuestionDraft] = useState("");
    const [smartAnswerDraft, setSmartAnswerDraft] = useState("");
    const [batchQuestionDrafts, setBatchQuestionDrafts] = useState<{ question: string; answer: string }[]>(
        () => [{ question: "", answer: "" }],
    );
    const [expandedAnswerIndex, setExpandedAnswerIndex] = useState<number | null>(null);
    const [batchTrackingIds, setBatchTrackingIds] = useState<string[]>([]);
    const [batchPollCount, setBatchPollCount] = useState(0);
    const [selectedModuleDraft, setSelectedModuleDraft] = useState<string[]>([]);
    const [selectedKeywordsDraft, setSelectedKeywordsDraft] = useState<string[]>([]);
    const [keywordInput, setKeywordInput] = useState("");
    const [selectedEvidenceChunkId, setSelectedEvidenceChunkId] = useState("");
    const [draggingBatchIndex, setDraggingBatchIndex] = useState<number | null>(null);
    const [selectedPracticeItemIds, setSelectedPracticeItemIds] = useState<string[]>([]);
    const [practiceMode, setPracticeMode] = useState<"SEQUENTIAL" | "RANDOM">("SEQUENTIAL");
    const [practiceFeedbackMode, setPracticeFeedbackMode] = useState<"ITEM_BY_ITEM" | "AFTER_ALL">("ITEM_BY_ITEM");

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
    const batchQuestionList = useMemo(
        () => batchQuestionDrafts.filter((item) => item.question.trim()),
        [batchQuestionDrafts],
    );
    const trackedBatchItems = useMemo(
        () => itemList.filter((item) => batchTrackingIds.includes(item.id)),
        [batchTrackingIds, itemList],
    );
    const trackedBatchProcessingCount = trackedBatchItems.filter((item) => item.completeStatus === "PROCESSING").length;

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
        setCompleteDialogOpen(false);
        setCompleteQuestionDraft("");
        setCompleteAnswerDraft("");
        setSelectedEvidenceChunkId("");
        setSelectedPracticeItemIds([]);
    }, [activeItemId]);

    useEffect(() => {
        if (activeItem) {
            setItemDraft(toQuestionItemDraft(activeItem));
        } else if (!activeItemId) {
            setItemDraft(emptyItemDraft);
        }
    }, [activeItem, activeItemId]);

    useEffect(() => {
        if (activeItem?.completeStatus !== "PROCESSING") {
            return;
        }
        const timer = window.setInterval(() => {
            activeItemQuery.refetch();
        }, 2000);
        return () => window.clearInterval(timer);
    }, [activeItem?.completeStatus, activeItemQuery]);

    useEffect(() => {
        if (!batchTrackingIds.length) {
            return;
        }
        if (batchPollCount >= BATCH_POLL_LIMIT || (trackedBatchItems.length > 0 && trackedBatchProcessingCount === 0)) {
            setBatchTrackingIds([]);
            setBatchPollCount(0);
            return;
        }
        const timer = window.setTimeout(() => {
            setBatchPollCount((current) => current + 1);
            refetchSelectedSetItems();
        }, 2000);
        return () => window.clearTimeout(timer);
    }, [batchPollCount, batchTrackingIds, refetchSelectedSetItems, trackedBatchItems.length, trackedBatchProcessingCount]);

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

    const openCompleteDialog = () => {
        if (!activeItem) return;
        setCompleteQuestionDraft(activeItem.question);
        setCompleteAnswerDraft(activeItem.answer || "");
        setCompleteDialogOpen(true);
    };

    const closeCompleteDialog = () => {
        if (retryCompleteQuestionItemMutation.isPending) return;
        setCompleteQuestionDraft("");
        setCompleteAnswerDraft("");
        setCompleteDialogOpen(false);
    };

    const handleDeleteItem = async () => {
        if (!activeItem) return;
        const deletedQaSetId = activeItem.qaSetId;
        const deletedId = activeItem.id;
        await deleteQuestionItemMutation.mutateAsync({
            qaSetId: deletedQaSetId,
            questionItemId: deletedId,
        });
        emitDasiBubble("题目已删除");
        setDeleteItemDialogOpen(false);
        const remaining = itemList.filter((item) => item.id !== deletedId);
        navigate(remaining[0]
            ? `/repository/question?qaSetId=${deletedQaSetId}&itemId=${remaining[0].id}`
            : `/repository/qa-set?qaSetId=${deletedQaSetId}`,
            { replace: true });
    };

    const openPracticeDialog = () => {
        if (!itemList.length) return;
        setSelectedPracticeItemIds([]);
        setPracticeDialogOpen(true);
    };

    const closePracticeDialog = () => {
        if (startSelectedPracticeMutation.isPending) return;
        setPracticeDialogOpen(false);
        setSelectedPracticeItemIds([]);
    };

    const closeCreateDialog = () => {
        if (createSmartQuestionItemMutation.isPending || createSmartQuestionItemsBatchMutation.isPending) return;
        setSmartQuestionDraft("");
        setBatchQuestionDrafts([{ question: "", answer: "" }]);
        setCreateMode("single");
        setCreateDialogOpen(false);
    };

    const createSmartItem = async () => {
        const question = smartQuestionDraft.trim();
        if (!question) return;
        const answer = smartAnswerDraft.trim();
        const qaSetEntry = await createSmartQuestionItemMutation.mutateAsync({ qaSetId, question, answer: answer || undefined });
        emitDasiBubble("✅ 题目已创建，Dasi 正在补全内容，稍后刷新查看。");
        setSmartQuestionDraft("");
        setSmartAnswerDraft("");
        setCreateDialogOpen(false);
        navigate(`/repository/question?qaSetId=${qaSetId}&itemId=${qaSetEntry.id}`, { replace: true });
    };

    const createBatchItems = async () => {
        if (!batchQuestionList.length || batchQuestionList.length > MAX_BATCH_QUESTIONS) return;
        const items = batchQuestionList.map((item) => ({
            question: item.question.trim(),
            answer: item.answer.trim() || undefined,
        }));
        const qaSetEntries = await createSmartQuestionItemsBatchMutation.mutateAsync({ qaSetId, items });
        emitDasiBubble("✅ 题目已批量创建，Dasi 正在后台补全内容，稍后刷新查看～");
        setBatchQuestionDrafts([{ question: "", answer: "" }]);
        setCreateDialogOpen(false);
        setCreateMode("single");
        setBatchTrackingIds(qaSetEntries.map((item) => item.id));
        setBatchPollCount(0);
        if (qaSetEntries[0]) {
            navigate(`/repository/question?qaSetId=${qaSetId}&itemId=${qaSetEntries[0].id}`, { replace: true });
        }
    };

    const retryCompleteItem = async () => {
        if (!activeItemId) return;
        const question = completeQuestionDraft.trim();
        if (!question) return;
        await retryCompleteQuestionItemMutation.mutateAsync({
            id: activeItemId,
            question,
            answer: completeAnswerDraft.trim() || undefined,
        });
        setCompleteDialogOpen(false);
        setCompleteQuestionDraft("");
        setCompleteAnswerDraft("");
        await activeItemQuery.refetch();
        await refetchSelectedSetItems();
    };

    const togglePracticeItemSelection = (itemId: string) => {
        setSelectedPracticeItemIds((current) => (
            current.includes(itemId)
                ? current.filter((id) => id !== itemId)
                : [...current, itemId]
        ));
    };

    const toggleSelectAllPracticeItems = () => {
        if (!itemList.length) return;
        if (selectedPracticeItemIds.length === itemList.length) {
            setSelectedPracticeItemIds([]);
        } else {
            setSelectedPracticeItemIds(itemList.map((qaSetEntry) => qaSetEntry.id));
        }
    };

    const allPracticeItemsSelected = itemList.length > 0 && selectedPracticeItemIds.length === itemList.length;

    const startPracticeWithItems = async (itemIds: string[]) => {
        const normalized = itemIds.filter(Boolean);
        if (!qaSetId || !normalized.length) {
            return;
        }
        const detail = await startSelectedPracticeMutation.mutateAsync({
            qaSetId,
            mode: practiceMode,
            feedbackMode: practiceFeedbackMode,
            itemIds: normalized,
        });
        setPracticeDialogOpen(false);
        setSelectedPracticeItemIds([]);
        navigate(`/practice/${detail.session.id}`);
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
        emitDasiBubble("✅ 题目已保存，继续完善其他题目吧。");
        setEditDialogOpen(false);
    };

    const updateBatchQuestionDraft = (index: number, question: string) => {
        setBatchQuestionDrafts((current) => current.map((item, i) => (
            i === index ? { ...item, question } : item
        )));
    };

    const updateBatchAnswerDraft = (index: number, answer: string) => {
        setBatchQuestionDrafts((current) => current.map((item, i) => (
            i === index ? { ...item, answer } : item
        )));
    };

    const appendBatchQuestionDraft = () => {
        setBatchQuestionDrafts((current) => {
            if (current.length >= MAX_BATCH_QUESTIONS) {
                return current;
            }
            return [...current, { question: "", answer: "" }];
        });
    };

    const removeBatchQuestionDraft = (index: number) => {
        setBatchQuestionDrafts((current) => {
            if (current.length === 1) {
                return [{ question: "", answer: "" }];
            }
            return sanitizeBatchQuestionDrafts(current.filter((_, i) => i !== index));
        });
    };

    const moveBatchQuestionDraft = (fromIndex: number, toIndex: number) => {
        if (fromIndex === toIndex) {
            return;
        }
        setBatchQuestionDrafts((current) => {
            if (fromIndex < 0 || fromIndex >= current.length || toIndex < 0 || toIndex >= current.length) {
                return current;
            }
            const next = [...current];
            const [moved] = next.splice(fromIndex, 1);
            next.splice(toIndex, 0, moved);
            return next;
        });
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
                                    onClick={async () => {
                                        const result = await llmHealthQuery.refetch();
                                        if (result.isError) {
                                            showErrorDialog({
                                                title: "LLM 接入未配置",
                                                message: "AI 补全功能需要配置 LLM，请先在个人设置中填写 Base URL、API Key 和 Model Name。",
                                            });
                                            return;
                                        }
                                        setCreateDialogOpen(true);
                                    }}
                                >
                                    新增题目
                                </button>
                            </div>
                            <div className="subtree tree">
                                {selectedSetItemsQuery.isLoading ? <div className="tree-qaSetEntry">加载中...</div> : null}
                                {selectedSetItemsQuery.isError ? (
                                    <div className="tree-qaSetEntry" style={{ color: "var(--ink)" }}>
                                        {selectedSetItemsQuery.error instanceof Error
                                            ? selectedSetItemsQuery.error.message
                                            : "题目列表加载失败"}
                                    </div>
                                ) : null}
                                {itemList.map((qaSetEntry) => (
                                    <button
                                        key={qaSetEntry.id}
                                        type="button"
                                        className={cn("tree-qaSetEntry", "tree-qaSetEntry--entry", qaSetEntry.id === activeItemId && "tree-qaSetEntry--active")}
                                        onClick={() => handleSelectItem(qaSetEntry.id)}
                                    >
                                        <span className="tree-item__label">{qaSetEntry.question}</span>
                                    </button>
                                ))}
                                {!selectedSetItemsQuery.isLoading && !selectedSetItemsQuery.isError && !itemList.length ? (
                                    <div className="tree-qaSetEntry">暂无题目</div>
                                ) : null}
                                {batchTrackingIds.length ? (
                                    <div className="question-batch-status">
                                        本批补全中 {trackedBatchProcessingCount}/{batchTrackingIds.length}
                                    </div>
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
                                                    {activeItem.completeStatus === "PROCESSING" ? (
                                                        <p className="question-detail-status">智能补全中，完成后会自动刷新当前题目。</p>
                                                    ) : null}
                                                    {activeItem.completeStatus === "UNSOLVED" ? (
                                                        <div className="question-detail-status question-detail-status--warning">
                                                            <span>智能补全未完成，可以在右侧操作区重试或手动编辑。</span>
                                                        </div>
                                                    ) : null}
                                                </div>
                                            </div>
                                        </div>

                                        <section className="question-detail-section question-detail-section--card">
                                            <div className="question-detail-section__header">
                                                <h2>标准回答</h2>
                                            </div>
                                            <div className="question-detail-section__body question-detail-section__body--expanded">
                                                <h2>标准回答</h2>
                                                <p>{activeItem.answer || "暂无标准回答"}</p>
                                            </div>
                                        </section>

                                        <section className="question-detail-section question-detail-section--card">
                                            <div className="question-detail-section__header">
                                                <h2>知识点</h2>
                                            </div>
                                            <div className="question-detail-section__body question-detail-section__body--expanded">
                                                <h2>知识点</h2>
                                                <p>{activeItem.knowledgeNote || "暂无知识点"}</p>
                                            </div>
                                        </section>

                                        <section className="question-detail-section question-detail-section--card">
                                            <div className="question-detail-section__header">
                                                <h2>证据切片</h2>
                                            </div>
                                            <div className="question-evidence-panel__body question-evidence-panel__body--expanded">
                                                <h2>证据切片</h2>
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
                                                    {activeItem.isImported ? (
                                                        <div className="question-reliability-indicator question-reliability-indicator--info" aria-label="导入题集，暂无证据切片">
                                                            <Info size={14} />
                                                            <span className="question-reliability-indicator__tooltip">导入题集，暂无证据切片</span>
                                                        </div>
                                                    ) : !activeItem.sourceReliable ? (
                                                        <div className="question-reliability-indicator" aria-label="与资料不一致，请注意甄别">
                                                            <AlertTriangle size={14} />
                                                            <span className="question-reliability-indicator__tooltip">与资料不一致，请注意甄别</span>
                                                        </div>
                                                    ) : null}
                                                </div>
                                                <div className="question-info-card__body">
                                                    {activeItem.practiceTotalCount != null ? (
                                                        <div className="question-info-card__stats">
                                                            <div className="question-info-card__stat">
                                                                <span className="question-info-card__stat-label">作答次数</span>
                                                                <strong className="question-info-card__stat-value">{activeItem.practiceTotalCount}</strong>
                                                            </div>
                                                            <div className="question-info-card__stat">
                                                                <span className="question-info-card__stat-label">平均分</span>
                                                                <strong className="question-info-card__stat-value">{activeItem.practiceAverageScore}</strong>
                                                            </div>
                                                            <div className="question-info-card__stat">
                                                                <span className="question-info-card__stat-label">正确率</span>
                                                                <strong className="question-info-card__stat-value">{activeItem.practiceCorrectRate}%</strong>
                                                            </div>
                                                        </div>
                                                    ) : null}
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
                                                    {activeItem.hint ? (
                                                        <div className="question-info-card__item">
                                                            <span>提示</span>
                                                            <p>{activeItem.hint}</p>
                                                        </div>
                                                    ) : null}
                                                </div>
                                            </div>
                                            <div className="question-side-rail__divider" />
                                            <div className="question-side-rail__actions">
                                                <BaseButton
                                                    variant="primary"
                                                    type="button"
                                                    className="question-side-rail__action-btn"
                                                    disabled={startSelectedPracticeMutation.isPending || itemList.length === 0}
                                                    onClick={openPracticeDialog}
                                                >
                                                    {startSelectedPracticeMutation.isPending ? "启动中" : "开始练习"}
                                                </BaseButton>
                                                <BaseButton
                                                    variant="soft"
                                                    type="button"
                                                    className="question-side-rail__action-btn"
                                                    onClick={openEditDialog}
                                                >
                                                    编辑信息
                                                </BaseButton>
                                                <BaseButton
                                                    variant="soft"
                                                    type="button"
                                                    className="question-side-rail__action-btn"
                                                    disabled={retryCompleteQuestionItemMutation.isPending}
                                                    onClick={openCompleteDialog}
                                                >
                                                    {retryCompleteQuestionItemMutation.isPending ? "补全中" : "重新补全"}
                                                </BaseButton>
                                                <BaseButton
                                                    variant="soft"
                                                    type="button"
                                                    className="question-side-rail__action-btn"
                                                    onClick={() => setDeleteItemDialogOpen(true)}
                                                >
                                                    删除题目
                                                </BaseButton>
                                                <BaseButton
                                                    variant="soft"
                                                    type="button"
                                                    className="question-side-rail__action-btn"
                                                    onClick={() => navigate(`/repository/qa-set?qaSetId=${qaSetId}`)}
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
                                        <div className="qa-text">可以从左侧新增题目，系统会在后台补全标准回答和知识点。</div>
                                    </div>
                                ) : null}
                            </div>
                        </div>
                    </GlassCard>
                </div>
            </div>

            {createDialogOpen ? (
                <div className="doc-select-dialog" role="presentation" onClick={closeCreateDialog}>
                    <div className="question-create-dialog" role="dialog" aria-modal="true" aria-label="新增题目" onClick={(event) => event.stopPropagation()}>
                        <div className="doc-select-dialog__header">
                            <h3 className="doc-select-dialog__title">新增题目</h3>
                            <button type="button" className="doc-select-dialog__close" onClick={closeCreateDialog}>
                                <X size={16} />
                            </button>
                        </div>

                        <div className="question-create-dialog__mode-switch" role="tablist" aria-label="新增题目方式">
                            <button
                                type="button"
                                className={cn("question-create-dialog__mode", createMode === "single" && "question-create-dialog__mode--active")}
                                onClick={() => setCreateMode("single")}
                            >
                                单题
                            </button>
                            <button
                                type="button"
                                className={cn("question-create-dialog__mode", createMode === "batch" && "question-create-dialog__mode--active")}
                                onClick={() => setCreateMode("batch")}
                            >
                                批量
                            </button>
                        </div>

                        <div className="question-edit-dialog__body">
                            {createMode === "single" ? (
                                <>
                                    <Field label="问题">
                                        <TextArea
                                            className="question-edit-dialog__textarea question-create-dialog__textarea"
                                            value={smartQuestionDraft}
                                            onChange={(event) => setSmartQuestionDraft(event.target.value)}
                                            rows={5}
                                            placeholder="输入题目"
                                        />
                                    </Field>
                                    <Field label="标准答案（可选）">
                                        <TextArea
                                            className="question-edit-dialog__textarea question-create-dialog__textarea"
                                            value={smartAnswerDraft}
                                            onChange={(event) => setSmartAnswerDraft(event.target.value)}
                                            rows={3}
                                            placeholder="输入标准答案，留空则由 AI 自动生成"
                                        />
                                    </Field>
                                </>
                            ) : (
                                <Field label={`问题列表（${batchQuestionList.length}/${MAX_BATCH_QUESTIONS}）`}>
                                    <div className="question-create-dialog__batch-list">
                                        {batchQuestionDrafts.map((item, index) => (
                                            <div key={`batch-question-${index}`}>
                                                <div
                                                    className={cn(
                                                        "question-create-dialog__batch-row",
                                                        draggingBatchIndex === index && "question-create-dialog__batch-row--dragging",
                                                    )}
                                                    onDragOver={(event) => {
                                                        event.preventDefault();
                                                        if (draggingBatchIndex == null || draggingBatchIndex === index) {
                                                            return;
                                                        }
                                                        moveBatchQuestionDraft(draggingBatchIndex, index);
                                                        setDraggingBatchIndex(index);
                                                    }}
                                                    onDrop={(event) => {
                                                        event.preventDefault();
                                                        setDraggingBatchIndex(null);
                                                    }}
                                                >
                                                    <button
                                                        type="button"
                                                        className="question-create-dialog__order"
                                                        draggable
                                                        onDragStart={(event) => {
                                                            setDraggingBatchIndex(index);
                                                            event.dataTransfer.effectAllowed = "move";
                                                        }}
                                                        onDragEnd={() => setDraggingBatchIndex(null)}
                                                        aria-label={`拖动排序，第 ${index + 1} 题`}
                                                    >
                                                        {index + 1}
                                                    </button>
                                                    <TextInput
                                                        className="question-create-dialog__input"
                                                        value={item.question}
                                                        onChange={(event) => updateBatchQuestionDraft(index, event.target.value)}
                                                        placeholder={index === 0 ? "输入题目" : ""}
                                                    />
                                                    <button
                                                        type="button"
                                                        className="question-create-dialog__row-action"
                                                        onClick={() => setExpandedAnswerIndex(expandedAnswerIndex === index ? null : index)}
                                                        aria-label={`编辑第 ${index + 1} 题答案`}
                                                    >
                                                        <Pencil size={14} />
                                                    </button>
                                                    <button
                                                        type="button"
                                                        className="question-create-dialog__row-action"
                                                        onClick={() => removeBatchQuestionDraft(index)}
                                                        aria-label={`删除第 ${index + 1} 道题`}
                                                        disabled={batchQuestionDrafts.length === 1}
                                                    >
                                                        <Trash2 size={16} />
                                                    </button>
                                                </div>
                                                {expandedAnswerIndex === index ? (
                                                    <div style={{ marginTop: 6, marginBottom: 2 }}>
                                                        <TextArea
                                                            className="question-edit-dialog__textarea question-create-dialog__textarea"
                                                            value={item.answer}
                                                            onChange={(event) => updateBatchAnswerDraft(index, event.target.value)}
                                                            rows={2}
                                                            placeholder="标准答案（可选）"
                                                        />
                                                    </div>
                                                ) : null}
                                            </div>
                                        ))}
                                        <button
                                            type="button"
                                            className="question-create-dialog__append"
                                            onClick={appendBatchQuestionDraft}
                                            disabled={batchQuestionDrafts.length >= MAX_BATCH_QUESTIONS}
                                            aria-label="新增一行题目"
                                        >
                                            <Plus size={16} />
                                        </button>
                                    </div>
                                </Field>
                            )}
                        </div>

                        <div className="modal-card__footer">
                            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                                {createMode === "single" ? (
                                    <BaseButton
                                        variant="primary"
                                        type="button"
                                        disabled={createSmartQuestionItemMutation.isPending || !smartQuestionDraft.trim()}
                                        onClick={createSmartItem}
                                    >
                                        {createSmartQuestionItemMutation.isPending ? "创建中" : "创建"}
                                    </BaseButton>
                                ) : (
                                    <BaseButton
                                        variant="primary"
                                        type="button"
                                        disabled={
                                            createSmartQuestionItemsBatchMutation.isPending
                                            || batchQuestionList.length === 0
                                            || batchQuestionList.length > MAX_BATCH_QUESTIONS
                                        }
                                        onClick={createBatchItems}
                                    >
                                        {createSmartQuestionItemsBatchMutation.isPending ? "创建中" : "创建"}
                                    </BaseButton>
                                )}
                                <BaseButton variant="ghost" type="button" onClick={closeCreateDialog}>
                                    取消
                                </BaseButton>
                            </div>
                        </div>
                    </div>
                </div>
            ) : null}

            {practiceDialogOpen ? (
                <div className="doc-select-dialog" role="presentation" onClick={closePracticeDialog}>
                    <div className="question-practice-dialog" role="dialog" aria-modal="true" aria-label="选择练习题目" onClick={(event) => event.stopPropagation()}>
                        <div className="doc-select-dialog__header">
                            <div className="question-practice-dialog__title-row">
                                <h3 className="doc-select-dialog__title">选择练习题目</h3>
                                <label className="question-practice-dialog__select-all">
                                    <input
                                        type="checkbox"
                                        checked={allPracticeItemsSelected}
                                        onChange={toggleSelectAllPracticeItems}
                                    />
                                    <span className="question-practice-dialog__select-all-mark" aria-hidden="true" />
                                    <span>全选</span>
                                </label>
                            </div>
                            <button type="button" className="doc-select-dialog__close" onClick={closePracticeDialog}>
                                <X size={16} />
                            </button>
                        </div>
                        <div className="question-practice-dialog__body">
                            {itemList.map((qaSetEntry) => {
                                const selected = selectedPracticeItemIds.includes(qaSetEntry.id);
                                return (
                                    <button
                                        key={`practice-${qaSetEntry.id}`}
                                        type="button"
                                        className={cn("question-practice-dialog__item", selected && "question-practice-dialog__item--selected")}
                                        onClick={() => togglePracticeItemSelection(qaSetEntry.id)}
                                    >
                                        <span className="question-practice-dialog__check" aria-hidden="true">{selected ? "✓" : ""}</span>
                                        <span className="question-practice-dialog__content">
                                            <strong className="question-practice-dialog__title">{qaSetEntry.question}</strong>
                                            <span className="repository-qaSetEntry-card__meta question-practice-dialog__meta">
                                                {qaSetEntry.difficulty ? (
                                                    <Tag className={`qaSetEntry__difficulty-tag qaSetEntry__difficulty--${qaSetEntry.difficulty.toLowerCase()}`}>
                                                        {qaSetEntry.difficulty}
                                                    </Tag>
                                                ) : null}
                                                {parseDelimitedValues(qaSetEntry.moduleTag).map((moduleTag) => (
                                                    <Tag key={`${qaSetEntry.id}-${moduleTag}`}>{moduleTag}</Tag>
                                                ))}
                                            </span>
                                        </span>
                                    </button>
                                );
                            })}
                        </div>
                        <div className="question-practice-dialog__mode-row">
                            <div className="question-practice-dialog__mode-group">
                                <span className="question-practice-dialog__mode-label">练习模式</span>
                                <ChoiceButton selected={practiceMode === "SEQUENTIAL"} onClick={() => setPracticeMode("SEQUENTIAL")}>顺序</ChoiceButton>
                                <ChoiceButton selected={practiceMode === "RANDOM"} onClick={() => setPracticeMode("RANDOM")}>随机</ChoiceButton>
                            </div>
                            <div className="question-practice-dialog__mode-group">
                                <span className="question-practice-dialog__mode-label">反馈模式</span>
                                <ChoiceButton selected={practiceFeedbackMode === "ITEM_BY_ITEM"} onClick={() => setPracticeFeedbackMode("ITEM_BY_ITEM")}>逐题</ChoiceButton>
                                <ChoiceButton selected={practiceFeedbackMode === "AFTER_ALL"} onClick={() => setPracticeFeedbackMode("AFTER_ALL")}>整轮</ChoiceButton>
                            </div>
                        </div>
                        <div className="modal-card__footer">
                            <div className="question-practice-dialog__footer">
                                <span>已选 {selectedPracticeItemIds.length} 题</span>
                                <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                                    <BaseButton
                                        variant="primary"
                                        type="button"
                                        disabled={startSelectedPracticeMutation.isPending || selectedPracticeItemIds.length === 0}
                                        onClick={() => startPracticeWithItems(selectedPracticeItemIds)}
                                    >
                                        {startSelectedPracticeMutation.isPending ? "启动中" : "开始练习"}
                                    </BaseButton>
                                    <BaseButton variant="ghost" type="button" onClick={closePracticeDialog}>
                                        取消
                                    </BaseButton>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            ) : null}

            {completeDialogOpen ? (
                <div className="doc-select-dialog" role="presentation" onClick={closeCompleteDialog}>
                    <div className="question-recomplete-dialog" role="dialog" aria-modal="true" aria-label="重新补全" onClick={(event) => event.stopPropagation()}>
                        <div className="doc-select-dialog__header">
                            <h3 className="doc-select-dialog__title">重新补全</h3>
                            <button type="button" className="doc-select-dialog__close" onClick={closeCompleteDialog}>
                                <X size={16} />
                            </button>
                        </div>

                        <div className="question-edit-dialog__body">
                            <Field label="问题">
                                <TextArea
                                    className="question-edit-dialog__textarea"
                                    value={completeQuestionDraft}
                                    onChange={(event) => setCompleteQuestionDraft(event.target.value)}
                                    rows={4}
                                    autoFocus
                                />
                            </Field>
                            <Field label="标准答案（可选）">
                                <TextArea
                                    className="question-edit-dialog__textarea"
                                    value={completeAnswerDraft}
                                    onChange={(event) => setCompleteAnswerDraft(event.target.value)}
                                    rows={6}
                                    placeholder="填写后，AI 不会改写这段答案，只补充知识点、难度、模块和来源信息。留空则由 AI 自动生成标准答案。"
                                />
                            </Field>
                        </div>

                        <div className="modal-card__footer">
                            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                                <BaseButton
                                    variant="primary"
                                    type="button"
                                    disabled={retryCompleteQuestionItemMutation.isPending || !completeQuestionDraft.trim()}
                                    onClick={retryCompleteItem}
                                >
                                    {retryCompleteQuestionItemMutation.isPending ? "提交中" : "确认"}
                                </BaseButton>
                                <BaseButton variant="ghost" type="button" onClick={closeCompleteDialog}>
                                    取消
                                </BaseButton>
                            </div>
                        </div>
                    </div>
                </div>
            ) : null}

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
                                        <div key={moduleTag} className="tag-dialog__selected-qaSetEntry">
                                            <span>{moduleTag}</span>
                                            <button
                                                type="button"
                                                className="tag-dialog__selected-remove"
                                                aria-label={`移除模块 ${moduleTag}`}
                                                onClick={() => setSelectedModuleDraft((current) => current.filter((qaSetEntry) => qaSetEntry !== moduleTag))}
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
                                            className="tag-dialog__pool-qaSetEntry"
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
                                        <div key={keyword} className="tag-dialog__selected-qaSetEntry">
                                            <span>{keyword}</span>
                                            <button
                                                type="button"
                                                className="tag-dialog__selected-remove"
                                                aria-label={`移除关键字 ${keyword}`}
                                                onClick={() => setSelectedKeywordsDraft((current) => current.filter((qaSetEntry) => qaSetEntry !== keyword))}
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
                                        <div className="question-evidence-dialog__value">{selectedEvidenceChunk.headingPath || "暂无标题路径"}</div>
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

            <TypeToConfirmDialog
                open={deleteItemDialogOpen}
                title="删除题目"
                message="确定要删除该题目吗？相关练习记录和记忆证据将同步清除。"
                confirmText="我确认删除题目"
                confirmLabel="删除"
                loading={deleteQuestionItemMutation.isPending}
                onConfirm={handleDeleteItem}
                onCancel={() => setDeleteItemDialogOpen(false)}
            />
        </>
    );
}
