import { useState } from "react";
import { Link } from "react-router";
import { ArrowLeft } from "lucide-react";
import { BaseButton } from "@/components/base/button";
import { Chip, Tag } from "@/components/base/tag";
import { Field, TextArea } from "@/components/base/field";
import { GlassCard } from "@/components/base/card";
import { cn } from "@/lib/cn";
import { useGlobalErrorDialog } from "@/lib/error/ErrorDialogProvider";

const mockQuestion = {
    question: "请描述 Redis 的过期键删除策略，并说明惰性删除和定期删除各自解决了什么问题？",
    tags: ["Redis", "缓存策略"],
    difficulty: "MEDIUM",
    hint: "从键过期的作用讲起，再分别说明惰性删除和定期删除的机制与互补关系。",
    scoringRubric: {
        answerStructure: "先从职责、访问模型、边界和取舍切入，再补项目例子。",
        keyPoints: ["惰性删除", "定期删除", "两者互补", "内存淘汰区别"],
    },
};

export function QAPage() {
    const [supportHintVisible, setSupportHintVisible] = useState(false);
    const [revealed, setRevealed] = useState(false);
    const [answerText, setAnswerText] = useState("");
    const [submitted, setSubmitted] = useState(false);
    const { showErrorDialog, showUnimplemented } = useGlobalErrorDialog();

    const handleSubmit = () => {
        if (answerText.trim().length < 12) {
            showErrorDialog({
                title: "作答内容过短",
                message: "请至少写一点回答（最低 12 字）。",
            });
            return;
        }
        showUnimplemented("提交答案链路尚未接入后端。");
    };

    const handleMarkUnknown = () => {
        setRevealed(true);
        setAnswerText("");
        showUnimplemented("“不会”标记链路尚未接入后端。");
    };

    return (
        <div className="qa-layout">
            <header className="sidebar__split" style={{ marginBottom: 16 }}>
                <Link to="/quiz" className="btn btn--link" style={{ paddingLeft: 0 }}>
                    <ArrowLeft size={14} />
                    返回测试页
                </Link>
                <div className="page-copy" style={{ fontSize: 12 }}>
                    Session 1 / 10
                </div>
            </header>

            <GlassCard className="qa-card">
                <div className="qa-head">
                    <section style={{ maxWidth: 760 }}>
                        <div className="eyebrow">Question 1</div>
                        <h1 className="qa-question">{mockQuestion.question}</h1>
                    </section>

                    <aside className="qa-aside">
                        <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "flex-end", gap: 8 }}>
                            {mockQuestion.tags.map((tag) => (
                                <Tag key={tag}>{tag}</Tag>
                            ))}
                            <Tag>{mockQuestion.difficulty}</Tag>
                        </div>
                        <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "flex-end", gap: 8 }}>
                            <BaseButton
                                variant="soft"
                                type="button"
                                onClick={() => setSupportHintVisible((value) => !value)}
                            >
                                提示
                            </BaseButton>
                            <BaseButton
                                variant="outline"
                                type="button"
                                disabled={submitted}
                                onClick={handleMarkUnknown}
                            >
                                不会
                            </BaseButton>
                        </div>
                    </aside>
                </div>

                <p className="qa-text">{mockQuestion.hint}</p>
                {supportHintVisible ? (
                    <div className="status-card" style={{ marginBottom: 18 }}>
                        <strong>提示</strong>
                        <div className="qa-text">
                            {mockQuestion.scoringRubric.answerStructure}
                        </div>
                        {mockQuestion.scoringRubric.keyPoints.length ? (
                            <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 12 }}>
                                {mockQuestion.scoringRubric.keyPoints.map((point) => (
                                    <Tag key={point}>{point}</Tag>
                                ))}
                            </div>
                        ) : null}
                    </div>
                ) : null}

                <div className="qa-editor">
                    <Field label="作答区">
                        <TextArea
                            className="qa-answer"
                            placeholder="在这里组织你的回答，从职责、访问模型和持久化边界开始。"
                            disabled={submitted}
                            value={answerText}
                            onChange={(e) => setAnswerText(e.target.value)}
                        />
                    </Field>

                    {revealed ? (
                        <div className="status-card">
                            <div className="sidebar__split">
                                <strong>反馈</strong>
                                <Chip>已提交</Chip>
                            </div>
                            <div className="qa-text">
                                练习链路尚未接入，反馈、评分功能将在后续版本开放。
                            </div>
                        </div>
                    ) : null}

                    <div className="sidebar__split">
                        <div style={{ display: "flex", flexWrap: "wrap", gap: 12 }}>
                            <button
                                className={cn("btn btn--link")}
                                type="button"
                                onClick={() => setSupportHintVisible((value) => !value)}
                            >
                                {supportHintVisible ? "收起提示" : "查看提示"}
                            </button>
                            <button
                                className="btn btn--link"
                                type="button"
                                disabled={submitted}
                                onClick={() => document.querySelector<HTMLTextAreaElement>(".qa-answer")?.focus()}
                            >
                                聚焦作答
                            </button>
                        </div>
                        <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                            <BaseButton
                                variant="outline"
                                type="button"
                                disabled={submitted}
                                onClick={() => showUnimplemented("下一题功能尚未接入后端。")}
                            >
                                下一题
                            </BaseButton>
                            <BaseButton
                                variant="soft"
                                type="button"
                                onClick={() => showUnimplemented("结束练习功能尚未接入后端。")}
                            >
                                结束练习
                            </BaseButton>
                            <BaseButton
                                variant="primary"
                                type="button"
                                disabled={submitted}
                                onClick={handleSubmit}
                            >
                                {submitted ? "已提交" : "提交答案"}
                            </BaseButton>
                        </div>
                    </div>
                </div>
            </GlassCard>
        </div>
    );
}
