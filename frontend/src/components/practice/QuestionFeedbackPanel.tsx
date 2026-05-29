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

    return (
        <section className="practice-feedback">
            <div className="practice-feedback__head">
                <div className="practice-feedback__head-left">
                    <span className={`practice-result practice-result--${(item.result || "unknown").toLowerCase()}`}>
                        {item.result || "UNKNOWN"}
                    </span>
                    <strong>{`${item.score ?? 0} / 100 分`}</strong>
                    {item.feedbackSummary ? (
                        <span className="practice-feedback__summary">{item.feedbackSummary}</span>
                    ) : null}
                </div>
            </div>

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

            {item.judgeDetail?.improvementAdvice && item.judgeDetail?.commonPitfall ? (
                <div className="practice-feedback__grid">
                    <article>
                        <span>改进建议</span>
                        <p>{item.judgeDetail.improvementAdvice}</p>
                    </article>
                    <article>
                        <span>常见误区</span>
                        <p>{item.judgeDetail.commonPitfall}</p>
                    </article>
                </div>
            ) : (
                <>
                    {item.judgeDetail?.improvementAdvice ? (
                        <article className="practice-feedback__answer">
                            <span>改进建议</span>
                            <p>{item.judgeDetail.improvementAdvice}</p>
                        </article>
                    ) : null}
                    {item.judgeDetail?.commonPitfall ? (
                        <article className="practice-feedback__answer">
                            <span>常见误区</span>
                            <p>{item.judgeDetail.commonPitfall}</p>
                        </article>
                    ) : null}
                </>
            )}

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
        </section>
    );
}
