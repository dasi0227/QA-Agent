import { useEffect, useMemo, useRef, useState } from "react";
import { ArrowLeft, Clock, LogOut, Save } from "lucide-react";
import { useNavigate, useParams } from "react-router";
import { BaseButton } from "@/components/base/button";
import { AnswerCard } from "@/components/practice/AnswerCard";
import { PracticeLayout } from "@/components/practice/PracticeLayout";
import { QuestionWorkspace } from "@/components/practice/QuestionWorkspace";
import {
    useAbandonPracticeMutation,
    useMarkPracticeUnknownMutation,
    usePracticeDetailQuery,
    useSavePracticeAnswerMutation,
    useSubmitPracticeItemMutation,
    useSubmitPracticeSessionMutation,
} from "@/lib/api/hooks";
import { useGlobalErrorDialog } from "@/lib/error/ErrorDialogProvider";

const RECENT_PRACTICE_KEY = "qa-agent:recent-practice";

function clampIndex(index: number, total: number) {
    if (total <= 0) return 0;
    return Math.min(Math.max(index, 0), total - 1);
}

function elapsedLabel(startedAt?: string) {
    if (!startedAt) return "00:00";
    const started = new Date(startedAt).getTime();
    if (!Number.isFinite(started)) return "00:00";
    const seconds = Math.max(0, Math.floor((Date.now() - started) / 1000));
    const minutes = Math.floor(seconds / 60);
    const remainSeconds = seconds % 60;
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
    const [saveStatus, setSaveStatus] = useState("已同步");
    const [cardCollapsed, setCardCollapsed] = useState(false);
    const [elapsed, setElapsed] = useState("00:00");
    const saveTimerRef = useRef<number | null>(null);

    const detail = detailQuery.data;
    const items = detail?.items ?? [];
    const session = detail?.session;
    const currentItem = items[currentIndex];
    const readonly = session?.status === "FINISHED" || session?.status === "ABANDONED";

    useEffect(() => {
        if (!detail) return;
        const nextIndex = clampIndex(detail.session.currentIndex, detail.items.length);
        setCurrentIndex(nextIndex);
        setAnswer(detail.items[nextIndex]?.userAnswer ?? "");
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
        const timer = window.setInterval(() => setElapsed(elapsedLabel(session?.startedAt)), 1000);
        setElapsed(elapsedLabel(session?.startedAt));
        return () => window.clearInterval(timer);
    }, [session?.startedAt]);

    useEffect(() => {
        if (!currentItem || readonly || currentItem.status === "SUBMITTED") return;
        if ((currentItem.userAnswer ?? "") === answer) return;
        setSaveStatus("保存中");
        if (saveTimerRef.current) {
            window.clearTimeout(saveTimerRef.current);
        }
        saveTimerRef.current = window.setTimeout(() => {
            saveMutation.mutate({
                sessionId,
                sessionItemId: currentItem.sessionItemId,
                userAnswer: answer,
                currentIndex,
            }, {
                onSuccess: () => setSaveStatus("已保存"),
                onError: () => setSaveStatus("保存失败"),
            });
        }, 700);
        return () => {
            if (saveTimerRef.current) {
                window.clearTimeout(saveTimerRef.current);
            }
        };
    }, [answer, currentIndex, currentItem, readonly, saveMutation, sessionId]);

    const flushAnswer = async () => {
        if (!currentItem || readonly || currentItem.status === "SUBMITTED") return;
        if ((currentItem.userAnswer ?? "") === answer) return;
        setSaveStatus("保存中");
        await saveMutation.mutateAsync({
            sessionId,
            sessionItemId: currentItem.sessionItemId,
            userAnswer: answer,
            currentIndex,
        });
        setSaveStatus("已保存");
    };

    const jumpTo = async (index: number) => {
        await flushAnswer();
        const nextIndex = clampIndex(index, items.length);
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
        });
        setSaveStatus("已提交");
    };

    const handleUnknown = async () => {
        if (!currentItem) return;
        await markUnknownMutation.mutateAsync({
            sessionId,
            sessionItemId: currentItem.sessionItemId,
            userAnswer: answer,
            currentIndex,
        });
        setSaveStatus("已标记不会");
    };

    const handleSubmitSession = async () => {
        await flushAnswer();
        const remaining = items.filter((item) => item.status !== "SUBMITTED" && item.status !== "UNKNOWN");
        if (remaining.length) {
            showErrorDialog({
                title: "还有题目未完成",
                message: `当前还有 ${remaining.length} 道题未提交或未标记不会。`,
            });
            return;
        }
        await submitSessionMutation.mutateAsync({ sessionId });
        navigate(`/practice/${sessionId}/result`);
    };

    const handleExit = async () => {
        await flushAnswer();
        navigate("/quiz");
    };

    const handleAbandon = async () => {
        if (!window.confirm("确认放弃本轮练习吗？已保存的历史不会删除。")) return;
        await abandonMutation.mutateAsync({ sessionId });
        navigate("/quiz");
    };

    const topStatus = useMemo(() => (
        <>
            <BaseButton variant="link" leadingIcon={<ArrowLeft size={16} />} onClick={handleExit}>
                退出并保存
            </BaseButton>
            <div className="practice-top-status__center">
                <strong>{session?.qaSetTitle || "练习"}</strong>
                <span>{session?.feedbackMode === "AFTER_ALL" ? "整轮反馈" : "逐题反馈"}</span>
            </div>
            <div className="practice-top-status__right">
                <span><Save size={14} />{saveStatus}</span>
                <span><Clock size={14} />{elapsed}</span>
                <BaseButton variant="ghost" leadingIcon={<LogOut size={15} />} onClick={handleAbandon} disabled={readonly}>
                    放弃
                </BaseButton>
            </div>
        </>
    ), [elapsed, readonly, saveStatus, session?.feedbackMode, session?.qaSetTitle]);

    if (detailQuery.isLoading) {
        return <div className="practice-shell practice-shell--center">正在恢复练习进度...</div>;
    }

    if (detailQuery.isError || !detail || !currentItem) {
        return (
            <div className="practice-shell practice-shell--center">
                <strong>练习加载失败</strong>
                <BaseButton variant="primary" onClick={() => detailQuery.refetch()}>重试</BaseButton>
                <BaseButton variant="link" onClick={() => navigate("/quiz")}>返回测试页</BaseButton>
            </div>
        );
    }

    return (
        <PracticeLayout
            topStatus={topStatus}
            workspace={(
                <QuestionWorkspace
                    item={currentItem}
                    index={currentIndex}
                    total={items.length}
                    answer={answer}
                    saveStatus={saveStatus}
                    submitting={submitItemMutation.isPending || markUnknownMutation.isPending}
                    readonly={readonly}
                    onAnswerChange={setAnswer}
                    onPrev={() => jumpTo(currentIndex - 1)}
                    onNext={() => jumpTo(currentIndex + 1)}
                    onUnknown={handleUnknown}
                    onSubmit={handleSubmitItem}
                />
            )}
            answerCard={(
                <AnswerCard
                    items={items}
                    currentIndex={currentIndex}
                    feedbackMode={session?.feedbackMode ?? "ITEM_BY_ITEM"}
                    collapsed={cardCollapsed}
                    onToggle={() => setCardCollapsed((value) => !value)}
                    onJump={jumpTo}
                    onSubmitSession={handleSubmitSession}
                    submitting={submitSessionMutation.isPending}
                />
            )}
        />
    );
}
