import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { ArrowLeft, ArrowUp, History, Loader, Paperclip, Plus, Settings, StopCircle, X } from "lucide-react";
import { Link, useLocation, useNavigate, useParams } from "react-router";

import { ConfirmDialog } from "@/components/base/confirm-dialog";
import { emitDasiBubble } from "@/components/dasi/DasiChatWidget";
import { TextArea } from "@/components/base/field";
import {
    apiKeys,
    useFinishedDocumentsQuery,
    useCreateTaskMutation,
    useAbortTaskMutation,
    useCreateQuestionSetStream,
    useTaskStatusQuery,
    useTaskMessagesQuery,
    useTaskListQuery,
    parseTaskMessagesToEvents,
} from "@/lib/api/hooks";
import type { DocumentRecord, SseEvent, TaskListItem } from "@/lib/api/types";
import { useGlobalErrorDialog } from "@/lib/error/ErrorDialogProvider";

const ACTIVE_TASK_KEY = "qa-agent.active-task-id";

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

function formatDocumentDisplayName(fileName: string) {
    const normalized = fileName.trim();
    if (!normalized) {
        return "";
    }
    return normalized.replace(/\.md$/i, "");
}

type TimelineNode = {
    stage: string;
    events: SseEvent[];
};

function buildTimelineNodes(events: SseEvent[]): TimelineNode[] {
    const nodes: TimelineNode[] = [];
    for (const event of events) {
        const last = nodes[nodes.length - 1];
        if (last && last.stage === event.stage) {
            last.events.push(event);
        } else {
            nodes.push({
                stage: event.stage,
                events: [event],
            });
        }
    }
    return nodes;
}

export function CreatePage() {
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const location = useLocation();
    const { taskId: urlTaskId } = useParams();
    const documentsQuery = useFinishedDocumentsQuery();
    const createTask = useCreateTaskMutation();
    const createStream = useCreateQuestionSetStream();
    const abortTask = useAbortTaskMutation();
    const [selectedDocumentIds, setSelectedDocumentIds] = useState<string[]>([]);
    const [dialogOpen, setDialogOpen] = useState(false);
    const [historyOpen, setHistoryOpen] = useState(false);
    const [settingsOpen, setSettingsOpen] = useState(false);
    const [qaSetTitle, setQaSetTitle] = useState("未命名问答集");
    const [requestedCount, setRequestedCount] = useState(20);
    const [countDraft, setCountDraft] = useState("20");
    const [jobDescription, setJobDescription] = useState("");
    const submittingRef = useRef(false);
    const { showErrorDialog } = useGlobalErrorDialog();

    const [streamState, setStreamState] = useState<"idle" | "streaming" | "interrupted">(() => urlTaskId ? "streaming" : "idle");
    const [abortConfirmOpen, setAbortConfirmOpen] = useState(false);
    const isStreaming = streamState === "streaming";
    const isInterrupted = streamState === "interrupted";
    const interruptedRef = useRef(false);
    const currentTaskIdRef = useRef<string>("");
    const [sseEvents, setSseEvents] = useState<SseEvent[]>([]);
    const [streamError, setStreamError] = useState("");
    const [snapshot, setSnapshot] = useState<{
        userPrompt: string;
        docNames: string[];
    } | null>(null);
    const scrollRef = useRef<HTMLDivElement>(null);

    // Manual recovery via history dialog (also used when entering via /create/:taskId)
    const [recoveryTaskId, setRecoveryTaskId] = useState<string | null>(() => urlTaskId ?? null);
    const [recoveryTrigger, setRecoveryTrigger] = useState(() => urlTaskId ? 1 : 0);
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
                || /完成|COMPLETED|失败|FAILED/i.test(events[events.length - 1].stage)) {
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
            setStreamError(taskStatusQuery.error instanceof Error ? taskStatusQuery.error.message : "获取任务状态失败");
            setRecoveryTrigger(0);
        }
        if (taskMessagesQuery.isError) {
            setStreamError(taskMessagesQuery.error instanceof Error ? taskMessagesQuery.error.message : "获取任务消息失败");
            setRecoveryTrigger(0);
        }
    }, [taskStatusQuery.isError, taskMessagesQuery.isError, recoveryTrigger]);

    // When entering /create/:taskId with router state, start SSE stream
    const streamInitiatedFor = useRef<string | undefined>(undefined);
    useEffect(() => {
        if (!urlTaskId || streamInitiatedFor.current === urlTaskId) return;
        const formState = location.state as {
            title: string;
            userPrompt: string;
            documentIds: string[];
            requestedCount: number;
            jobDescription: string;
            docNames: string[];
        } | null;
        if (!formState) return; // No router state — recovery polling handles it
        streamInitiatedFor.current = urlTaskId;
        currentTaskIdRef.current = urlTaskId;
        interruptedRef.current = false;
        setSnapshot({ userPrompt: formState.userPrompt, docNames: formState.docNames });
        setSseEvents([]);
        setStreamError("");
        queryClient.invalidateQueries({ queryKey: apiKeys.taskList });
        createStream.mutateAsync({
            taskId: urlTaskId,
            title: formState.title,
            userPrompt: formState.userPrompt,
            documentIds: formState.documentIds,
            requestedQuestionCount: formState.requestedCount,
            jobDescription: formState.jobDescription,
            onEvent: (event: SseEvent) => {
                if (interruptedRef.current) return;
                if (!sessionStorage.getItem(ACTIVE_TASK_KEY)) {
                    sessionStorage.setItem(ACTIVE_TASK_KEY, event.taskId);
                }
                setSseEvents((prev) => [...prev, event]);
            },
        }).catch((err) => {
            if (interruptedRef.current) return;
            setStreamError(err instanceof Error ? err.message : "生成失败，请重试");
        });
    }, [urlTaskId]);

    // When urlTaskId changes (same-component route transition):
    // - appears: trigger recovery polling
    // - disappears (back to /create): reset to idle form
    useEffect(() => {
        if (urlTaskId) {
            setStreamState("streaming");
            setSseEvents([]);
            setStreamError("");
            setRecoveryTaskId(urlTaskId);
            setRecoveryTrigger((n) => n + 1);
        } else {
            setStreamState("idle");
            interruptedRef.current = false;
            setSseEvents([]);
            setStreamError("");
            setSnapshot(null);
            setRecoveryTaskId(null);
            setRecoveryTrigger(0);
        }
    }, [urlTaskId]);

    const form = useForm({
        defaultValues: {
            userPrompt: "",
        },
    });

    const uploadedDocuments = documentsQuery.data ?? [];
    const selectedDocuments = useMemo(
        () => uploadedDocuments.filter((qaSetEntry) => selectedDocumentIds.includes(qaSetEntry.id)),
        [selectedDocumentIds, uploadedDocuments],
    );

    const hasDocuments = selectedDocuments.length > 0;
    const taskTerminal = taskStatusQuery.data?.status
        ? !["PROCESSING", "PENDING"].includes(taskStatusQuery.data.status)
        : false;
    const isCompleted = taskTerminal
        || (sseEvents.length > 0 && (
            sseEvents[sseEvents.length - 1].isCompleted
            || /完成|COMPLETED|失败|FAILED/i.test(sseEvents[sseEvents.length - 1].stage)
        ));

    // Auto-scroll when new events arrive
    useEffect(() => {
        if (scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
    }, [sseEvents]);

    // Clear active task on completion
    const completionBubbleFired = useRef(false);
    useEffect(() => {
        if (isCompleted && !completionBubbleFired.current) {
            completionBubbleFired.current = true;
            sessionStorage.removeItem(ACTIVE_TASK_KEY);
            setStreamState("idle");
            emitDasiBubble("🎉 问答集生成完成！点击查看结果吧～");
        }
    }, [isCompleted]);

    // Switch to idle when stream errors out
    useEffect(() => {
        if (streamError) {
            setStreamState("idle");
        }
    }, [streamError]);

    const handleDialogToggle = (id: string) => {
        setSelectedDocumentIds((current) => {
            if (current.includes(id)) {
                return current.filter((qaSetEntry) => qaSetEntry !== id);
            }
            return [...current, id];
        });
    };

    const openDialog = useCallback(() => setDialogOpen(true), []);
    const closeDialog = useCallback(() => setDialogOpen(false), []);

    const handleSelectHistoryTask = (task: TaskListItem) => {
        setHistoryOpen(false);
        navigate(`/create/${task.taskId}`);
    };

    const handleSubmit = form.handleSubmit(async (values) => {
        if (submittingRef.current) return;
        if (selectedDocumentIds.length === 0) {
            showErrorDialog({
                title: "资料未选择",
                message: "请先添加本次要使用的资料。",
            });
            return;
        }

        const docNames = selectedDocuments.map((d) => d.fileName);
        const documentIds = [...selectedDocumentIds];

        submittingRef.current = true;
        try {
            const { taskId } = await createTask.mutateAsync({
                title: qaSetTitle,
                userPrompt: values.userPrompt,
                documentIds,
                requestedQuestionCount: requestedCount,
                jobDescription: jobDescription || undefined,
            });

            setSelectedDocumentIds([]);
            form.reset({ userPrompt: "" });

            navigate(`/create/${taskId}`, {
                replace: true,
                state: {
                    title: qaSetTitle,
                    userPrompt: values.userPrompt,
                    documentIds,
                    requestedCount,
                    jobDescription,
                    docNames,
                },
            });
        } catch {
            // error handled by mutation
        } finally {
            submittingRef.current = false;
        }
    });

    const handleAbort = async () => {
        const taskId = currentTaskIdRef.current;
        if (!taskId) return;
        interruptedRef.current = true;
        try {
            await abortTask.mutateAsync(taskId);
        } catch {
            // error handled by mutation
        }
        setAbortConfirmOpen(false);
        setStreamState("interrupted");
    };

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
                        {urlTaskId ? (
                            <div className="create-page__back-row">
                                <Link to="/create" className="btn btn--soft">
                                    <ArrowLeft size={16} />
                                    <span>返回</span>
                                </Link>
                            </div>
                        ) : null}
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
                                            key={`${node.stage}-${nodeIdx}`}
                                            className={`sse-timeline__node${isActive ? " sse-timeline__node--active" : ""}`}
                                        >
                                            <div className="sse-timeline__phase-header">
                                                <span className="sse-timeline__phase-label">
                                                    {node.stage}
                                                </span>
                                                <span className="sse-timeline__phase-tags">
                                                    <span className="sse-timeline__phase-tag">
                                                        {formatTime(node.events[0].timestamp)}
                                                    </span>
                                                    {(() => {
                                                        const total = node.events.reduce((sum, e) => sum + e.currentTokens, 0);
                                                        return total > 0 ? (
                                                            <span className="sse-timeline__phase-tag">
                                                                {total} tokens
                                                            </span>
                                                        ) : null;
                                                    })()}
                                                </span>
                                            </div>

                                            {node.events.map((event, i) => (
                                                <div key={i} className="sse-timeline__message fade-in">
                                                    <span className="sse-timeline__message-text">
                                                        {event.message?.trim() || "暂无消息"}
                                                    </span>
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

                        {/* Interrupted state */}
                        {isInterrupted ? (
                            <div className="status-card status-card--interrupted fade-in" style={{ maxWidth: 720, width: "100%", margin: "0 auto" }}>
                                <strong>生成任务已中断</strong>
                                <div className="qa-text">本轮生成已停止，可重新发起新任务。</div>
                            </div>
                        ) : null}

                        {/* Error state */}
                        {!isInterrupted && streamError ? (
                            <div className="status-card fade-in" style={{ maxWidth: 720, width: "100%", margin: "0 auto" }}>
                                <strong>生成失败</strong>
                                <div className="qa-text">{streamError}</div>
                            </div>
                        ) : null}

                        {/* Completion */}
                        {(() => {
                            const grandTotal = sseEvents.length > 0 ? sseEvents[sseEvents.length - 1].totalTokens : 0;
                            return isCompleted ? (
                            <div className="sse-timeline fade-in">
                                <div className="sse-timeline__divider">
                                    <span>生成完成，总消耗 <span style={{ textTransform: "none" }}>{grandTotal} tokens</span></span>
                                </div>
                                <div className="sse-timeline__completed-link" style={{ marginTop: 16 }}>
                                    <Link to="/repository/qa-set" className="btn btn--soft">
                                        查看问答集
                                    </Link>
                                </div>
                            </div>
                            ) : null;
                        })()}
                    </div>
                )}

                {/* ── Input area (always visible) ── */}
                <div className="create-page__bottom">
                    {createStream.isError && streamState === "idle" ? (
                        <div className="status-card" style={{ marginBottom: 16, maxWidth: 720, width: "100%" }}>
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
                                placeholder={isStreaming ? "当前对话仅支持一次任务处理，返回首页可新建任务" : "输入你的需求..."}
                                disabled={isStreaming}
                                className="create-page__input"
                            />
                        </div>

                        <div className="create-page__actions">
                            <div className="create-page__actions-left">
                                <button type="button" className="create-page__action-btn create-page__action-btn--icon" disabled={isStreaming} onClick={() => setSettingsOpen(true)}>
                                    <Settings size={18} />
                                </button>
                                <button
                                    type="button"
                                    className="create-page__action-btn create-page__action-btn--icon"
                                    disabled={isStreaming}
                                    onClick={openDialog}
                                    aria-label="添加资料"
                                    title="添加资料"
                                >
                                    <Paperclip size={18} />
                                </button>
                                <button
                                    type="button"
                                    className="create-page__action-btn create-page__action-btn--icon"
                                    onClick={() => { setHistoryOpen(true); taskListQuery.refetch(); }}
                                >
                                    <History size={18} />
                                </button>
                            </div>
                            {isStreaming ? (
                                <button
                                    type="button"
                                    className="create-page__send-btn create-page__send-btn--abort"
                                    aria-label="中断生成"
                                    disabled={abortTask.isPending}
                                    onClick={() => setAbortConfirmOpen(true)}
                                >
                                    <StopCircle size={20} strokeWidth={2.5} />
                                </button>
                            ) : (
                                <button
                                    type="submit"
                                    className="create-page__send-btn"
                                    aria-label="发送"
                                    disabled={isStreaming || isInterrupted || createTask.isPending || createStream.isPending}
                                    style={{ opacity: (isStreaming || isInterrupted || createTask.isPending || createStream.isPending) ? 0.5 : undefined }}
                                >
                                    <ArrowUp size={20} strokeWidth={2.5} />
                                </button>
                            )}
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
                    title={qaSetTitle}
                    countDraft={countDraft}
                    jobDescription={jobDescription}
                    onChangeTitle={setQaSetTitle}
                    onChangeCountDraft={(v) => setCountDraft(v)}
                    onCommitCount={(n) => { setCountDraft(String(n)); setRequestedCount(n); }}
                    onChangeJobDesc={setJobDescription}
                    onClose={() => setSettingsOpen(false)}
                />
            ) : null}

            <ConfirmDialog
                open={abortConfirmOpen}
                title="中断生成任务"
                message="中断后当前生成进度将丢失，已生成的题目不会保存。确定要中断吗？"
                confirmLabel="中断"
                variant="danger"
                loading={abortTask.isPending}
                onConfirm={handleAbort}
                onCancel={() => setAbortConfirmOpen(false)}
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
                                    className="sse-history-qaSetEntry"
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
    title,
    countDraft,
    jobDescription,
    onChangeTitle,
    onChangeCountDraft,
    onCommitCount,
    onChangeJobDesc,
    onClose,
}: {
    title: string;
    countDraft: string;
    jobDescription: string;
    onChangeTitle: (v: string) => void;
    onChangeCountDraft: (v: string) => void;
    onCommitCount: (v: number) => void;
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
                        <span className="field__label">题目名称</span>
                        <input
                            className="input"
                            type="text"
                            maxLength={50}
                            value={title}
                            onChange={(e) => onChangeTitle(e.target.value)}
                        />
                    </label>
                    <label className="field">
                        <span className="field__label">题目数量</span>
                        <input
                            className="input"
                            inputMode="numeric"
                            value={countDraft}
                            onChange={(e) => onChangeCountDraft(e.target.value)}
                            onBlur={() => {
                                const n = parseInt(countDraft, 10);
                                if (isNaN(n) || n < 10) onCommitCount(10);
                                else if (n > 50) onCommitCount(50);
                                else onCommitCount(n);
                            }}
                        />
                    </label>
                    <label className="field">
                        <span className="field__label">岗位描述（可选）</span>
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
                                        <span className="doc-select-dialog__item-name">{formatDocumentDisplayName(doc.fileName)}</span>
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
