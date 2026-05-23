import { BaseButton } from "@/components/base/button";

type AssessmentGeneratingPanelProps = {
    failed?: boolean;
    onRetry?: () => void;
};

export function AssessmentGeneratingPanel({ failed, onRetry }: AssessmentGeneratingPanelProps) {
    return (
        <div className="assessment-generating" role="status" aria-live="polite">
            <div className="assessment-generating__panel">
                <span className="assessment-generating__eyebrow">练习报告</span>
                <h1>{failed ? "评估生成失败" : "正在生成评估"}</h1>
                <p>
                    {failed
                        ? "本轮答案已经保留，可以重新提交生成评估。"
                        : "系统正在整理本轮作答、单题反馈和整轮分析。"}
                </p>
                {!failed ? (
                    <div className="assessment-generating__steps">
                        <span>整理作答</span>
                        <span>生成单题反馈</span>
                        <span>汇总整轮分析</span>
                    </div>
                ) : null}
                {failed && onRetry ? (
                    <BaseButton variant="primary" type="button" onClick={onRetry}>
                        重试提交
                    </BaseButton>
                ) : null}
            </div>
        </div>
    );
}
