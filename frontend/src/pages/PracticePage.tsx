import { useEffect, useRef, useState } from "react";
import { Clock, CornerUpLeft, Save } from "lucide-react";
import { useNavigate, useParams } from "react-router";
import { BaseButton } from "@/components/base/button";
import { ConfirmDialog } from "@/components/base/confirm-dialog";
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
    const [saveStatus, setSaveStatus] = useState("自动保存");
    const [abandonOpen, setAbandonOpen] = useState(false);
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
        setSaveStatus("判题中");
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
        await abandonMutation.mutateAsync({ sessionId });
        navigate("/quiz");
    };

    const topStatus = (
        <>
            <div className="practice-top-status__left">
                <BaseButton variant="ghost" className="practice-top-status__exit" leadingIcon={<CornerUpLeft size={16} />} onClick={handleExit}>
                    退出
                </BaseButton>
            </div>
            <div className="practice-top-status__center">
                <strong>{session?.qaSetTitle || "练习"}</strong>
            </div>
            <div className="practice-top-status__right">
                <span><Save size={14} />{saveStatus}</span>
                <span><Clock size={14} />{elapsed}</span>
            </div>
        </>
    );

    const questionWorkspace = (
        <QuestionWorkspace
            item={currentItem}
            index={currentIndex}
            total={items.length}
            answer={answer}
            submitting={submitItemMutation.isPending || markUnknownMutation.isPending}
            readonly={readonly}
            onAnswerChange={setAnswer}
            onPrev={() => jumpTo(currentIndex - 1)}
            onNext={() => jumpTo(currentIndex + 1)}
            onUnknown={handleUnknown}
            onSubmit={handleSubmitItem}
        />
    );

    const answerCard = (
        <AnswerCard
            items={items}
            currentIndex={currentIndex}
            feedbackMode={session?.feedbackMode ?? "ITEM_BY_ITEM"}
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
                    <BaseButton variant="link" onClick={() => navigate("/quiz")}>返回测试页</BaseButton>
                </div>
                <SiteFooter />
            </div>
        );
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
