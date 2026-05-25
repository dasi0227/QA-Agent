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

            {item.judgeDetail?.improvementAdvice ? (
                <article className="practice-feedback__answer">
                    <span>改进建议</span>
                    <p>{item.judgeDetail.improvementAdvice}</p>
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
                    <article>
                        <span>缺失点</span>
                        {missingPoints.length ? (
                            <ul>
                                {missingPoints.map((point) => <li key={point}>{point}</li>)}
                            </ul>
                        ) : (
                            <p>无</p>
                        )}
                    </article>
                    <article>
                        <span>错误点</span>
                        {wrongPoints.length ? (
                            <ul>
                                {wrongPoints.map((point) => <li key={point}>{point}</li>)}
                            </ul>
                        ) : (
                            <p>无</p>
                        )}
                    </article>
                </div>
            ) : null}
        </section>
    );
}
