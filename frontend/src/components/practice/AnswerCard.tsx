import { LogOut, SendHorizontal } from "lucide-react";
import type { PracticeFeedbackMode, PracticeFlowItem } from "@/lib/api/types";

type AnswerCardProps = {
    items: PracticeFlowItem[];
    currentIndex: number;
    feedbackMode: PracticeFeedbackMode;
    onJump: (index: number) => void;
    onSubmitSession: () => void;
    onAbandon: () => void;
    submitting?: boolean;
};

function statusClass(item: PracticeFlowItem, feedbackMode: PracticeFeedbackMode) {
    if (item.unknown || item.status === "UNKNOWN" || item.result === "UNKNOWN") {
        return "unknown";
    }
    if (feedbackMode === "AFTER_ALL" && !item.result) {
        if (item.status === "DRAFT" || item.status === "SUBMITTED") return "answered";
        return "unanswered";
    }
    if (item.result === "PERFECT" || item.result === "CORRECT" || item.result === "DEFICIENT") {
        return "correct";
    }
    if (item.result === "WRONG") {
        return "wrong";
    }
    if (item.status === "DRAFT" || item.status === "SUBMITTED") {
        return "answered";
    }
    return "unanswered";
}

export function AnswerCard({
    items,
    currentIndex,
    feedbackMode,
    onJump,
    onSubmitSession,
    onAbandon,
    submitting,
}: AnswerCardProps) {
    return (
        <aside className="answer-card">
            <div className="answer-card__head">
                <div>
                    <span>答题卡</span>
                    <strong>{currentIndex + 1} / {items.length}</strong>
                </div>
            </div>

            <div className="answer-card__legend">
                <span><i className="answer-dot answer-dot--answered" />已做</span>
                <span><i className="answer-dot answer-dot--unknown" />不会</span>
                <span><i className="answer-dot answer-dot--unanswered" />未做</span>
            </div>

            <div className="answer-card__grid">
                {items.map((item, index) => (
                    <button
                        key={item.sessionItemId}
                        type="button"
                        className={`answer-card__number answer-card__number--${statusClass(item, feedbackMode)}${index === currentIndex ? " answer-card__number--current" : ""}`}
                        onClick={() => onJump(index)}
                        aria-label={`第 ${index + 1} 题`}
                    >
                        {index + 1}
                    </button>
                ))}
            </div>

            <div className="answer-card__footer-actions">
                <button type="button" className="answer-card__submit" onClick={onSubmitSession} disabled={submitting}>
                    <SendHorizontal size={16} />
                    <span>{submitting ? "判题中" : "提交整轮"}</span>
                </button>
                <button type="button" className="answer-card__abandon" onClick={onAbandon}>
                    <LogOut size={15} />
                    <span>放弃该轮</span>
                </button>
            </div>
        </aside>
    );
}
