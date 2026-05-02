import { useMemo } from "react";
import { useSearchParams } from "react-router";
import { BaseButton, LinkButton } from "@/components/base/button";
import { ChoiceButton } from "@/components/base/button";
import { GlassCard } from "@/components/base/card";
import { parseModuleTags, useQuestionSetsQuery } from "@/lib/api/hooks";

const practiceModes = [
    { label: "顺序练习", value: "SEQUENTIAL" as const, className: "choice-btn--quiz-tone" },
    { label: "随机练习", value: "RANDOM" as const, className: "choice-btn--quiz-tone" },
];

const feedbackModes = [
    { label: "逐题反馈", value: "ITEM_BY_ITEM" as const, className: "choice-btn--quiz-tone" },
    { label: "整轮反馈", value: "AFTER_ALL" as const, className: "choice-btn--quiz-tone" },
];

export function QuizPage() {
    const [searchParams] = useSearchParams();
    const questionSetsQuery = useQuestionSetsQuery();

    const selectedSetId = searchParams.get("questionSetId") ?? questionSetsQuery.data?.[0]?.id ?? "";
    const activeSet = useMemo(
        () => questionSetsQuery.data?.find((item) => item.id === selectedSetId) ?? questionSetsQuery.data?.[0],
        [questionSetsQuery.data, selectedSetId],
    );
    const errorMessage = questionSetsQuery.error instanceof Error ? questionSetsQuery.error.message : "";

    return (
        <div className="page-frame">
            <GlassCard className="hero-card hero-card--plain" style={{ width: "min(1180px, 84vw)" }}>
                <div className="quiz-hero">
                    {questionSetsQuery.isLoading ? (
                        <div className="qa-feedback" style={{ marginTop: 12, width: "min(720px, 100%)" }}>
                            <strong>正在加载问答集</strong>
                            <div className="qa-text">从真实接口读取可练习的问答集列表。</div>
                        </div>
                    ) : null}

                    {questionSetsQuery.isError ? (
                        <div className="qa-feedback" style={{ marginTop: 12, width: "min(720px, 100%)" }}>
                            <strong>问答集加载失败</strong>
                            <div className="qa-text">{errorMessage || "请稍后重试。"}</div>
                            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                                <BaseButton variant="soft" type="button" onClick={() => questionSetsQuery.refetch()}>
                                    重试
                                </BaseButton>
                                <LinkButton to="/repository" variant="ghost">
                                    去仓库
                                </LinkButton>
                            </div>
                        </div>
                    ) : null}

                    {activeSet ? (
                        <>
                            <section className="quiz-carousel" aria-label="测试集概览">
                                <div className="quiz-carousel__stage">
                                    <div className="quiz-carousel__counter" aria-live="polite">
                                        当前题集
                                    </div>
                                    <div
                                        className="quiz-carousel__card quiz-carousel__card--active"
                                        style={{
                                            transform: "translate(-50%, -50%) translateX(0%) translateY(-10px) scale(1) rotateY(0deg)",
                                            opacity: 1,
                                            filter: "none",
                                            zIndex: 3,
                                        }}
                                    >
                                        <div className="quiz-focus-card__center">
                                            <div className="quiz-focus-card__title">{activeSet.title}</div>
                                            <div className="quiz-focus-card__meta">
                                                {activeSet.questionCount} 题 · 平均分 {activeSet.averageScore}
                                            </div>
                                            {parseModuleTags(activeSet.moduleTagsJson).length ? (
                                                <div className="quiz-focus-card__tags">
                                                    {parseModuleTags(activeSet.moduleTagsJson).map((tag) => (
                                                        <span key={tag} className="quiz-badge">
                                                            {tag}
                                                        </span>
                                                    ))}
                                                </div>
                                            ) : null}
                                        </div>
                                    </div>
                                </div>
                            </section>

                            <section className="quiz-controls" style={{ marginTop: 10 }}>
                                <div className="quiz-controls__group">
                                    <div className="sidebar__label">练习模式</div>
                                    <div className="quiz-controls__buttons">
                                        {practiceModes.map((mode) => (
                                            <ChoiceButton key={mode.value} selected={mode.value === "SEQUENTIAL"} className={mode.className}>
                                                {mode.label}
                                            </ChoiceButton>
                                        ))}
                                    </div>
                                </div>
                                <div className="quiz-controls__group">
                                    <div className="sidebar__label">反馈模式</div>
                                    <div className="quiz-controls__buttons">
                                        {feedbackModes.map((mode) => (
                                            <ChoiceButton key={mode.value} selected={mode.value === "ITEM_BY_ITEM"} className={mode.className}>
                                                {mode.label}
                                            </ChoiceButton>
                                        ))}
                                    </div>
                                </div>
                            </section>

                            <div className="qa-feedback" style={{ marginTop: 16, width: "min(720px, 100%)" }}>
                                <strong>练习链路未接入</strong>
                                <div className="qa-text">第一版当前只打通资产查询与维护。练习、反馈、评分链路暂不可用。</div>
                            </div>

                            <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginTop: 12 }}>
                                <BaseButton variant="primary" type="button" disabled>
                                    开始练习未接入
                                </BaseButton>
                                <BaseButton variant="ghost" type="button" disabled>
                                    继续测试未接入
                                </BaseButton>
                                <LinkButton to={`/repository/${activeSet.id}`} variant="soft">
                                    去维护题目
                                </LinkButton>
                            </div>
                        </>
                    ) : null}
                </div>
            </GlassCard>
        </div>
    );
}
