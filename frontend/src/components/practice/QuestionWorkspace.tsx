import { ArrowLeft, ArrowRight, CircleHelp, Save, SendHorizontal } from "lucide-react";
import { Eye, EyeOff, Lightbulb } from "lucide-react";
import { useMemo, useState } from "react";
import type { PracticeFeedbackMode, PracticeFlowItem } from "@/lib/api/types";
import { BaseButton } from "@/components/base/button";
import { TextArea } from "@/components/base/field";
import { Tag } from "@/components/base/tag";
import { MarkdownRenderer } from "@/lib/markdown";
import { QuestionFeedbackPanel } from "./QuestionFeedbackPanel";
type QuestionWorkspaceProps = {
    item: PracticeFlowItem;
    index: number;
    total: number;
    answer: string;
    feedbackMode: PracticeFeedbackMode;
    showFeedback?: boolean;
    submitting?: boolean;
    readonly?: boolean;
    onAnswerChange: (value: string) => void;
    onPrev: () => void;
    onNext: () => void;
    onUnknown: () => void;
    onSubmit: () => void;
    onSaveAndNext: () => void;
};

export function QuestionWorkspace({
    item,
    index,
    total,
    answer,
    feedbackMode,
    showFeedback,
    submitting,
    readonly,
    onAnswerChange,
    onPrev,
    onNext,
    onUnknown,
    onSubmit,
    onSaveAndNext,
}: QuestionWorkspaceProps) {
    const submitted = item.status === "SUBMITTED";
    const afterAll = feedbackMode === "AFTER_ALL";
    const [keywordsOpen, setKeywordsOpen] = useState(false);
    const [hintOpen, setHintOpen] = useState(false);
    const moduleLabel = useMemo(() => {
        const value = item.moduleTag?.trim();
        if (!value) return "";
        return value.split(/[,，、|]/).map((part) => part.trim()).filter(Boolean).join(" · ");
    }, [item.moduleTag]);
    return (
        <main className="question-workspace">
            <section className="question-workspace__question">
                <h1>{item.question}</h1>
                {(item.moduleTag || item.difficulty || item.keywords || item.hint) ? (
                    <div className="question-workspace__meta">
                        {item.difficulty ? <Tag>{item.difficulty}</Tag> : null}
                        {moduleLabel ? <Tag className="question-workspace__module-tag">{moduleLabel}</Tag> : null}
                        {item.keywords ? (
                            <div className="question-workspace__keyword-wrap">
                                <button
                                    type="button"
                                    className={`question-workspace__keyword-toggle${keywordsOpen ? " question-workspace__keyword-toggle--open" : ""}`}
                                    onClick={() => setKeywordsOpen((value) => !value)}
                                >
                                    {keywordsOpen ? <Eye size={16} /> : <EyeOff size={16} />}
                                    <span>关键词</span>
                                </button>
                                {keywordsOpen ? (
                                    <div className="question-workspace__keyword-popover">
                                        <div className="question-workspace__keyword-chips">
                                            {item.keywords.split(",").slice(0, 4).map((keyword) => (
                                                <Tag key={keyword.trim()}>{keyword.trim()}</Tag>
                                            ))}
                                        </div>
                                    </div>
                                ) : null}
                            </div>
                        ) : null}
                        {item.hint ? (
                            <button
                                type="button"
                                className={`question-workspace__pre-hint${hintOpen ? " question-workspace__pre-hint--open" : ""}`}
                                onClick={() => setHintOpen((value) => !value)}
                                aria-expanded={hintOpen}
                            >
                                <Lightbulb size={16} />
                                <span>{hintOpen ? item.hint : "提示"}</span>
                            </button>
                        ) : null}
                    </div>
                ) : null}
            </section>

            <div className="question-workspace__answer">
                <TextArea
                    className="practice-answer"
                    value={answer}
                    disabled={readonly || submitted}
                    onChange={(event) => onAnswerChange(event.target.value)}
                    placeholder="在这里组织你的回答"
                />
            </div>

            <div className="practice-action-bar">
                <BaseButton variant="outline" leadingIcon={<ArrowLeft size={16} />} onClick={onPrev} disabled={index <= 0}>
                    上一题
                </BaseButton>
                <BaseButton variant="outline" leadingIcon={<ArrowRight size={16} />} onClick={onNext} disabled={index >= total - 1}>
                    下一题
                </BaseButton>
                <BaseButton variant="soft" leadingIcon={<CircleHelp size={16} />} onClick={onUnknown} disabled={readonly || submitted || submitting}>
                    标记不会
                </BaseButton>
                {afterAll ? (
                    <BaseButton variant="primary" leadingIcon={<Save size={16} />} onClick={onSaveAndNext} disabled={readonly || submitting}>
                        {submitting ? "保存中" : "保存并下一题"}
                    </BaseButton>
                ) : (
                    <BaseButton variant="primary" leadingIcon={<SendHorizontal size={16} />} onClick={onSubmit} disabled={readonly || submitted || submitting}>
                        {submitted ? "已提交" : submitting ? "判题中" : "提交本题"}
                    </BaseButton>
                )}
            </div>

            {showFeedback && item.standardAnswer ? (
                <section className="review-answer-block">
                    <span>参考答案</span>
                    <MarkdownRenderer content={item.standardAnswer} />
                </section>
            ) : null}

            {showFeedback ? <QuestionFeedbackPanel item={item} /> : null}
        </main>
    );
}
