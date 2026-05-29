import { ArrowLeft, ArrowRight, Clock } from "lucide-react";
import { useNavigate, useParams, useSearchParams } from "react-router";
import { BaseButton, LinkButton } from "@/components/base/button";
import { Tag } from "@/components/base/tag";
import { AnswerCard } from "@/components/practice/AnswerCard";
import { PracticeLayout } from "@/components/practice/PracticeLayout";
import { QuestionFeedbackPanel } from "@/components/practice/QuestionFeedbackPanel";
import { SiteFooter } from "@/components/layout/SiteFooter";
import { usePracticeDetailQuery } from "@/lib/api/hooks";

function clampIndex(index: number, total: number) {
    if (total <= 0) return 0;
    return Math.min(Math.max(index, 0), total - 1);
}

function durationLabel(value?: number) {
    const totalSeconds = Math.max(0, Math.floor(value ?? 0));
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    if (hours > 0) {
        return `${hours}小时 ${String(minutes).padStart(2, "0")}分`;
    }
    return `${String(minutes).padStart(2, "0")}分 ${String(seconds).padStart(2, "0")}秒`;
}

function feedbackModeLabel(value?: string) {
    return value === "AFTER_ALL" ? "整轮反馈" : "逐题反馈";
}

export function ReviewPage() {
    const { sessionId = "" } = useParams();
    const [searchParams, setSearchParams] = useSearchParams();
    const navigate = useNavigate();
    const detailQuery = usePracticeDetailQuery(sessionId);
    const detail = detailQuery.data;
    const session = detail?.session;
    const items = detail?.items ?? [];
    const currentIndex = clampIndex(Number(searchParams.get("index") ?? 0), items.length);
    const currentItem = items[currentIndex];

    const jumpTo = (index: number) => {
        setSearchParams({ index: String(clampIndex(index, items.length)) });
    };

    if (detailQuery.isLoading) {
        return (
            <div className="practice-shell">
                <div className="practice-shell__center">正在读取回看记录...</div>
                <SiteFooter />
            </div>
        );
    }

    if (detailQuery.isError || !session || !currentItem) {
        return (
            <div className="practice-shell">
                <div className="practice-shell__center">
                    <strong>回看加载失败</strong>
                    <BaseButton variant="primary" type="button" onClick={() => detailQuery.refetch()}>重试</BaseButton>
                    <BaseButton variant="link" type="button" onClick={() => navigate("/quiz")}>返回练习页</BaseButton>
                </div>
                <SiteFooter />
            </div>
        );
    }

    if (session.status !== "FINISHED") {
        return (
            <div className="practice-shell">
                <div className="practice-shell__center">
                    <strong>本轮尚未完成</strong>
                    <LinkButton to={`/practice/${session.id}`} variant="primary">返回练习</LinkButton>
                </div>
                <SiteFooter />
            </div>
        );
    }

    const topStatus = (
        <>
            <div className="practice-top-status__left">
                <BaseButton variant="ghost" className="practice-top-status__exit" leadingIcon={<ArrowLeft size={16} />} onClick={() => navigate(`/practice/${session.id}/result`)}>
                    返回结果
                </BaseButton>
            </div>
            <div className="practice-top-status__center">
                <strong>{session.qaSetTitle || "练习回看"}</strong>
            </div>
            <div className="practice-top-status__right">
                <span>{feedbackModeLabel(session.feedbackMode)}</span>
                <span><Clock size={14} />{durationLabel(session.durationSeconds)}</span>
            </div>
        </>
    );

    const workspace = (
        <main className="question-workspace review-workspace">
            <section className="question-workspace__question">
                <span className="review-workspace__eyebrow">第 {currentIndex + 1} / {items.length} 题</span>
                <h1>{currentItem.question}</h1>
                <div className="question-workspace__meta">
                    {currentItem.difficulty ? <Tag className="question-workspace__difficulty-tag">{currentItem.difficulty}</Tag> : null}
                    {currentItem.moduleTag ? <Tag className="question-workspace__module-tag">{currentItem.moduleTag}</Tag> : null}
                </div>
            </section>

            <section className="review-answer-block">
                <span>我的回答</span>
                <p>{currentItem.userAnswer || (currentItem.unknown ? "已标记不会" : "未作答")}</p>
            </section>

            {currentItem.standardAnswer ? (
                <section className="review-answer-block">
                    <span>参考答案</span>
                    <p>{currentItem.standardAnswer}</p>
                </section>
            ) : null}

            <QuestionFeedbackPanel item={currentItem} />

            <div className="practice-action-bar">
                <BaseButton variant="outline" leadingIcon={<ArrowLeft size={16} />} onClick={() => jumpTo(currentIndex - 1)} disabled={currentIndex <= 0}>
                    上一题
                </BaseButton>
                <BaseButton variant="outline" leadingIcon={<ArrowRight size={16} />} onClick={() => jumpTo(currentIndex + 1)} disabled={currentIndex >= items.length - 1}>
                    下一题
                </BaseButton>
                <BaseButton variant="primary" onClick={() => navigate(`/practice/${session.id}/result`)}>
                    返回结果页
                </BaseButton>
            </div>
        </main>
    );

    const answerCard = (
        <AnswerCard
            items={items}
            currentIndex={currentIndex}
            feedbackMode={session.feedbackMode}
            readonly
            onJump={jumpTo}
            onSubmitSession={() => undefined}
            onAbandon={() => undefined}
        />
    );

    return <PracticeLayout topStatus={topStatus} workspace={workspace} answerCard={answerCard} />;
}
