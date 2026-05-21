import { ArrowLeft, ArrowRight, CircleHelp, SendHorizontal } from "lucide-react";
import type { PracticeFlowItem } from "@/lib/api/types";
import { BaseButton } from "@/components/base/button";
import { Field, TextArea } from "@/components/base/field";
import { Tag } from "@/components/base/tag";
import { QuestionFeedbackPanel } from "./QuestionFeedbackPanel";

type QuestionWorkspaceProps = {
    item: PracticeFlowItem;
    index: number;
    total: number;
    answer: string;
    saveStatus: string;
    submitting?: boolean;
    readonly?: boolean;
    onAnswerChange: (value: string) => void;
    onPrev: () => void;
    onNext: () => void;
    onUnknown: () => void;
    onSubmit: () => void;
};

export function QuestionWorkspace({
    item,
    index,
    total,
    answer,
    saveStatus,
    submitting,
    readonly,
    onAnswerChange,
    onPrev,
    onNext,
    onUnknown,
    onSubmit,
}: QuestionWorkspaceProps) {
    const submitted = item.status === "SUBMITTED";
    return (
        <main className="question-workspace">
            <div className="question-workspace__meta">
                <span>Question {index + 1} / {total}</span>
                <span>{saveStatus}</span>
            </div>

            <section className="question-workspace__question">
                <h1>{item.question}</h1>
                <div className="question-workspace__tags">
                    {item.moduleTag ? <Tag>{item.moduleTag}</Tag> : null}
                    {item.difficulty ? <Tag>{item.difficulty}</Tag> : null}
                    {item.keywords ? item.keywords.split(",").slice(0, 4).map((keyword) => (
                        <Tag key={keyword.trim()}>{keyword.trim()}</Tag>
                    )) : null}
                </div>
            </section>

            <Field label="作答区" hint={readonly ? "本轮练习已完成，答案不可修改。" : "系统会自动保存草稿。"}>
                <TextArea
                    className="practice-answer"
                    value={answer}
                    disabled={readonly || submitted}
                    onChange={(event) => onAnswerChange(event.target.value)}
                    placeholder="在这里组织你的回答，尽量覆盖关键词、机制、边界和实际场景。"
                />
            </Field>

            <div className="practice-action-bar">
                <BaseButton variant="outline" leadingIcon={<ArrowLeft size={16} />} onClick={onPrev} disabled={index <= 0}>
                    上一题
                </BaseButton>
                <BaseButton variant="outline" leadingIcon={<ArrowRight size={16} />} onClick={onNext} disabled={index >= total - 1}>
                    下一题
                </BaseButton>
                <BaseButton variant="soft" leadingIcon={<CircleHelp size={16} />} onClick={onUnknown} disabled={readonly || submitted || submitting}>
                    不会
                </BaseButton>
                <BaseButton variant="primary" leadingIcon={<SendHorizontal size={16} />} onClick={onSubmit} disabled={readonly || submitted || submitting}>
                    {submitted ? "已提交" : submitting ? "提交中" : "提交本题"}
                </BaseButton>
            </div>

            <QuestionFeedbackPanel item={item} />
        </main>
    );
}
