import { useParams } from "react-router";
import { LinkButton } from "@/components/base/button";
import { GlassCard, MetricCard } from "@/components/base/card";
import { usePracticeDetailQuery } from "@/lib/api/hooks";

function pointLabel(point: { title?: string; analysis?: string; moduleTag?: string }) {
    return point.title || point.analysis || point.moduleTag || "未命名要点";
}

export function ResultPage() {
    const { sessionId = "" } = useParams();
    const detailQuery = usePracticeDetailQuery(sessionId);
    const detail = detailQuery.data;
    const session = detail?.session;
    const items = detail?.items ?? [];
    const assessDetail = session?.assessDetail;
    const strengths = assessDetail?.strengths ?? [];
    const weakPoints = assessDetail?.weaknesses ?? [];
    const reviewSuggestions = assessDetail?.reviewSuggestions ?? [];

    if (detailQuery.isLoading) {
        return <div className="page-frame"><div className="status-card">正在读取练习结果...</div></div>;
    }

    if (detailQuery.isError || !session) {
        return (
            <div className="page-frame">
                <div className="status-card">
                    <strong>结果加载失败</strong>
                    <div className="qa-text">请返回测试页重新进入。</div>
                </div>
                <LinkButton to="/quiz" variant="primary">返回测试页</LinkButton>
            </div>
        );
    }

    return (
        <div className="page-frame">
            <GlassCard className="hero-card">
                <div className="eyebrow">Practice Result</div>
                <h1 className="hero-title" style={{ fontSize: "clamp(34px, 3vw, 48px)" }}>
                    本轮练习完成
                </h1>
                <p className="hero-copy" style={{ maxWidth: 720 }}>
                    {session.summary || assessDetail?.overallComment || "本轮练习已完成，系统已保存你的作答记录。"}
                </p>
            </GlassCard>

            <section className="result-grid">
                <MetricCard label="总分" value={session.score == null ? "-" : `${session.score}`} detail="百分制评分" />
                <MetricCard label="完成情况" value={`${session.answeredCount} / ${session.totalQuestions || items.length}`} detail="本轮答题覆盖" />
                <MetricCard label="题集" value={session.qaSetTitle || "当前题集"} detail="来自当前练习会话" />
                <MetricCard label="达标率" value={session.accuracy == null ? "-" : `${session.accuracy}%`} detail="评估 Agent 汇总" />
            </section>

            <div className="layout-two-col">
                <GlassCard className="panel" style={{ padding: 18 }}>
                    <h3 style={{ margin: "0 0 14px", fontSize: 18 }}>做得好的地方</h3>
                    <div className="result-list">
                        {strengths.length ? strengths.map((item) => (
                            <div key={`${pointLabel(item)}-${item.analysis}`} className="result-item">
                                <strong>{pointLabel(item)}</strong>
                                {item.analysis ? <span>{item.analysis}</span> : null}
                            </div>
                        )) : <div className="qa-text">暂无优势明细。</div>}
                    </div>
                </GlassCard>

                <GlassCard className="panel" style={{ padding: 18 }}>
                    <h3 style={{ margin: "0 0 14px", fontSize: 18 }}>需要补的地方</h3>
                    <div className="result-list">
                        {weakPoints.length ? weakPoints.map((item) => (
                            <div key={`${pointLabel(item)}-${item.analysis}`} className="result-item">
                                <strong>{pointLabel(item)}</strong>
                                {item.analysis ? <span>{item.analysis}</span> : null}
                            </div>
                        )) : <div className="qa-text">暂无薄弱点明细。</div>}
                    </div>
                </GlassCard>
            </div>

            <GlassCard className="panel" style={{ padding: 18 }}>
                <h3 style={{ margin: "0 0 14px", fontSize: 18 }}>建议复习顺序</h3>
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    {reviewSuggestions.length ? reviewSuggestions.map((item, index) => (
                        <div key={item} className="result-item" style={{ minWidth: 140 }}>
                            <strong>{index + 1}. {item}</strong>
                        </div>
                    )) : <div className="qa-text">暂无复习建议。</div>}
                </div>
            </GlassCard>

            <GlassCard className="panel" style={{ padding: 18 }}>
                <h3 style={{ margin: "0 0 14px", fontSize: 18 }}>题目结果</h3>
                <div className="result-list">
                    {items.map((item, index) => (
                        <div key={item.sessionItemId} className="result-item">
                            <div>
                                <strong>{index + 1}. {item.question}</strong>
                                <span>{item.feedbackSummary || item.status}</span>
                            </div>
                            <strong>{item.result || item.status}</strong>
                        </div>
                    ))}
                </div>
            </GlassCard>

            <div style={{ display: "flex", justifyContent: "center", gap: 12, flexWrap: "wrap" }}>
                <LinkButton to="/repository/qa-set" variant="ghost">
                    回仓库
                </LinkButton>
                <LinkButton to="/quiz" variant="primary">
                    继续练习
                </LinkButton>
            </div>
        </div>
    );
}
