import type { PracticeFlowItem } from "@/lib/api/types";

type QuestionFeedbackPanelProps = {
    item: PracticeFlowItem;
};

export function QuestionFeedbackPanel({ item }: QuestionFeedbackPanelProps) {
    const hasFeedback = item.status === "SUBMITTED" || Boolean(item.result);

    if (!hasFeedback) {
        return null;
    }

    const missingPoints = item.judgeDetail?.missingPoints ?? [];
    const wrongPoints = item.judgeDetail?.wrongPoints ?? [];
    const referenceAnswer = item.judgeDetail?.betterAnswer || item.standardAnswer;

    return (
        <section className="practice-feedback">
            <div className="practice-feedback__head">
                <div className="practice-feedback__head-left">
                    <span className={`practice-result practice-result--${(item.result || "unknown").toLowerCase()}`}>
                        {item.result || "UNKNOWN"}
                    </span>
                    <strong>{`${item.score ?? 0} / 100 分`}</strong>
                </div>
            </div>

            {referenceAnswer ? (
                <article className="practice-feedback__answer">
                    <span>参考答案</span>
                    <p>{referenceAnswer}</p>
                </article>
            ) : null}

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
        </section>
    );
}
