import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { ArrowUp, Loader, Plus, Settings, X } from "lucide-react";
import { Link } from "react-router";
import { TextArea } from "@/components/base/field";
import { useDocumentsQuery, useCreateQuestionSetStream } from "@/lib/api/hooks";
import type { DocumentRecord, SseEvent } from "@/lib/api/types";

const STORAGE_KEY = "create-page-draft";

function loadDraft(): { selectedIds: string[]; userPrompt: string } {
    try {
        return JSON.parse(localStorage.getItem(STORAGE_KEY) || "null") || { selectedIds: [], userPrompt: "" };
    } catch {
        return { selectedIds: [], userPrompt: "" };
    }
}

function saveDraft(selectedIds: string[], userPrompt: string) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ selectedIds, userPrompt }));
}

function clearDraft() {
    localStorage.removeItem(STORAGE_KEY);
}

function formatTime(timestamp: number) {
    const d = new Date(timestamp);
    const datePart = d.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" });
    const timePart = d.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
    return `${datePart} ${timePart}`;
}

type TimelineNode = {
    phase: string;
    events: SseEvent[];
};

function buildTimelineNodes(events: SseEvent[]): TimelineNode[] {
    const nodes: TimelineNode[] = [];
    for (const event of events) {
        const last = nodes[nodes.length - 1];
        if (last && last.phase === event.phase) {
            last.events.push(event);
        } else {
            nodes.push({
                phase: event.phase,
                events: [event],
            });
        }
    }
    return nodes;
}

export function CreatePage() {
    const [draft] = useState(loadDraft);
    const documentsQuery = useDocumentsQuery();
    const createStream = useCreateQuestionSetStream();
    const [selectedDocumentIds, setSelectedDocumentIds] = useState<string[]>(draft.selectedIds);
    const [dialogOpen, setDialogOpen] = useState(false);

    const [streamState, setStreamState] = useState<"idle" | "streaming">("idle");
    const [sseEvents, setSseEvents] = useState<SseEvent[]>([]);
    const [streamError, setStreamError] = useState("");
    const [snapshot, setSnapshot] = useState<{
        userPrompt: string;
        docNames: string[];
    } | null>(null);
    const scrollRef = useRef<HTMLDivElement>(null);

    const form = useForm({
        defaultValues: {
            userPrompt: draft.userPrompt,
        },
    });

    const uploadedDocuments = documentsQuery.data ?? [];
    const selectedDocuments = useMemo(
        () => uploadedDocuments.filter((item) => selectedDocumentIds.includes(item.id)),
        [selectedDocumentIds, uploadedDocuments],
    );

    const hasDocuments = selectedDocuments.length > 0;
    const isCompleted = sseEvents.length > 0 && sseEvents[sseEvents.length - 1].isCompleted;

    // Persist draft on changes
    useEffect(() => {
        const sub = form.watch((value) => {
            saveDraft(selectedDocumentIds, value.userPrompt || "");
        });
        return () => sub.unsubscribe();
    }, [selectedDocumentIds, form]);

    // Auto-scroll when new events arrive
    useEffect(() => {
        if (scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
    }, [sseEvents]);

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

    const handleSubmit = form.handleSubmit(async (values) => {
        if (selectedDocumentIds.length === 0) {
            alert("请先添加本次要使用的资料。");
            return;
        }

        const docNames = selectedDocuments.map((d) => d.fileName);
        setSnapshot({ userPrompt: values.userPrompt, docNames });
        setStreamState("streaming");
        setSseEvents([]);
        setStreamError("");
        clearDraft();

        try {
            await createStream.mutateAsync({
                title: "",
                userPrompt: values.userPrompt,
                documentIds: selectedDocumentIds,
                requestedQuestionCount: 10,
                onEvent: (event: SseEvent) => {
                    setSseEvents((prev) => [...prev, event]);
                },
            });
        } catch (err) {
            setStreamError(err instanceof Error ? err.message : "生成失败，请重试");
        }
    });

    const timelineNodes = buildTimelineNodes(sseEvents);
    const lastPhaseIsEmpty = timelineNodes.length > 0
        && timelineNodes[timelineNodes.length - 1].events.every((e) => !e.message.trim());

    return (
        <div className="page-frame">
            <div className="create-page">
                {streamState === "idle" ? (
                    /* ── Idle State ── */
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
                ) : (
                    /* ── Streaming State ── */
                    <div className="create-page__stream-area" ref={scrollRef}>
                        {/* User message bubble */}
                        {snapshot ? (
                            <>
                                <div className="sse-user-bubble fade-in">
                                    <div className="sse-user-bubble__content">
                                        {snapshot.userPrompt || "（无额外需求）"}
                                    </div>
                                </div>
                                {snapshot.docNames.length > 0 ? (
                                    <div className="sse-user-docs fade-in">
                                        {snapshot.docNames.map((name) => (
                                            <span key={name} className="sse-user-docs__tag">{name}</span>
                                        ))}
                                    </div>
                                ) : null}
                            </>
                        ) : null}

                        {/* SSE Timeline */}
                        {timelineNodes.length > 0 ? (
                            <div className="sse-timeline fade-in">
                                {timelineNodes.map((node, nodeIdx) => {
                                    const isLastNode = nodeIdx === timelineNodes.length - 1;
                                    const isActive = isLastNode && !isCompleted;
                                    return (
                                        <div
                                            key={`${node.phase}-${nodeIdx}`}
                                            className={`sse-timeline__node${isActive ? " sse-timeline__node--active" : ""}`}
                                        >
                                            {/* Phase header */}
                                            <div className="sse-timeline__phase-header">
                                                <span className="sse-timeline__phase-label">
                                                    {node.phase}
                                                </span>
                                                <span className="sse-timeline__phase-tags">
                                                    <span className="sse-timeline__phase-tag">
                                                        {formatTime(node.events[0].timestamp)}
                                                    </span>
                                                    {node.events[0].currentTokens > 0 ? (
                                                        <span className="sse-timeline__phase-tag">
                                                            {node.events[0].currentTokens} tokens
                                                        </span>
                                                    ) : null}
                                                </span>
                                            </div>

                                            {/* Messages */}
                                            {node.events.map((event, i) => (
                                                <div key={i} className="sse-timeline__message fade-in">
                                                    {event.message.trim() ? (
                                                        <span className="sse-timeline__message-text">
                                                            {event.message}
                                                        </span>
                                                    ) : (
                                                        <span className="sse-timeline__spinner">
                                                            <Loader size={14} className="sse-timeline__spinner-icon" />
                                                        </span>
                                                    )}
                                                </div>
                                            ))}

                                            {/* Inline spinner for active node with non-empty last message */}
                                            {isActive && !lastPhaseIsEmpty ? (
                                                <div className="sse-timeline__message">
                                                    <span className="sse-timeline__spinner">
                                                        <Loader size={14} className="sse-timeline__spinner-icon" />
                                                    </span>
                                                </div>
                                            ) : null}
                                        </div>
                                    );
                                })}
                            </div>
                        ) : null}

                        {/* Loading before first event */}
                        {sseEvents.length === 0 && !streamError ? (
                            <div className="sse-timeline fade-in">
                                <div className="sse-timeline__node sse-timeline__node--active">
                                    <div className="sse-timeline__message">
                                        <span className="sse-timeline__spinner">
                                            <Loader size={16} className="sse-timeline__spinner-icon" />
                                        </span>
                                        <span className="sse-timeline__message-text" style={{ color: "var(--ink-faint)" }}>
                                            正在创建生成任务...
                                        </span>
                                    </div>
                                </div>
                            </div>
                        ) : null}

                        {/* Error state */}
                        {streamError ? (
                            <div className="qa-feedback fade-in" style={{ maxWidth: 720, width: "100%", margin: "0 auto" }}>
                                <strong>生成失败</strong>
                                <div className="qa-text">{streamError}</div>
                            </div>
                        ) : null}

                        {/* Completion */}
                        {isCompleted ? (
                            <div className="sse-timeline fade-in">
                                <div className="sse-timeline__completed-link">
                                    <Link to="/repository/qa-set" className="btn btn--soft">
                                        查看问答集
                                    </Link>
                                </div>
                                <div className="sse-timeline__divider">
                                    <span>生成完成</span>
                                </div>
                            </div>
                        ) : null}
                    </div>
                )}

                {/* ── Input area (always visible) ── */}
                <div className="create-page__bottom">
                    {createStream.isError && streamState === "idle" ? (
                        <div className="qa-feedback" style={{ marginBottom: 16, maxWidth: 720, width: "100%" }}>
                            <strong>生成失败</strong>
                            <div className="qa-text">
                                {createStream.error instanceof Error
                                    ? createStream.error.message
                                    : "请稍后重试"}
                            </div>
                        </div>
                    ) : null}

                    <form onSubmit={handleSubmit} style={{ width: "100%", maxWidth: 720 }}>
                        <div className="create-page__input-area">
                            <TextArea
                                {...form.register("userPrompt")}
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
                            <button
                                type="submit"
                                className="create-page__send-btn"
                                aria-label="发送"
                                disabled={createStream.isPending}
                                style={{ opacity: createStream.isPending ? 0.5 : undefined }}
                            >
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
