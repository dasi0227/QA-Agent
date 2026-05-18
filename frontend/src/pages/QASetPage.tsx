import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { Plus, X } from "lucide-react";
import { ConfirmDialog } from "@/components/base/confirm-dialog";
import { BaseButton, LinkButton } from "@/components/base/button";
import { GlassCard } from "@/components/base/card";
import { Tag } from "@/components/base/tag";
import {
    useDeleteQuestionSetMutation,
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

export function QASetPage() {
    const params = useParams();
    const navigate = useNavigate();
    const [editingSetMeta, setEditingSetMeta] = useState(false);
    const [setTitleDraft, setSetTitleDraft] = useState("");
    const [setDescriptionDraft, setSetDescriptionDraft] = useState("");
    const [deleteSetDialogOpen, setDeleteSetDialogOpen] = useState(false);
    const [tagDialogOpen, setTagDialogOpen] = useState(false);
    const [selectedTagsDraft, setSelectedTagsDraft] = useState<string[]>([]);

    const questionSetsQuery = useQuestionSetsQuery();

    const selectedSetId = params.id ?? questionSetsQuery.data?.[0]?.id ?? "";
    const selectedSetQuery = useQuestionSetQuery(selectedSetId);
    const selectedSetItemsQuery = useQuestionSetItemsQuery(selectedSetId);
    const deleteQuestionSetMutation = useDeleteQuestionSetMutation();
    const updateQuestionSetMutation = useUpdateQuestionSetMutation();

    const hasQuestionSets = (questionSetsQuery.data?.length ?? 0) > 0;
    const setErrorMessage = questionSetsQuery.error instanceof Error ? questionSetsQuery.error.message : "";

    useEffect(() => {
        if (!selectedSetQuery.data) {
            setSetTitleDraft("");
            setSetDescriptionDraft("");
            return;
        }
        setSetTitleDraft(selectedSetQuery.data.title);
        setSetDescriptionDraft(selectedSetQuery.data.description);
        setSelectedTagsDraft(parseModuleTags(selectedSetQuery.data.moduleTagsJson));
    }, [selectedSetQuery.data]);

    const selectedSetDescription = selectedSetQuery.data?.description?.trim();
    const selectedSetPracticeTotal = selectedSetQuery.data
        ? selectedSetQuery.data.questionCount * selectedSetQuery.data.practiceCount
        : 0;
    const itemList = selectedSetItemsQuery.data ?? [];
    const selectedTags = parseModuleTags(selectedSetQuery.data?.moduleTagsJson);
    const availableTags = useMemo(
        () => TAG_OPTIONS.filter((tag) => !selectedTagsDraft.includes(tag)),
        [selectedTagsDraft],
    );
    const firstItemId = itemList[0]?.id ?? "";

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
                            <LinkButton to="/create" variant="soft" className="sidebar__upload-btn">
                                新增题集
                            </LinkButton>
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
                                        to={`/repository/qa-set/${item.id}`}
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
                </aside>

                <GlassCard className="panel repository-main-panel" style={{ padding: 24, overflow: "hidden" }}>
                    <div className="fade-in" style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
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

                        {!selectedSetQuery.isLoading && !selectedSetQuery.isError && !selectedSetQuery.data ? (
                            <div className="qa-feedback">
                                <strong>暂无问答集可预览</strong>
                            </div>
                        ) : null}

                        {selectedSetQuery.data ? (
                            <>
                                <div className="repository-header">
                                    <div style={{ flex: 1, minWidth: 0 }}>
                                        <div
                                            style={{
                                                minHeight: 72,
                                                padding: editingSetMeta ? "14px 12px" : 0,
                                                borderRadius: 22,
                                                border: editingSetMeta ? "1px solid var(--line-strong)" : "1px solid transparent",
                                                background: editingSetMeta ? "rgba(255,255,255,0.4)" : "transparent",
                                            }}
                                        >
                                            {editingSetMeta ? (
                                                <div style={{ display: "flex", alignItems: "center", gap: 10, height: 44 }}>
                                                    <input
                                                        value={setTitleDraft}
                                                        onChange={(event) => {
                                                            if (event.target.value.length <= 50) setSetTitleDraft(event.target.value);
                                                        }}
                                                        placeholder="输入问答集标题"
                                                        maxLength={50}
                                                        style={{
                                                            flex: 1,
                                                            minWidth: 0,
                                                            height: 44,
                                                            border: 0,
                                                            outline: "none",
                                                            fontSize: 34,
                                                            fontWeight: 600,
                                                            letterSpacing: "-0.05em",
                                                            lineHeight: "44px",
                                                            fontFamily: "var(--font-serif)",
                                                            color: "var(--ink)",
                                                            background: "transparent",
                                                            padding: 0,
                                                        }}
                                                    />
                                                    <span style={{ flexShrink: 0, fontSize: 11, color: "var(--ink-faint)", fontFamily: "var(--font-sans)" }}>
                                                        {setTitleDraft.length}/50
                                                    </span>
                                                </div>
                                            ) : (
                                                <h1 className="hero-title" style={{ fontSize: 34, margin: 0 }}>
                                                    {selectedSetQuery.data.title}
                                                </h1>
                                            )}
                                        </div>
                                        <div
                                            style={{
                                                height: editingSetMeta ? 200 : undefined,
                                                minHeight: 64,
                                                marginTop: 12,
                                                padding: editingSetMeta ? "8px 12px 28px" : 0,
                                                borderRadius: 22,
                                                border: editingSetMeta ? "1px solid var(--line-strong)" : "1px solid transparent",
                                                background: editingSetMeta ? "rgba(255,255,255,0.4)" : "transparent",
                                            }}
                                        >
                                            {editingSetMeta ? (
                                                <div style={{ position: "relative", height: "100%" }}>
                                                    <textarea
                                                        value={setDescriptionDraft}
                                                        onChange={(event) => {
                                                            if (event.target.value.length <= 300) setSetDescriptionDraft(event.target.value);
                                                        }}
                                                        placeholder="输入问答集描述"
                                                        maxLength={300}
                                                        style={{
                                                            width: "100%",
                                                            height: "100%",
                                                            border: 0,
                                                            outline: "none",
                                                            fontSize: 15,
                                                            lineHeight: 1.75,
                                                            fontFamily: "var(--font-serif)",
                                                            color: "var(--ink-soft)",
                                                            background: "transparent",
                                                            padding: 0,
                                                            resize: "none",
                                                        }}
                                                    />
                                                    <span style={{ position: "absolute", right: 0, bottom: -22, fontSize: 11, color: "var(--ink-faint)", fontFamily: "var(--font-sans)" }}>
                                                        {setDescriptionDraft.length}/300
                                                    </span>
                                                </div>
                                            ) : (
                                                <p className="page-copy" style={{ margin: 0 }}>
                                                    {selectedSetDescription || "本问答集用于维护训练资产，支持逐题打磨问题、知识笔记和标准答案。"}
                                                </p>
                                            )}
                                        </div>
                                        <div className="repository-header__tags" style={{ marginTop: 12 }}>
                                            {selectedTags.map((tag) => (
                                                <Tag key={tag}>{tag}</Tag>
                                            ))}
                                            <button
                                                type="button"
                                                className="repository-tag-add"
                                                aria-label="管理标签"
                                                onClick={() => setTagDialogOpen(true)}
                                            >
                                                <Plus size={14} />
                                            </button>
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

                                <div style={{ marginTop: 24, marginBottom: 28, display: "flex", gap: 12, flexWrap: "wrap" }}>
                                    <LinkButton to={`/quiz?questionSetId=${selectedSetQuery.data.id}`} variant="primary">
                                        开始练习
                                    </LinkButton>
                                    <LinkButton
                                        to={firstItemId ? `/repository/question?qaSetId=${selectedSetQuery.data.id}&itemId=${firstItemId}` : "#"}
                                        variant="soft"
                                        className={cn("repository-detail-btn", !firstItemId && "repository-detail-btn--disabled")}
                                    >
                                        题目详情
                                    </LinkButton>
                                    {editingSetMeta ? (
                                        <>
                                            <BaseButton
                                                variant="ghost"
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
                                                {updateQuestionSetMutation.isPending ? "保存中" : "保存"}
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
                                        </>
                                    ) : (
                                        <BaseButton
                                            variant="soft"
                                            type="button"
                                            onClick={() => {
                                                setSetTitleDraft(selectedSetQuery.data.title);
                                                setSetDescriptionDraft(selectedSetQuery.data.description);
                                                setEditingSetMeta(true);
                                            }}
                                        >
                                            编辑信息
                                        </BaseButton>
                                    )}
                                    <BaseButton
                                        variant="outline"
                                        type="button"
                                        disabled={deleteQuestionSetMutation.isPending}
                                        onClick={() => setDeleteSetDialogOpen(true)}
                                    >
                                        {deleteQuestionSetMutation.isPending ? "删除中" : "删除问答集"}
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
                                                <div className="repository-item-list">
                                                    {selectedSetItemsQuery.data.map((item) => (
                                                        <Link
                                                            key={item.id}
                                                            to={`/repository/question?qaSetId=${selectedSetQuery.data.id}&itemId=${item.id}`}
                                                            className={cn("repository-item-card")}
                                                        >
                                                            <strong>{item.question}</strong>
                                                            {item.moduleTag?.trim() ? (
                                                                <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
                                                                    {item.moduleTag.split(",").map((tag) => tag.trim()).filter(Boolean).map((tag) => (
                                                                        <Tag key={tag}>{tag}</Tag>
                                                                    ))}
                                                                </div>
                                                            ) : (
                                                                <span>{item.moduleTag}</span>
                                                            )}
                                                            <small>{item.difficulty || "未标注难度"}</small>
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

            {tagDialogOpen ? (
                <div className="doc-select-dialog" role="presentation" onClick={() => setTagDialogOpen(false)}>
                    <div className="tag-dialog__card" role="dialog" aria-modal="true" aria-label="选择标签" onClick={(event) => event.stopPropagation()}>
                        <div className="doc-select-dialog__header">
                            <h3 className="doc-select-dialog__title">管理问答集标签</h3>
                            <button type="button" className="doc-select-dialog__close" onClick={() => setTagDialogOpen(false)}>
                                <X size={16} />
                            </button>
                        </div>
                        <div className="tag-dialog__body">
                            <section className="tag-dialog__section">
                                <div className="tag-dialog__section-head">
                                    <strong>已选标签</strong>
                                    <span>{selectedTagsDraft.length} 个</span>
                                </div>
                                <div className="tag-dialog__selected-list">
                                    {selectedTagsDraft.length ? selectedTagsDraft.map((tag) => (
                                        <div key={tag} className="tag-dialog__selected-item">
                                            <span>{tag}</span>
                                            <button
                                                type="button"
                                                className="tag-dialog__selected-remove"
                                                aria-label={`移除 ${tag}`}
                                                onClick={() => setSelectedTagsDraft((current) => current.filter((item) => item !== tag))}
                                            >
                                                <X size={12} />
                                            </button>
                                        </div>
                                    )) : (
                                        <div className="tag-dialog__empty">还没有选择标签</div>
                                    )}
                                </div>
                            </section>
                            <section className="tag-dialog__section">
                                <div className="tag-dialog__section-head">
                                    <strong>标签池</strong>
                                </div>
                                <div className="tag-dialog__pool">
                                    {availableTags.map((tag) => (
                                        <button
                                            key={tag}
                                            type="button"
                                            className="tag-dialog__pool-item"
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
                                    variant="soft"
                                    type="button"
                                    disabled={updateQuestionSetMutation.isPending}
                                    onClick={async () => {
                                        if (!selectedSetQuery.data) return;
                                        await updateQuestionSetMutation.mutateAsync({
                                            questionSetId: selectedSetQuery.data.id,
                                            title: selectedSetQuery.data.title,
                                            description: selectedSetQuery.data.description,
                                            moduleTagsJson: JSON.stringify(selectedTagsDraft),
                                        });
                                        setTagDialogOpen(false);
                                    }}
                                >
                                    {updateQuestionSetMutation.isPending ? "保存中" : "保存标签"}
                                </BaseButton>
                                <BaseButton
                                    variant="ghost"
                                    type="button"
                                    onClick={() => {
                                        setSelectedTagsDraft(selectedTags);
                                        setTagDialogOpen(false);
                                    }}
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
