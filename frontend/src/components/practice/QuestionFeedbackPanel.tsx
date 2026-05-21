import { ChevronDown } from "lucide-react";
import { useState } from "react";
import type { PracticeFlowItem } from "@/lib/api/types";

type QuestionFeedbackPanelProps = {
    item: PracticeFlowItem;
};

export function QuestionFeedbackPanel({ item }: QuestionFeedbackPanelProps) {
    const [answerOpen, setAnswerOpen] = useState(false);
    const hasFeedback = item.status === "SUBMITTED" || Boolean(item.result);

    if (!hasFeedback) {
        return null;
    }

    const missingPoints = item.judgeDetail?.missingPoints ?? [];
    const wrongPoints = item.judgeDetail?.wrongPoints ?? [];

    return (
        <section className="practice-feedback">
            <div className="practice-feedback__head">
                <span className={`practice-result practice-result--${(item.result || "unknown").toLowerCase()}`}>
                    {item.result || "UNKNOWN"}
                </span>
                <strong>{item.score == null ? "未评分" : `${item.score} 分`}</strong>
            </div>

            {item.feedbackSummary ? <p>{item.feedbackSummary}</p> : null}

            {item.hintDetail ? (
                <div className="practice-feedback__grid">
                    {item.hintDetail.memoryTip ? (
                        <article>
                            <span>记忆提示</span>
                            <p>{item.hintDetail.memoryTip}</p>
                        </article>
                    ) : null}
                    {item.hintDetail.encouragement ? (
                        <article>
                            <span>鼓励</span>
                            <p>{item.hintDetail.encouragement}</p>
                        </article>
                    ) : null}
                </div>
            ) : null}

            {item.judgeDetail ? (
                <div className="practice-feedback__grid">
                    {missingPoints.length ? (
                        <article>
                            <span>缺失点</span>
                            <ul>
                                {missingPoints.map((point) => <li key={point}>{point}</li>)}
                            </ul>
                        </article>
                    ) : null}
                    {wrongPoints.length ? (
                        <article>
                            <span>错误点</span>
                            <ul>
                                {wrongPoints.map((point) => <li key={point}>{point}</li>)}
                            </ul>
                        </article>
                    ) : null}
                    {item.judgeDetail.improvementAdvice ? (
                        <article>
                            <span>改进建议</span>
                            <p>{item.judgeDetail.improvementAdvice}</p>
                        </article>
                    ) : null}
                </div>
            ) : null}

            {item.judgeDetail?.betterAnswer || item.standardAnswer ? (
                <button className="practice-feedback__answer-toggle" type="button" onClick={() => setAnswerOpen((value) => !value)}>
                    <span>{answerOpen ? "收起参考回答" : "查看参考回答"}</span>
                    <ChevronDown size={16} className={answerOpen ? "practice-feedback__answer-icon practice-feedback__answer-icon--open" : "practice-feedback__answer-icon"} />
                </button>
            ) : null}

            {answerOpen ? (
                <div className="practice-feedback__answer">
                    {item.judgeDetail?.betterAnswer || item.standardAnswer}
                </div>
            ) : null}
        </section>
    );
}
