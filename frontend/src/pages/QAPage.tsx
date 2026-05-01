import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router";
import { z } from "zod";
import { ArrowLeft } from "lucide-react";
import { BaseButton } from "@/components/base/button";
import { Chip, Tag } from "@/components/base/tag";
import { Field, TextArea } from "@/components/base/field";
import { GlassCard } from "@/components/base/card";
import {
    useContinuePracticeSessionMutation,
    useFinishPracticeSessionMutation,
    useMarkUnknownMutation,
    usePracticeSessionQuery,
    useSubmitPracticeAnswerMutation,
} from "@/lib/api/hooks";
import { saveLastPracticeSession } from "@/lib/practice-history";
import { cn } from "@/lib/cn";

const answerSchema = z.object({
    answer: z.string().min(12, "请至少写一点回答"),
});

type AnswerForm = z.infer<typeof answerSchema>;

export function QAPage() {
    const params = useParams();
    const navigate = useNavigate();
    const sessionId = params.sessionId ?? "";
    const sessionQuery = usePracticeSessionQuery(sessionId || undefined);
    const submitAnswerMutation = useSubmitPracticeAnswerMutation();
    const markUnknownMutation = useMarkUnknownMutation();
    const continueMutation = useContinuePracticeSessionMutation();
    const finishMutation = useFinishPracticeSessionMutation();
    const [supportHintVisible, setSupportHintVisible] = useState(false);
    const [revealed, setRevealed] = useState(false);

    const form = useForm<AnswerForm>({
        resolver: zodResolver(answerSchema),
        defaultValues: {
            answer: "",
        },
    });
    const answerField = form.register("answer");

    useEffect(() => {
        if (sessionQuery.data?.currentAnswer) {
            form.reset({ answer: sessionQuery.data.currentAnswer });
        }
        if (sessionQuery.data?.feedback || sessionQuery.data?.canRevealAnswer) {
            setRevealed(true);
        }
    }, [form, sessionQuery.data]);

    useEffect(() => {
        if (!sessionQuery.data?.currentQuestion) {
            return;
        }

        saveLastPracticeSession({
            sessionId,
            questionSetId: sessionQuery.data.questionSetId,
            questionSetTitle: sessionQuery.data.questionSetTitle || "",
            currentQuestionIndex: sessionQuery.data.currentQuestionIndex,
            totalQuestions: sessionQuery.data.totalQuestions,
            currentQuestion: sessionQuery.data.currentQuestion.question,
            mode: sessionQuery.data.mode,
            feedbackMode: sessionQuery.data.feedbackMode,
            status: sessionQuery.data.status,
            updatedAt: new Date().toISOString(),
        });
    }, [sessionId, sessionQuery.data]);

    const latestAnswer = submitAnswerMutation.data ?? markUnknownMutation.data ?? sessionQuery.data?.latestAnswer ?? null;
    const currentQuestion = sessionQuery.data?.currentQuestion;
    const feedbackVisible = Boolean(revealed && latestAnswer && sessionQuery.data?.feedbackMode === "ITEM_BY_ITEM");
    const answerGuideText = latestAnswer?.standardAnswer || latestAnswer?.answerGuide || "";
    const isAnsweredLocally = Boolean(latestAnswer);
    const errorMessage = sessionQuery.error instanceof Error ? sessionQuery.error.message : "";

    return (
        <div className="qa-layout">
            <header className="sidebar__split" style={{ marginBottom: 16 }}>
                <Link to="/quiz" className="btn btn--link" style={{ paddingLeft: 0 }}>
                    <ArrowLeft size={14} />
                    返回测试页
                </Link>
                <div className="page-copy" style={{ fontSize: 12 }}>
                    Session {sessionQuery.data?.currentQuestionIndex ?? 0} / {sessionQuery.data?.totalQuestions ?? 0}
                </div>
            </header>

            {sessionQuery.isLoading ? (
                <GlassCard className="qa-card">
                    <div className="qa-feedback">
                        <strong>正在加载练习会话</strong>
                        <div className="qa-text">从真实接口读取当前题目、标签和反馈状态。</div>
                    </div>
                </GlassCard>
            ) : null}

            {sessionQuery.isError ? (
                <GlassCard className="qa-card">
                    <div className="qa-feedback">
                        <strong>练习会话加载失败</strong>
                        <div className="qa-text">{errorMessage || "请稍后重试。"}</div>
                        <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                            <BaseButton variant="soft" type="button" onClick={() => sessionQuery.refetch()}>
                                重试
                            </BaseButton>
                            <Link to="/quiz" className="btn btn--link">
                                返回测试页
                            </Link>
                        </div>
                    </div>
                </GlassCard>
            ) : null}

            {currentQuestion ? (
                <GlassCard className="qa-card">
                    <div className="qa-head">
                        <section style={{ maxWidth: 760 }}>
                            <div className="eyebrow">Question {sessionQuery.data?.currentQuestionIndex}</div>
                            <h1 className="qa-question">{currentQuestion.question}</h1>
                        </section>

                        <aside className="qa-aside">
                            <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "flex-end", gap: 8 }}>
                                {currentQuestion.tags.map((tag) => (
                                    <Tag key={tag}>{tag}</Tag>
                                ))}
                                {currentQuestion.difficulty ? <Tag>{currentQuestion.difficulty}</Tag> : null}
                            </div>
                            <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "flex-end", gap: 8 }}>
                                <BaseButton
                                    variant="soft"
                                    type="button"
                                    onClick={() => setSupportHintVisible((value) => !value)}
                                >
                                    提示
                                </BaseButton>
                                <BaseButton
                                    variant="outline"
                                    type="button"
                                    disabled={markUnknownMutation.isPending}
                                    onClick={async () => {
                                        await markUnknownMutation.mutateAsync(sessionId);
                                        setRevealed(true);
                                        form.setValue("answer", "", { shouldDirty: true });
                                    }}
                                >
                                    {markUnknownMutation.isPending ? "记录中" : "不会"}
                                </BaseButton>
                            </div>
                        </aside>
                    </div>

                    <p className="qa-text">{currentQuestion.hint}</p>

                    {currentQuestion.conflictTip ? (
                        <div className="qa-feedback" style={{ marginBottom: 18 }}>
                            <strong>证据边界</strong>
                            <div className="qa-text">{currentQuestion.conflictTip}</div>
                        </div>
                    ) : null}

                    {supportHintVisible ? (
                        <div className="qa-feedback" style={{ marginBottom: 18 }}>
                            <strong>提示</strong>
                            <div className="qa-text">
                                {currentQuestion.scoringRubric?.answerStructure || "先从职责、访问模型、边界和取舍切入，再补项目例子。"}
                            </div>
                            {currentQuestion.scoringRubric?.keyPoints?.length ? (
                                <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 12 }}>
                                    {currentQuestion.scoringRubric.keyPoints.map((point) => (
                                        <Tag key={point}>{point}</Tag>
                                    ))}
                                </div>
                            ) : null}
                        </div>
                    ) : null}

                    <form
                        className="qa-editor"
                        onSubmit={form.handleSubmit(async (values) => {
                            await submitAnswerMutation.mutateAsync({
                                sessionId,
                                answer: values.answer,
                            });
                            setRevealed(true);
                        })}
                    >
                        <Field label="作答区">
                            <TextArea
                                className="qa-answer"
                                placeholder="在这里组织你的回答，从职责、访问模型和持久化边界开始。"
                                disabled={isAnsweredLocally}
                                {...answerField}
                            />
                        </Field>

                        {feedbackVisible ? (
                            <div className="qa-feedback">
                                <div className="sidebar__split">
                                    <strong>反馈</strong>
                                    <Chip>{latestAnswer?.feedbackDetail?.judgement || latestAnswer?.result || "已提交"}</Chip>
                                </div>
                                <div className="qa-text">
                                    {latestAnswer?.feedbackDetail?.reason || latestAnswer?.feedback || "当前题目已提交，反馈信息暂未返回。"}
                                </div>
                                {latestAnswer?.feedbackDetail?.missingPoints?.length ? (
                                    <div>
                                        <strong style={{ display: "block", marginBottom: 8 }}>缺失点</strong>
                                        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                                            {latestAnswer.feedbackDetail.missingPoints.map((item) => (
                                                <Tag key={item}>{item}</Tag>
                                            ))}
                                        </div>
                                    </div>
                                ) : null}
                                {latestAnswer?.feedbackDetail?.suggestions?.length ? (
                                    <div>
                                        <strong style={{ display: "block", margin: "12px 0 8px" }}>改进建议</strong>
                                        <div className="result-list">
                                            {latestAnswer.feedbackDetail.suggestions.map((item) => (
                                                <div key={item} className="result-item">
                                                    <strong>{item}</strong>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                ) : null}
                                {latestAnswer?.feedbackDetail?.evidenceRefs?.length ? (
                                    <div style={{ marginTop: 12 }}>
                                        <strong style={{ display: "block", marginBottom: 8 }}>证据引用</strong>
                                        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                                            {latestAnswer.feedbackDetail.evidenceRefs.map((ref) => (
                                                <Tag key={ref}>{ref}</Tag>
                                            ))}
                                        </div>
                                    </div>
                                ) : null}
                                <div className="qa-text">{answerGuideText || "后端返回后会展示标准答题提示。"}</div>
                            </div>
                        ) : null}

                        {revealed && latestAnswer && sessionQuery.data?.feedbackMode === "AFTER_ALL" ? (
                            <div className="qa-feedback">
                                <strong>本题已记录</strong>
                                <div className="qa-text">当前反馈模式是整轮反馈，结束练习后统一展示结果。</div>
                                {latestAnswer.suggestions?.length ? (
                                    <div className="qa-text">建议先记住：{latestAnswer.suggestions.join("；")}</div>
                                ) : null}
                            </div>
                        ) : null}

                        <div className="sidebar__split">
                            <div style={{ display: "flex", flexWrap: "wrap", gap: 12 }}>
                                <button
                                    className={cn("btn btn--link")}
                                    type="button"
                                    onClick={() => setSupportHintVisible((value) => !value)}
                                >
                                    {supportHintVisible ? "收起提示" : "查看提示"}
                                </button>
                                <button
                                    className="btn btn--link"
                                    type="button"
                                    disabled={isAnsweredLocally}
                                    onClick={() => form.setFocus("answer")}
                                >
                                    聚焦作答
                                </button>
                            </div>
                            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                                {latestAnswer?.nextQuestion ? (
                                    <BaseButton
                                        variant="outline"
                                        type="button"
                                        disabled={continueMutation.isPending}
                                        onClick={async () => {
                                            submitAnswerMutation.reset();
                                            markUnknownMutation.reset();
                                            setRevealed(false);
                                            setSupportHintVisible(false);
                                            form.reset({ answer: "" });
                                            await continueMutation.mutateAsync(sessionId);
                                        }}
                                    >
                                        {continueMutation.isPending ? "切换中" : "下一题"}
                                    </BaseButton>
                                ) : null}
                                <BaseButton
                                    variant="soft"
                                    type="button"
                                    disabled={finishMutation.isPending}
                                    onClick={async () => {
                                        const result = await finishMutation.mutateAsync(sessionId);
                                        navigate(`/result/${result.sessionId || sessionId}`, { replace: true });
                                    }}
                                >
                                    {finishMutation.isPending ? "结束中" : "结束练习"}
                                </BaseButton>
                                <BaseButton
                                    variant="primary"
                                    type="submit"
                                    disabled={submitAnswerMutation.isPending || isAnsweredLocally}
                                >
                                    {submitAnswerMutation.isPending ? "提交中" : isAnsweredLocally ? "已提交" : "提交答案"}
                                </BaseButton>
                            </div>
                        </div>

                        {submitAnswerMutation.isError ? (
                            <div className="page-copy" style={{ color: "var(--ink)" }}>
                                提交失败：{submitAnswerMutation.error instanceof Error ? submitAnswerMutation.error.message : "请重试"}
                            </div>
                        ) : null}
                        {markUnknownMutation.isError ? (
                            <div className="page-copy" style={{ color: "var(--ink)" }}>
                                记录失败：{markUnknownMutation.error instanceof Error ? markUnknownMutation.error.message : "请重试"}
                            </div>
                        ) : null}
                        {finishMutation.isError ? (
                            <div className="page-copy" style={{ color: "var(--ink)" }}>
                                结束失败：{finishMutation.error instanceof Error ? finishMutation.error.message : "请重试"}
                            </div>
                        ) : null}
                        {continueMutation.isError ? (
                            <div className="page-copy" style={{ color: "var(--ink)" }}>
                                切换失败：{continueMutation.error instanceof Error ? continueMutation.error.message : "请重试"}
                            </div>
                        ) : null}
                    </form>
                </GlassCard>
            ) : null}
        </div>
    );
}
