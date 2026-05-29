import { useEffect, useRef, useState } from "react";
import { Clock, CornerUpLeft, Save } from "lucide-react";
import { useNavigate, useParams } from "react-router";
import { BaseButton } from "@/components/base/button";
import { ConfirmDialog } from "@/components/base/confirm-dialog";
import { emitDasiBubble } from "@/components/dasi/DasiChatWidget";
import { AssessmentGeneratingPanel } from "@/components/practice/AssessmentGeneratingPanel";
import { AnswerCard } from "@/components/practice/AnswerCard";
import { PracticeLayout } from "@/components/practice/PracticeLayout";
import { QuestionWorkspace } from "@/components/practice/QuestionWorkspace";
import { SiteFooter } from "@/components/layout/SiteFooter";
import {
    useAbandonPracticeMutation,
    useMarkPracticeUnknownMutation,
    usePracticeDetailQuery,
    useSavePracticeAnswerMutation,
    useSubmitPracticeItemMutation,
    useSubmitPracticeSessionMutation,
} from "@/lib/api/hooks";
import { getAccessToken } from "@/lib/auth";
import { getApiBaseUrl } from "@/lib/api/client";
import { useGlobalErrorDialog } from "@/lib/error/ErrorDialogProvider";

const RECENT_PRACTICE_KEY = "qa-agent:recent-practice";

function clampIndex(index: number, total: number) {
    if (total <= 0) return 0;
    return Math.min(Math.max(index, 0), total - 1);
}

function formatSaveTime(date: Date) {
    const hh = String(date.getHours()).padStart(2, "0");
    const mm = String(date.getMinutes()).padStart(2, "0");
    const ss = String(date.getSeconds()).padStart(2, "0");
    return `${hh}:${mm}:${ss}`;
}

function durationLabel(totalSeconds: number) {
    const seconds = Math.max(0, Math.floor(totalSeconds));
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor(seconds / 60);
    const remainSeconds = seconds % 60;
    if (hours > 0) {
        return `${hours}:${String(minutes % 60).padStart(2, "0")}:${String(remainSeconds).padStart(2, "0")}`;
    }
    return `${String(minutes).padStart(2, "0")}:${String(remainSeconds).padStart(2, "0")}`;
}

export function PracticePage() {
    const { sessionId = "" } = useParams();
    const navigate = useNavigate();
    const { showErrorDialog } = useGlobalErrorDialog();
    const detailQuery = usePracticeDetailQuery(sessionId);
    const saveMutation = useSavePracticeAnswerMutation();
    const submitItemMutation = useSubmitPracticeItemMutation();
    const markUnknownMutation = useMarkPracticeUnknownMutation();
    const submitSessionMutation = useSubmitPracticeSessionMutation();
    const abandonMutation = useAbandonPracticeMutation();
    const [currentIndex, setCurrentIndex] = useState(0);
    const [answer, setAnswer] = useState("");
    const [lastSavedAt, setLastSavedAt] = useState<string | null>(null);
    const [abandonOpen, setAbandonOpen] = useState(false);
    const [durationSeconds, setDurationSeconds] = useState(0);
    const durationBaseRef = useRef(0);
    const durationStartedAtRef = useRef<number | null>(null);
    const durationRef = useRef(0);
    const saveBeforeUnloadRef = useRef<() => void>(() => {});
    useEffect(() => { durationRef.current = durationSeconds; }, [durationSeconds]);

    const detail = detailQuery.data;
    const items = detail?.items ?? [];
    const session = detail?.session;
    const currentItem = items[currentIndex];
    const readonly = session?.status === "FINISHED" || session?.status === "ABANDONED";
    const feedbackMode = session?.feedbackMode ?? "ITEM_BY_ITEM";
    const afterAll = feedbackMode === "AFTER_ALL";
    const itemSubmitted = currentItem?.status === "SUBMITTED";
    const itemReadonly = readonly || (!afterAll && itemSubmitted);

    useEffect(() => {
        if (!detail) return;
        const nextIndex = clampIndex(detail.session.currentIndex, detail.items.length);
        setCurrentIndex(nextIndex);
        setAnswer(detail.items[nextIndex]?.userAnswer ?? "");
        durationBaseRef.current = detail.session.durationSeconds ?? 0;
        durationStartedAtRef.current = Date.now();
        setDurationSeconds(durationBaseRef.current);
        window.localStorage.setItem(RECENT_PRACTICE_KEY, JSON.stringify({
            sessionId: detail.session.id,
            qaSetId: detail.session.qaSetId,
            currentIndex: nextIndex,
            updatedAt: new Date().toISOString(),
        }));
        if (detail.session.status === "FINISHED") {
            navigate(`/practice/${detail.session.id}/result`, { replace: true });
        }
    }, [detail, navigate]);

    useEffect(() => {
        if (!currentItem) return;
        setAnswer(currentItem.userAnswer ?? "");
    }, [currentItem?.sessionItemId]);

    useEffect(() => {
        const timer = window.setInterval(() => {
            if (readonly || durationStartedAtRef.current == null) {
                setDurationSeconds(durationBaseRef.current);
                return;
            }
            const delta = Math.floor((Date.now() - durationStartedAtRef.current) / 1000);
            setDurationSeconds(durationBaseRef.current + delta);
        }, 1000);
        return () => window.clearInterval(timer);
    }, [readonly]);

    // Save duration before page unload (refresh / close)
    useEffect(() => {
        saveBeforeUnloadRef.current = () => {
            const token = getAccessToken();
            if (!token || !currentItem || itemReadonly) return;
            fetch(`${getApiBaseUrl()}/practice/item/save`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${token}`,
                },
                body: JSON.stringify({
                    sessionId,
                    sessionItemId: currentItem.sessionItemId,
                    userAnswer: currentItem.userAnswer ?? "",
                    currentIndex,
                    durationSeconds: durationRef.current,
                }),
                keepalive: true,
            });
        };
    }, [sessionId, currentItem, currentIndex, itemReadonly]);

    useEffect(() => {
        const handler = () => saveBeforeUnloadRef.current();
        window.addEventListener("beforeunload", handler);
        return () => window.removeEventListener("beforeunload", handler);
    }, []);

    // Auto-save every 60 seconds
    useEffect(() => {
        if (!currentItem || itemReadonly) return;
        const id = window.setInterval(() => {
            if ((currentItem.userAnswer ?? "") === answer) return;
            saveMutation.mutate({
                sessionId,
                sessionItemId: currentItem.sessionItemId,
                userAnswer: answer,
                currentIndex,
                durationSeconds,
            }, {
                onSuccess: () => setLastSavedAt(formatSaveTime(new Date())),
            });
        }, 60_000);
        return () => window.clearInterval(id);
    }, [answer, currentIndex, currentItem, durationSeconds, itemReadonly, saveMutation, sessionId]);

    // Save immediately when entering a new question
    useEffect(() => {
        if (!currentItem || itemReadonly) return;
        const doSave = async () => {
            try {
                await saveMutation.mutateAsync({
                    sessionId,
                    sessionItemId: currentItem.sessionItemId,
                    userAnswer: currentItem.userAnswer ?? "",
                    currentIndex,
                    durationSeconds,
                });
                setLastSavedAt(formatSaveTime(new Date()));
            } catch { /* handled by mutation */ }
        };
        doSave();
    }, [currentItem?.sessionItemId]);

    // Idle timer: show encouragement bubble if stuck on same question > 3 min
    const idleFiredRef = useRef(false);
    const itemEnteredAtRef = useRef(Date.now());
    useEffect(() => {
        itemEnteredAtRef.current = Date.now();
        idleFiredRef.current = false;
    }, [currentItem?.sessionItemId]);

    useEffect(() => {
        if (!currentItem || itemReadonly) return;
        const id = window.setInterval(() => {
            if (idleFiredRef.current) return;
            if (Date.now() - itemEnteredAtRef.current > 180_000) {
                idleFiredRef.current = true;
                emitDasiBubble("这道题花了些时间，实在卡住了可以先标记「不会」跳过哦 ⏰");
            }
        }, 30000);
        return () => window.clearInterval(id);
    }, [currentItem, itemReadonly]);

    const flushAnswer = async (force = false, nextIndex = currentIndex) => {
        if (!currentItem || itemReadonly) return;
        if (!force && (currentItem.userAnswer ?? "") === answer) return;
        await saveMutation.mutateAsync({
            sessionId,
            sessionItemId: currentItem.sessionItemId,
            userAnswer: answer,
            currentIndex: nextIndex,
            durationSeconds,
        });
        setLastSavedAt(formatSaveTime(new Date()));
    };

    const jumpTo = async (index: number) => {
        const nextIndex = clampIndex(index, items.length);
        await flushAnswer(true, nextIndex);
        setCurrentIndex(nextIndex);
        setAnswer(items[nextIndex]?.userAnswer ?? "");
    };

    const handleSubmitItem = async () => {
        if (!currentItem) return;
        if (!answer.trim()) {
            showErrorDialog({ title: "请先作答", message: "提交本题前需要填写答案，或者选择“不会”。" });
            return;
        }
        await submitItemMutation.mutateAsync({
            sessionId,
            sessionItemId: currentItem.sessionItemId,
            userAnswer: answer,
            currentIndex,
            durationSeconds,
        });
    };

    const handleUnknown = async () => {
        if (!currentItem) return;
        await markUnknownMutation.mutateAsync({
            sessionId,
            sessionItemId: currentItem.sessionItemId,
            userAnswer: answer,
            currentIndex,
            durationSeconds,
        });
        emitDasiBubble("不会也没关系，标记下来回头再攻克它～ 🤗");
    };

    const itemHasAnswer = (item: typeof currentItem, index: number) => {
        if (!item) return false;
        if (item.unknown || item.status === "UNKNOWN") return true;
        if (index === currentIndex) return answer.trim().length > 0;
        return (item.userAnswer ?? "").trim().length > 0;
    };

    const handleSubmitSession = async () => {
        await flushAnswer(true);
        const remaining = afterAll
            ? items.filter((item, index) => !itemHasAnswer(item, index))
            : items.filter((item) => item.status !== "SUBMITTED");
        if (remaining.length) {
            showErrorDialog({
                title: "还有题目未完成",
                message: afterAll
                    ? `当前还有 ${remaining.length} 道题未作答或未标记不会。`
                    : `当前还有 ${remaining.length} 道题未提交。`,
            });
            return;
        }
        await submitSessionMutation.mutateAsync({ sessionId, durationSeconds });
        emitDasiBubble("🎉 一轮练习完成！来看看总结，薄弱点值得再多花时间。");
        navigate(`/practice/${sessionId}/result`);
    };

    const handleExit = async () => {
        await flushAnswer(true);
        navigate("/quiz");
    };

    const handleAbandon = async () => {
        await abandonMutation.mutateAsync({ sessionId, durationSeconds });
        navigate("/quiz");
    };

    const topStatus = (
        <>
            <div className="practice-top-status__left">
                <BaseButton variant="ghost" className="practice-top-status__exit" leadingIcon={<CornerUpLeft size={16} />} onClick={handleExit}>
                    退出
                </BaseButton>
                <BaseButton
                    variant="ghost"
                    className="practice-top-status__exit"
                    leadingIcon={<Save size={16} />}
                    onClick={async () => {
                        if (!currentItem || itemReadonly) return;
                        try {
                            await saveMutation.mutateAsync({
                                sessionId,
                                sessionItemId: currentItem.sessionItemId,
                                userAnswer: answer,
                                currentIndex,
                                durationSeconds,
                            });
                            setLastSavedAt(formatSaveTime(new Date()));
                        } catch { /* handled by mutation */ }
                    }}
                    disabled={!currentItem || itemReadonly}
                >
                    保存
                </BaseButton>
            </div>
            <div className="practice-top-status__center">
                <strong>{session?.qaSetTitle || "练习"}</strong>
            </div>
            <div className="practice-top-status__right">
                <span><Save size={14} />{lastSavedAt ? `保存于 ${lastSavedAt}` : "尚未保存"}</span>
                <span><Clock size={14} />{durationLabel(durationSeconds)}</span>
            </div>
        </>
    );

    const questionWorkspace = (
        <QuestionWorkspace
            item={currentItem}
            index={currentIndex}
            total={items.length}
            answer={answer}
            feedbackMode={feedbackMode}
            showFeedback={feedbackMode === "ITEM_BY_ITEM" && currentItem?.status === "SUBMITTED"}
            submitting={submitItemMutation.isPending || markUnknownMutation.isPending || saveMutation.isPending}
            readonly={itemReadonly}
            onAnswerChange={setAnswer}
            onPrev={() => jumpTo(currentIndex - 1)}
            onNext={() => jumpTo(currentIndex + 1)}
            onUnknown={handleUnknown}
            onSubmit={handleSubmitItem}
            onSaveAndNext={() => jumpTo(currentIndex + 1)}
        />
    );

    const answerCard = (
        <AnswerCard
            items={items}
            currentIndex={currentIndex}
            feedbackMode={feedbackMode}
            onJump={jumpTo}
            onSubmitSession={handleSubmitSession}
            onAbandon={() => setAbandonOpen(true)}
            submitting={submitSessionMutation.isPending}
        />
    );

    if (detailQuery.isLoading) {
        return (
            <div className="practice-shell">
                <div className="practice-shell__center">正在恢复练习进度...</div>
                <SiteFooter />
            </div>
        );
    }

    if (detailQuery.isError || !detail || !currentItem) {
        return (
            <div className="practice-shell">
                <div className="practice-shell__center">
                    <strong>练习加载失败</strong>
                    <BaseButton variant="primary" onClick={() => detailQuery.refetch()}>重试</BaseButton>
                    <BaseButton variant="link" onClick={() => navigate("/quiz")}>返回练习页</BaseButton>
                </div>
                <SiteFooter />
            </div>
        );
    }

    if (submitSessionMutation.isPending) {
        return <AssessmentGeneratingPanel />;
    }

    return (
        <>
            <PracticeLayout
                topStatus={topStatus}
                workspace={questionWorkspace}
                answerCard={answerCard}
            />
            <ConfirmDialog
                open={abandonOpen}
                title="放弃本轮练习"
                message="放弃后当前练习会结束，已保存的内容会保留。"
                confirmLabel={abandonMutation.isPending ? "处理中" : "放弃"}
                cancelLabel="继续练习"
                variant="danger"
                loading={abandonMutation.isPending}
                onConfirm={handleAbandon}
                onCancel={() => setAbandonOpen(false)}
            />
        </>
    );
}
