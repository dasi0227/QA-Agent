import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useNavigate, useSearchParams } from "react-router";
import { BaseButton, ChoiceButton, LinkButton } from "@/components/base/button";
import { GlassCard } from "@/components/base/card";
import {
    parseModuleTags,
    useExistingPracticeSessionQuery,
    useQuestionSetsQuery,
    useRestartPracticeMutation,
    useStartPracticeMutation,
} from "@/lib/api/hooks";

const practiceModes = [
    { label: "顺序练习", value: "SEQUENTIAL" as const, className: "choice-btn--quiz-tone" },
    { label: "随机练习", value: "RANDOM" as const, className: "choice-btn--quiz-tone" },
];

const feedbackModes = [
    { label: "逐题反馈", value: "ITEM_BY_ITEM" as const, className: "choice-btn--quiz-tone" },
    { label: "整轮反馈", value: "AFTER_ALL" as const, className: "choice-btn--quiz-tone" },
];

const QUIZ_CAROUSEL_ANIMATION_MS = 480;

type CarouselDirection = -1 | 0 | 1;

export function QuizPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const navigate = useNavigate();
    const questionSetsQuery = useQuestionSetsQuery();
    const [practiceMode, setPracticeMode] = useState<"SEQUENTIAL" | "RANDOM">("SEQUENTIAL");
    const [feedbackMode, setFeedbackMode] = useState<"ITEM_BY_ITEM" | "AFTER_ALL">("ITEM_BY_ITEM");
    const [isAnimating, setIsAnimating] = useState(false);
    const [transitioningDirection, setTransitioningDirection] = useState<CarouselDirection>(0);
    const animationTimerRef = useRef<number | null>(null);

    const selectedSetId = searchParams.get("questionSetId") ?? questionSetsQuery.data?.[0]?.id ?? "";
    const activeSet = useMemo(
        () => questionSetsQuery.data?.find((item) => item.id === selectedSetId) ?? questionSetsQuery.data?.[0],
        [questionSetsQuery.data, selectedSetId],
    );
    const existingPracticeQuery = useExistingPracticeSessionQuery(activeSet?.id, { enabled: Boolean(activeSet?.id) });
    const startPracticeMutation = useStartPracticeMutation();
    const restartPracticeMutation = useRestartPracticeMutation();
    const questionSets = questionSetsQuery.data ?? [];
    const errorMessage = questionSetsQuery.error instanceof Error ? questionSetsQuery.error.message : "";

    const activeSetIndex = useMemo(() => {
        if (!questionSets.length) return -1;
        const directIndex = questionSets.findIndex((item) => item.id === selectedSetId);
        return directIndex >= 0 ? directIndex : 0;
    }, [questionSets, selectedSetId]);

    const activeSetPosition = activeSetIndex >= 0 ? activeSetIndex + 1 : 0;
    const hasCarouselNavigation = questionSets.length > 1;
    const existingPractice = existingPracticeQuery.data;
    const practiceLoading = startPracticeMutation.isPending || restartPracticeMutation.isPending;

    const carouselCards = useMemo(() => {
        if (!questionSets.length || activeSetIndex < 0) return [];
        const offsets = questionSets.length === 1 ? [0] : [-1, 0, 1];
        return offsets.map((offset) => {
            const index = (activeSetIndex + offset + questionSets.length) % questionSets.length;
            return { offset, item: questionSets[index] };
        });
    }, [activeSetIndex, questionSets]);

    const enteringCard = useMemo(() => {
        if (!questionSets.length || activeSetIndex < 0 || !isAnimating || transitioningDirection === 0 || questionSets.length <= 2) {
            return null;
        }
        const enteringOffset = transitioningDirection === 1 ? 2 : -2;
        const enteringIndex = (activeSetIndex + enteringOffset + questionSets.length) % questionSets.length;
        const enteringSide = transitioningDirection === 1 ? "right" : "left";
        return {
            item: questionSets[enteringIndex],
            enteringSide,
        };
    }, [activeSetIndex, isAnimating, questionSets, transitioningDirection]);

    useEffect(() => {
        return () => {
            if (animationTimerRef.current) {
                window.clearTimeout(animationTimerRef.current);
            }
        };
    }, []);

    const setActiveSetByIndex = (index: number) => {
        if (!questionSets.length) return;
        const nextIndex = (index + questionSets.length) % questionSets.length;
        const nextSet = questionSets[nextIndex];
        if (!nextSet || nextSet.id === selectedSetId) return;
        setSearchParams({ questionSetId: nextSet.id }, { replace: true });
    };

    const handleCarouselMove = (direction: -1 | 1) => {
        if (!hasCarouselNavigation || activeSetIndex < 0 || isAnimating) return;
        if (animationTimerRef.current) {
            window.clearTimeout(animationTimerRef.current);
            animationTimerRef.current = null;
        }
        setTransitioningDirection(direction);
        setIsAnimating(true);
        animationTimerRef.current = window.setTimeout(() => {
            setActiveSetByIndex(activeSetIndex + direction);
            setIsAnimating(false);
            setTransitioningDirection(0);
            animationTimerRef.current = null;
        }, QUIZ_CAROUSEL_ANIMATION_MS);
    };

    const handleStartPractice = async () => {
        if (!activeSet) return;
        const input = {
            qaSetId: activeSet.id,
            mode: practiceMode,
            feedbackMode,
        };
        const detail = existingPractice
            ? await restartPracticeMutation.mutateAsync(input)
            : await startPracticeMutation.mutateAsync(input);
        navigate(`/practice/${detail.session.id}`);
    };

    const handleContinuePractice = () => {
        if (!existingPractice) return;
        navigate(`/practice/${existingPractice.id}`);
    };

    const getCardMotion = (offset: number) => {
        const resting = {
            shift: offset === 0 ? "0%" : offset < 0 ? "-42%" : "42%",
            scale: offset === 0 ? 1 : 0.84,
            opacity: offset === 0 ? 1 : 0.38,
            blur: offset === 0 ? "none" : "saturate(0.78) brightness(0.92)",
            tilt: offset === 0 ? "0deg" : offset < 0 ? "16deg" : "-16deg",
            lift: offset === 0 ? "-10px" : "12px",
            zIndex: offset === 0 ? 3 : 1,
        };

        if (!isAnimating || transitioningDirection === 0) {
            return resting;
        }

        if (transitioningDirection === 1) {
            if (offset === -1) {
                return { shift: "-88%", scale: 0.72, opacity: 0.08, blur: "saturate(0.6) brightness(0.82)", tilt: "20deg", lift: "20px", zIndex: 1 };
            }
            if (offset === 0) {
                return { shift: "-42%", scale: 0.84, opacity: 0.38, blur: "saturate(0.78) brightness(0.92)", tilt: "16deg", lift: "12px", zIndex: 2 };
            }
            if (offset === 1) {
                return { shift: "0%", scale: 1, opacity: 1, blur: "none", tilt: "0deg", lift: "-10px", zIndex: 3 };
            }
        }

        if (offset === 1) {
            return { shift: "88%", scale: 0.72, opacity: 0.08, blur: "saturate(0.6) brightness(0.82)", tilt: "-20deg", lift: "20px", zIndex: 1 };
        }
        if (offset === 0) {
            return { shift: "42%", scale: 0.84, opacity: 0.38, blur: "saturate(0.78) brightness(0.92)", tilt: "-16deg", lift: "12px", zIndex: 2 };
        }
        if (offset === -1) {
            return { shift: "0%", scale: 1, opacity: 1, blur: "none", tilt: "0deg", lift: "-10px", zIndex: 3 };
        }

        return resting;
    };

    return (
        <div className="page-frame">
            <GlassCard className="hero-card hero-card--plain" style={{ width: "min(1180px, 84vw)" }}>
                <div className="quiz-hero">
                    {questionSetsQuery.isLoading ? (
                        <div className="status-card" style={{ marginTop: 12, width: "min(720px, 100%)" }}>
                            <strong>正在加载问答集</strong>
                            <div className="qa-text">从真实接口读取可练习的问答集列表。</div>
                        </div>
                    ) : null}

                    {questionSetsQuery.isError ? (
                        <div className="status-card" style={{ marginTop: 12, width: "min(720px, 100%)" }}>
                            <strong>问答集加载失败</strong>
                            <div className="qa-text">{errorMessage || "请稍后重试。"}</div>
                            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                                <BaseButton variant="soft" type="button" onClick={() => questionSetsQuery.refetch()}>
                                    重试
                                </BaseButton>
                                <LinkButton to="/repository/qa-set" variant="ghost">
                                    去仓库
                                </LinkButton>
                            </div>
                        </div>
                    ) : null}

                    {!questionSetsQuery.isLoading && !questionSetsQuery.isError && !activeSet ? (
                        <section className="quiz-carousel" aria-label="测试集轮播">
                            <div className="quiz-carousel__stage" style={{ overflow: "visible" }}>
                                <button
                                    type="button"
                                    className="quiz-carousel__card quiz-carousel__card--active"
                                    style={{
                                        transform: "translate(-50%, -50%) translateX(0%) translateY(-10px) scale(1) rotateY(0deg)",
                                        opacity: 1,
                                        filter: "none",
                                        zIndex: 3,
                                        placeContent: "center",
                                    }}
                                    onClick={() => navigate("/create")}
                                >
                                    <div className="quiz-focus-card__center">
                                        <div className="quiz-focus-card__title">问答集为空，点击创建</div>
                                    </div>
                                </button>
                            </div>
                        </section>
                    ) : null}

                    {activeSet ? (
                        <>
                            <section className="quiz-carousel" aria-label="测试集轮播">
                                <button
                                    className="quiz-carousel__arrow quiz-carousel__arrow--left"
                                    type="button"
                                    onClick={() => handleCarouselMove(-1)}
                                    aria-label="切换到上一个测试集"
                                    disabled={!hasCarouselNavigation || isAnimating}
                                >
                                    <ChevronLeft size={28} strokeWidth={1.8} />
                                </button>
                                <div className={`quiz-carousel__stage${isAnimating ? " quiz-carousel__stage--animating" : ""}`}>
                                    <div className="quiz-carousel__counter" aria-live="polite">
                                        {activeSetPosition} / {questionSets.length || 0}
                                    </div>
                                    {carouselCards.map(({ item, offset }) => {
                                        const isActiveCard = offset === 0;
                                        const motion = getCardMotion(offset);
                                        const isDuplicateExitingCard = Boolean(
                                            enteringCard
                                            && item.id === enteringCard.item.id
                                            && offset === -transitioningDirection,
                                        );

                                        return (
                                            <button
                                                key={isDuplicateExitingCard ? `exiting-${item.id}-${activeSetIndex}-${transitioningDirection}` : item.id}
                                                type="button"
                                                className={
                                                    isActiveCard
                                                        ? "quiz-carousel__card quiz-carousel__card--active"
                                                        : "quiz-carousel__card quiz-carousel__card--ghost"
                                                }
                                                style={{
                                                    transform: `translate(-50%, -50%) translateX(${motion.shift}) translateY(${motion.lift}) scale(${motion.scale}) rotateY(${motion.tilt})`,
                                                    opacity: motion.opacity,
                                                    filter: motion.blur,
                                                    zIndex: motion.zIndex,
                                                }}
                                                onClick={() => {
                                                    if (isAnimating) return;
                                                    if (isActiveCard) {
                                                        navigate(`/repository/qa-set/${item.id}`);
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
                                    {enteringCard ? (
                                        <button
                                            key={enteringCard.item.id}
                                            type="button"
                                            className={`quiz-carousel__card quiz-carousel__card--ghost quiz-carousel__card--entering quiz-carousel__card--entering-${enteringCard.enteringSide}`}
                                            style={{
                                                zIndex: 1,
                                            }}
                                            onClick={() => {
                                                if (isAnimating) return;
                                                setActiveSetByIndex(questionSets.findIndex((set) => set.id === enteringCard.item.id));
                                            }}
                                            aria-label={`切换到 ${enteringCard.item.title}`}
                                        >
                                            <div className="quiz-focus-card__center">
                                                <div className="quiz-focus-card__title">{enteringCard.item.title}</div>
                                                <div className="quiz-focus-card__meta" style={{ display: "flex", gap: 14, justifyContent: "center", flexWrap: "wrap" }}>
                                                    <span>平均分 {enteringCard.item.averageScore}</span>
                                                    <span>正确率 {enteringCard.item.averageAccuracy}%</span>
                                                    <span>共 {enteringCard.item.questionCount} 题</span>
                                                    <span>已做 {enteringCard.item.practiceCount} 轮</span>
                                                </div>
                                                {parseModuleTags(enteringCard.item.moduleTagsJson).length ? (
                                                    <div className="quiz-focus-card__tags">
                                                        {parseModuleTags(enteringCard.item.moduleTagsJson).map((tag) => (
                                                            <span key={tag} className="quiz-badge">
                                                                {tag}
                                                            </span>
                                                        ))}
                                                    </div>
                                                ) : null}
                                            </div>
                                        </button>
                                    ) : null}
                                </div>
                                <button
                                    className="quiz-carousel__arrow quiz-carousel__arrow--right"
                                    type="button"
                                    onClick={() => handleCarouselMove(1)}
                                    aria-label="切换到下一个测试集"
                                    disabled={!hasCarouselNavigation || isAnimating}
                                >
                                    <ChevronRight size={28} strokeWidth={1.8} />
                                </button>
                            </section>

                        </>
                    ) : null}

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
                                            disabled={!activeSet || practiceLoading}
                                            onClick={handleStartPractice}
                                        >
                                            {existingPractice ? "重新开始" : practiceLoading ? "创建中" : "开始练习"}
                                        </BaseButton>
                                        <BaseButton
                                            variant="ghost"
                                            className="btn--quiz-action"
                                            type="button"
                                            disabled={!existingPractice || practiceLoading}
                                            onClick={handleContinuePractice}
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
                </div>
            </GlassCard>
        </div>
    );
}
