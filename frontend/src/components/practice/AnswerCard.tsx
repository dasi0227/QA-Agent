import { PanelRightClose, PanelRightOpen } from "lucide-react";
import type { PracticeFeedbackMode, PracticeFlowItem } from "@/lib/api/types";

type AnswerCardProps = {
    items: PracticeFlowItem[];
    currentIndex: number;
    feedbackMode: PracticeFeedbackMode;
    collapsed: boolean;
    onToggle: () => void;
    onJump: (index: number) => void;
    onSubmitSession: () => void;
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
    collapsed,
    onToggle,
    onJump,
    onSubmitSession,
    submitting,
}: AnswerCardProps) {
    if (collapsed) {
        return (
            <aside className="answer-card answer-card--collapsed">
                <button type="button" className="answer-card__toggle" onClick={onToggle} aria-label="展开答题卡">
                    <PanelRightOpen size={18} />
                </button>
                <strong>{currentIndex + 1}</strong>
                <span>/ {items.length}</span>
            </aside>
        );
    }

    return (
        <aside className="answer-card">
            <div className="answer-card__head">
                <div>
                    <span>答题卡</span>
                    <strong>{currentIndex + 1} / {items.length}</strong>
                </div>
                <button type="button" className="answer-card__toggle" onClick={onToggle} aria-label="折叠答题卡">
                    <PanelRightClose size={18} />
                </button>
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

            <button type="button" className="answer-card__submit" onClick={onSubmitSession} disabled={submitting}>
                {submitting ? "提交中" : "提交整轮"}
            </button>
        </aside>
    );
}
