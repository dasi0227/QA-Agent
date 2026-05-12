import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { ArrowUp, History, Loader, Plus, Settings, X } from "lucide-react";
import { Link } from "react-router";
import { TextArea } from "@/components/base/field";
import { ErrorDialog } from "@/components/base/error-dialog";
import {
    apiKeys,
    useDocumentsQuery,
    useCreateQuestionSetStream,
    useTaskStatusQuery,
    useTaskMessagesQuery,
    useTaskListQuery,
    parseTaskMessagesToEvents,
} from "@/lib/api/hooks";
import type { DocumentRecord, SseEvent, TaskListItem } from "@/lib/api/types";

const STORAGE_KEY = "create-page-draft";
const ACTIVE_TASK_KEY = "qa-agent.active-task-id";

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

function parseJsonArray(json: string): string[] {
    if (!json) return [];
    try {
        const parsed = JSON.parse(json);
        return Array.isArray(parsed) ? parsed : [];
    } catch {
        return [];
    }
}

function formatTime(timestamp: number) {
    const d = new Date(timestamp);
    const datePart = d.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" });
    const timePart = d.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
    return `${datePart} ${timePart}`;
}

function formatTaskTime(createdAt?: string) {
    if (!createdAt) return "";
    return createdAt.replace("T", " ").substring(0, 16);
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
    const queryClient = useQueryClient();
    const [draft] = useState(loadDraft);
    const documentsQuery = useDocumentsQuery();
    const createStream = useCreateQuestionSetStream();
    const [selectedDocumentIds, setSelectedDocumentIds] = useState<string[]>(draft.selectedIds);
    const [dialogOpen, setDialogOpen] = useState(false);
    const [historyOpen, setHistoryOpen] = useState(false);
    const [settingsOpen, setSettingsOpen] = useState(false);
    const [requestedCount, setRequestedCount] = useState(20);
    const [jobDescription, setJobDescription] = useState("");
    const [errorDialog, setErrorDialog] = useState<{ title: string; message: string } | null>(null);

    const [streamState, setStreamState] = useState<"idle" | "streaming">("idle");
    const [sseEvents, setSseEvents] = useState<SseEvent[]>([]);
    const [streamError, setStreamError] = useState("");
    const [snapshot, setSnapshot] = useState<{
        userPrompt: string;
        docNames: string[];
    } | null>(null);
    const scrollRef = useRef<HTMLDivElement>(null);

    // Manual recovery via history dialog
    const [recoveryTaskId, setRecoveryTaskId] = useState<string | null>(null);
    const [recoveryTrigger, setRecoveryTrigger] = useState(0);
    const taskStatusQuery = useTaskStatusQuery(recoveryTaskId ?? undefined, { poll: recoveryTrigger > 0 });
    const taskMessagesQuery = useTaskMessagesQuery(recoveryTaskId ?? undefined, { poll: recoveryTrigger > 0 });
    const taskListQuery = useTaskListQuery();

    // When recovery data loads, populate sseEvents and snapshot
    useEffect(() => {
        if (!recoveryTaskId || !taskMessagesQuery.data) return;
        const events = parseTaskMessagesToEvents(taskMessagesQuery.data);
        if (events.length > 0) {
            setSseEvents(events);
            if (events[events.length - 1].isCompleted
                || /完成|COMPLETED|失败|FAILED/i.test(events[events.length - 1].phase)) {
                setRecoveryTrigger(0);
            }
        }
    }, [recoveryTaskId, taskMessagesQuery.data, recoveryTrigger]);

    // Stop polling when task status is terminal
    useEffect(() => {
        const status = taskStatusQuery.data?.status;
        if (status && !["PROCESSING", "PENDING"].includes(status)) {
            setRecoveryTrigger(0);
        }
    }, [taskStatusQuery.data?.status]);

    useEffect(() => {
        if (!recoveryTaskId || !taskStatusQuery.data) return;
        const docNames = parseJsonArray(taskStatusQuery.data.documentNamesJson);
        setSnapshot({
            userPrompt: taskStatusQuery.data.userPrompt || "",
            docNames,
        });
    }, [recoveryTaskId, taskStatusQuery.data]);

    // Watch for recovery errors (only when actively recovering)
    useEffect(() => {
        if (!recoveryTrigger) return;
        if (taskStatusQuery.isError) {
            setErrorDialog({
                title: "查询失败",
                message: taskStatusQuery.error instanceof Error ? taskStatusQuery.error.message : "获取任务状态失败",
            });
        }
        if (taskMessagesQuery.isError) {
            setErrorDialog({
                title: "查询失败",
                message: taskMessagesQuery.error instanceof Error ? taskMessagesQuery.error.message : "获取任务消息失败",
            });
        }
    }, [taskStatusQuery.isError, taskMessagesQuery.isError, recoveryTrigger]);

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
    const taskTerminal = taskStatusQuery.data?.status
        ? !["PROCESSING", "PENDING"].includes(taskStatusQuery.data.status)
        : false;
    const isCompleted = taskTerminal
        || (sseEvents.length > 0 && (
            sseEvents[sseEvents.length - 1].isCompleted
            || /完成|COMPLETED|失败|FAILED/i.test(sseEvents[sseEvents.length - 1].phase)
        ));

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

    // Clear active task on completion
    useEffect(() => {
        if (isCompleted) {
            sessionStorage.removeItem(ACTIVE_TASK_KEY);
        }
    }, [isCompleted]);

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

    const handleSelectHistoryTask = (task: TaskListItem) => {
        setHistoryOpen(false);
        setStreamState("streaming");
        setSseEvents([]);
        setStreamError("");
        setRecoveryTaskId(task.taskId);
        setRecoveryTrigger((n) => n + 1);
        taskMessagesQuery.refetch();
    };

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
        setRecoveryTaskId(null);
        setRecoveryTrigger(0);
        setSelectedDocumentIds([]);
        form.reset({ userPrompt: "" });
        clearDraft();

        // Invalidate task list so new task appears in history
        queryClient.invalidateQueries({ queryKey: apiKeys.taskList });

        try {
            await createStream.mutateAsync({
                title: "",
                userPrompt: values.userPrompt,
                documentIds: selectedDocumentIds,
                requestedQuestionCount: requestedCount,
                jobDescription: jobDescription || undefined,
                onEvent: (event: SseEvent) => {
                    if (!sessionStorage.getItem(ACTIVE_TASK_KEY)) {
                        sessionStorage.setItem(ACTIVE_TASK_KEY, event.taskId);
                    }
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
    const taskList = taskListQuery.data ?? [];
    const activeTaskId = sessionStorage.getItem(ACTIVE_TASK_KEY);

    return (
        <div className="page-frame">
            <div className="create-page">
                {streamState === "idle" ? (
                    /* ── Idle State ── */
                    <div className="create-page__main">
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
                        ) : (
                            <>
                                <p className="create-page__empty">请添加笔记资料，并输入需求给 QA Agent</p>
                                <button type="button" className="create-page__add-doc-btn" onClick={openDialog}>
                                    <Plus size={18} />
                                    添加资料
                                </button>
                            </>
                        )}
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
                                            {recoveryTaskId ? "正在恢复生成进度..." : "正在创建生成任务..."}
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
                                <button type="button" className="create-page__action-btn create-page__action-btn--icon" onClick={() => setSettingsOpen(true)}>
                                    <Settings size={18} />
                                </button>
                                <button
                                    type="button"
                                    className="create-page__action-btn create-page__action-btn--icon"
                                    onClick={() => { setHistoryOpen(true); taskListQuery.refetch(); }}
                                >
                                    <History size={18} />
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

            {historyOpen ? (
                <HistoryDialog
                    tasks={taskList}
                    activeTaskId={activeTaskId}
                    onSelect={handleSelectHistoryTask}
                    onClose={() => setHistoryOpen(false)}
                />
            ) : null}

            {settingsOpen ? (
                <SettingsDialog
                    requestedCount={requestedCount}
                    jobDescription={jobDescription}
                    onChangeCount={setRequestedCount}
                    onChangeJobDesc={setJobDescription}
                    onClose={() => setSettingsOpen(false)}
                />
            ) : null}

            <ErrorDialog
                open={errorDialog !== null}
                title={errorDialog?.title || "错误"}
                message={errorDialog?.message || ""}
                onConfirm={() => setErrorDialog(null)}
            />
        </div>
    );
}

function HistoryDialog({
    tasks,
    activeTaskId,
    onSelect,
    onClose,
}: {
    tasks: TaskListItem[];
    activeTaskId: string | null;
    onSelect: (task: TaskListItem) => void;
    onClose: () => void;
}) {
    return (
        <div className="doc-select-dialog" onClick={onClose}>
            <div className="doc-select-dialog__card" onClick={(e) => e.stopPropagation()}>
                <div className="doc-select-dialog__header">
                    <h3 className="doc-select-dialog__title">历史任务</h3>
                    <button className="doc-select-dialog__close" onClick={onClose} aria-label="关闭">
                        <X size={18} />
                    </button>
                </div>

                <div className="doc-select-dialog__body" style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                    {tasks.length === 0 ? (
                        <p style={{ gridColumn: "1/-1", textAlign: "center", color: "var(--ink-faint)", fontSize: 14, margin: 0, padding: 24 }}>
                            暂无历史任务
                        </p>
                    ) : (
                        tasks.map((task) => {
                            const isActive = task.taskId === activeTaskId;
                            return (
                                <button
                                    key={task.taskId}
                                    type="button"
                                    className="sse-history-item"
                                    onClick={() => onSelect(task)}
                                >
                                    <div className="sse-history-item__main">
                                        <span className="sse-history-item__title">
                                            {task.title || "未命名任务"}
                                            {isActive ? (
                                                <span className="sse-history-item__badge">进行中</span>
                                            ) : null}
                                        </span>
                                        <span className="sse-history-item__id">
                                            {task.taskId.length > 12
                                                ? task.taskId.slice(0, 12) + "..."
                                                : task.taskId}
                                        </span>
                                    </div>
                                    <div className="sse-history-item__meta">
                                        <span>{formatTaskTime(task.createdAt)}</span>
                                        <span className="sse-history-item__status">{task.stage || task.status}</span>
                                    </div>
                                </button>
                            );
                        })
                    )}
                </div>
            </div>
        </div>
    );
}

function SettingsDialog({
    requestedCount,
    jobDescription,
    onChangeCount,
    onChangeJobDesc,
    onClose,
}: {
    requestedCount: number;
    jobDescription: string;
    onChangeCount: (v: number) => void;
    onChangeJobDesc: (v: string) => void;
    onClose: () => void;
}) {
    return (
        <div className="doc-select-dialog" onClick={onClose}>
            <div className="doc-select-dialog__card" onClick={(e) => e.stopPropagation()}>
                <div className="doc-select-dialog__header">
                    <h3 className="doc-select-dialog__title">生成设置</h3>
                    <button className="doc-select-dialog__close" onClick={onClose} aria-label="关闭">
                        <X size={18} />
                    </button>
                </div>
                <div className="doc-select-dialog__body" style={{ display: "flex", flexDirection: "column", gap: 18 }}>
                    <label className="field">
                        <span className="field__label">题目数量</span>
                        <span className="field__hint">单次生成 10–100 题</span>
                        <input
                            className="input"
                            type="number"
                            min={10}
                            max={100}
                            value={requestedCount}
                            onChange={(e) => onChangeCount(Number(e.target.value))}
                        />
                    </label>
                    <label className="field">
                        <span className="field__label">岗位描述</span>
                        <span className="field__hint">可选，用于辅助生成更贴合岗位的题目</span>
                        <textarea
                            className="textarea"
                            rows={4}
                            placeholder="例如：Java 后端开发，要求熟悉 Spring Boot、MySQL、Redis，有分布式系统经验..."
                            value={jobDescription}
                            onChange={(e) => onChangeJobDesc(e.target.value)}
                        />
                    </label>
                </div>
            </div>
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
