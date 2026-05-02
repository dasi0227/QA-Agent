import { useMemo, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useNavigate, useSearchParams } from "react-router";
import { BaseButton, ChoiceButton, LinkButton } from "@/components/base/button";
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
    const [searchParams, setSearchParams] = useSearchParams();
    const navigate = useNavigate();
    const questionSetsQuery = useQuestionSetsQuery();
    const [practiceMode, setPracticeMode] = useState<"SEQUENTIAL" | "RANDOM">("SEQUENTIAL");
    const [feedbackMode, setFeedbackMode] = useState<"ITEM_BY_ITEM" | "AFTER_ALL">("ITEM_BY_ITEM");

    const selectedSetId = searchParams.get("questionSetId") ?? questionSetsQuery.data?.[0]?.id ?? "";
    const activeSet = useMemo(
        () => questionSetsQuery.data?.find((item) => item.id === selectedSetId) ?? questionSetsQuery.data?.[0],
        [questionSetsQuery.data, selectedSetId],
    );
    const questionSets = questionSetsQuery.data ?? [];
    const errorMessage = questionSetsQuery.error instanceof Error ? questionSetsQuery.error.message : "";

    const activeSetIndex = useMemo(() => {
        if (!questionSets.length) return -1;
        const directIndex = questionSets.findIndex((item) => item.id === selectedSetId);
        return directIndex >= 0 ? directIndex : 0;
    }, [questionSets, selectedSetId]);

    const activeSetPosition = activeSetIndex >= 0 ? activeSetIndex + 1 : 0;
    const hasCarouselNavigation = questionSets.length > 1;

    const carouselCards = useMemo(() => {
        if (!questionSets.length || activeSetIndex < 0) return [];
        const offsets = questionSets.length === 1 ? [0] : [-1, 0, 1];
        return offsets.map((offset) => {
            const index = (activeSetIndex + offset + questionSets.length) % questionSets.length;
            return { offset, item: questionSets[index] };
        });
    }, [activeSetIndex, questionSets]);

    const setActiveSetByIndex = (index: number) => {
        if (!questionSets.length) return;
        const nextIndex = (index + questionSets.length) % questionSets.length;
        const nextSet = questionSets[nextIndex];
        if (!nextSet || nextSet.id === selectedSetId) return;
        setSearchParams({ questionSetId: nextSet.id }, { replace: true });
    };

    const handleCarouselMove = (direction: -1 | 1) => {
        if (!hasCarouselNavigation || activeSetIndex < 0) return;
        setActiveSetByIndex(activeSetIndex + direction);
    };

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
                            <section className="quiz-carousel" aria-label="测试集轮播">
                                <button
                                    className="quiz-carousel__arrow quiz-carousel__arrow--left"
                                    type="button"
                                    onClick={() => handleCarouselMove(-1)}
                                    aria-label="切换到上一个测试集"
                                    disabled={!hasCarouselNavigation}
                                >
                                    <ChevronLeft size={28} strokeWidth={1.8} />
                                </button>
                                <div className="quiz-carousel__stage">
                                    <div className="quiz-carousel__counter" aria-live="polite">
                                        {activeSetPosition} / {questionSets.length || 0}
                                    </div>
                                    {carouselCards.map(({ item, offset }) => {
                                        const isActiveCard = offset === 0;
                                        const shift = offset === 0 ? "0%" : offset < 0 ? "-42%" : "42%";
                                        const scale = offset === 0 ? 1 : 0.84;
                                        const opacity = offset === 0 ? 1 : 0.38;
                                        const blur = offset === 0 ? "none" : "saturate(0.78) brightness(0.92)";
                                        const tilt = offset === 0 ? "0deg" : offset < 0 ? "16deg" : "-16deg";
                                        const lift = offset === 0 ? "-10px" : "12px";

                                        return (
                                            <button
                                                key={`slot-${offset}`}
                                                type="button"
                                                className={
                                                    isActiveCard
                                                        ? "quiz-carousel__card quiz-carousel__card--active"
                                                        : "quiz-carousel__card quiz-carousel__card--ghost"
                                                }
                                                style={{
                                                    transform: `translate(-50%, -50%) translateX(${shift}) translateY(${lift}) scale(${scale}) rotateY(${tilt})`,
                                                    opacity,
                                                    filter: blur,
                                                    zIndex: isActiveCard ? 3 : 1,
                                                }}
                                                onClick={() => {
                                                    if (isActiveCard) {
                                                        navigate(`/repository/${item.id}`);
                                                    } else {
                                                        setActiveSetByIndex(questionSets.findIndex((set) => set.id === item.id));
                                                    }
                                                }}
                                                aria-label={`切换到 ${item.title}`}
                                            >
                                                <div className="quiz-focus-card__center">
                                                    <div className="quiz-focus-card__title">{item.title}</div>
                                                    <div className="quiz-focus-card__meta" style={{ display: "flex", gap: 14, justifyContent: "center", flexWrap: "wrap" }}>
                                                        <span>平均分 {item.averageScore}</span>
                                                        <span>正确率 {item.averageAccuracy}%</span>
                                                        <span>共 {item.questionCount} 题</span>
                                                        <span>已做 {item.practiceCount} 轮</span>
                                                    </div>
                                                    {parseModuleTags(item.moduleTagsJson).length ? (
                                                        <div className="quiz-focus-card__tags">
                                                            {parseModuleTags(item.moduleTagsJson).map((tag) => (
                                                                <span key={tag} className="quiz-badge">
                                                                    {tag}
                                                                </span>
                                                            ))}
                                                        </div>
                                                    ) : null}
                                                </div>
                                            </button>
                                        );
                                    })}
                                </div>
                                <button
                                    className="quiz-carousel__arrow quiz-carousel__arrow--right"
                                    type="button"
                                    onClick={() => handleCarouselMove(1)}
                                    aria-label="切换到下一个测试集"
                                    disabled={!hasCarouselNavigation}
                                >
                                    <ChevronRight size={28} strokeWidth={1.8} />
                                </button>
                            </section>

                            <section className="quiz-controls" style={{ marginTop: 10 }}>
                                <div className="quiz-controls__group">
                                    <div className="sidebar__label">练习模式</div>
                                    <div className="quiz-controls__buttons">
                                        {practiceModes.map((mode) => (
                                            <ChoiceButton
                                                key={mode.value}
                                                selected={practiceMode === mode.value}
                                                className={mode.className}
                                                onClick={() => setPracticeMode(mode.value)}
                                            >
                                                {mode.label}
                                            </ChoiceButton>
                                        ))}
                                    </div>
                                </div>
                                <div className="quiz-controls__action-group">
                                    <div className="sidebar__label">执行操作</div>
                                    <div className="quiz-controls__actions">
                                        <BaseButton
                                            variant="ghost"
                                            className="btn--quiz-action"
                                            type="button"
                                            onClick={() => alert("接口尚未实现")}
                                        >
                                            开始练习
                                        </BaseButton>
                                        <BaseButton
                                            variant="ghost"
                                            className="btn--quiz-action"
                                            type="button"
                                            onClick={() => alert("接口尚未实现")}
                                        >
                                            继续测试
                                        </BaseButton>
                                    </div>
                                </div>
                                <div className="quiz-controls__group">
                                    <div className="sidebar__label">反馈模式</div>
                                    <div className="quiz-controls__buttons">
                                        {feedbackModes.map((mode) => (
                                            <ChoiceButton
                                                key={mode.value}
                                                selected={feedbackMode === mode.value}
                                                className={mode.className}
                                                onClick={() => setFeedbackMode(mode.value)}
                                            >
                                                {mode.label}
                                            </ChoiceButton>
                                        ))}
                                    </div>
                                </div>
                            </section>

                        </>
                    ) : null}
                </div>
            </GlassCard>
        </div>
    );
}
