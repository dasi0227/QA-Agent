import { useParams } from "react-router";
import { LinkButton } from "@/components/base/button";
import { GlassCard, MetricCard } from "@/components/base/card";
import { usePracticeResultQuery, usePracticeSessionQuery } from "@/lib/api/hooks";

export function ResultPage() {
    const params = useParams();
    const sessionId = params.sessionId ?? "";
    const resultQuery = usePracticeResultQuery(sessionId || undefined);
    const sessionQuery = usePracticeSessionQuery(sessionId || undefined);

    const questionSetId = resultQuery.data?.questionSetId || sessionQuery.data?.questionSetId || "";
    const scoringDetail = resultQuery.data?.detail;
    const errorMessage = resultQuery.error instanceof Error ? resultQuery.error.message : "";

    return (
        <div className="page-frame">
            {resultQuery.isLoading ? (
                <GlassCard className="hero-card">
                    <div className="qa-feedback">
                        <strong>正在加载结果</strong>
                        <div className="qa-text">从真实接口读取本轮练习评分与总结。</div>
                    </div>
                </GlassCard>
            ) : null}

            {resultQuery.isError ? (
                <GlassCard className="hero-card">
                    <div className="qa-feedback">
                        <strong>结果加载失败</strong>
                        <div className="qa-text">{errorMessage || "请稍后重试。"}</div>
                        <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                            <LinkButton to="/quiz" variant="soft">
                                返回测试页
                            </LinkButton>
                            <LinkButton to={`/practice/${sessionId}`} variant="ghost">
                                回到会话
                            </LinkButton>
                        </div>
                    </div>
                </GlassCard>
            ) : null}

            {resultQuery.data ? (
                <>
                    <GlassCard className="hero-card">
                        <div className="eyebrow">Practice Result</div>
                        <h1 className="hero-title" style={{ fontSize: "clamp(34px, 3vw, 48px)" }}>
                            本轮练习完成
                        </h1>
                        <p className="hero-copy" style={{ maxWidth: 720 }}>
                            {scoringDetail?.summary || resultQuery.data.summary}
                        </p>
                    </GlassCard>

                    <section className="result-grid">
                        <MetricCard label="总分" value={`${resultQuery.data.score}`} detail="百分制评分" />
                        <MetricCard
                            label="完成情况"
                            value={`${resultQuery.data.completedCount ?? 0} / ${resultQuery.data.totalCount ?? 0}`}
                            detail="本轮答题覆盖"
                        />
                        <MetricCard label="题集" value={questionSetId || "暂无"} detail="来自当前练习会话" />
                        <MetricCard
                            label="下一步"
                            value={scoringDetail?.reviewOrder?.[0] || "复盘"}
                            detail="先补最薄弱模块，再继续刷题"
                        />
                    </section>

                    <div className="layout-two-col">
                        <GlassCard className="panel" style={{ padding: 18 }}>
                            <h3 style={{ margin: "0 0 14px", fontSize: 18 }}>做得好的地方</h3>
                            <div className="result-list">
                                {resultQuery.data.strengths.map((item) => (
                                    <div key={item} className="result-item">
                                        <strong>{item}</strong>
                                    </div>
                                ))}
                            </div>
                        </GlassCard>

                        <GlassCard className="panel" style={{ padding: 18 }}>
                            <h3 style={{ margin: "0 0 14px", fontSize: 18 }}>需要补的地方</h3>
                            <div className="result-list">
                                {resultQuery.data.gaps.map((item) => (
                                    <div key={item} className="result-item">
                                        <strong>{item}</strong>
                                    </div>
                                ))}
                            </div>
                        </GlassCard>
                    </div>

                    {resultQuery.data.reviewOrder?.length ? (
                        <GlassCard className="panel" style={{ padding: 18 }}>
                            <h3 style={{ margin: "0 0 14px", fontSize: 18 }}>建议复习顺序</h3>
                            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                                {resultQuery.data.reviewOrder.map((item, index) => (
                                    <div key={item} className="result-item" style={{ minWidth: 140 }}>
                                        <strong>{index + 1}. {item}</strong>
                                    </div>
                                ))}
                            </div>
                        </GlassCard>
                    ) : null}

                    <GlassCard className="panel" style={{ padding: 18 }}>
                        <h3 style={{ margin: "0 0 14px", fontSize: 18 }}>模块分布</h3>
                        <div className="result-list">
                            {resultQuery.data.moduleResults.map((item) => (
                                <div key={item.label} className="result-item">
                                    <div>
                                        <strong>{item.label}</strong>
                                        <span>{item.detail}</span>
                                    </div>
                                    <strong>{item.score}</strong>
                                </div>
                            ))}
                        </div>
                    </GlassCard>

                    {resultQuery.data.evidenceRefs?.length ? (
                        <GlassCard className="panel" style={{ padding: 18 }}>
                            <h3 style={{ margin: "0 0 14px", fontSize: 18 }}>评分证据引用</h3>
                            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                                {resultQuery.data.evidenceRefs.map((item) => (
                                    <div key={item} className="result-item">
                                        <strong>{item}</strong>
                                    </div>
                                ))}
                            </div>
                        </GlassCard>
                    ) : null}

                    <div style={{ display: "flex", justifyContent: "center", gap: 12, flexWrap: "wrap" }}>
                        <LinkButton to={questionSetId ? `/repository/${questionSetId}` : "/repository"} variant="ghost">
                            回仓库
                        </LinkButton>
                        <LinkButton to="/quiz" variant="primary">
                            继续练习
                        </LinkButton>
                    </div>
                </>
            ) : null}
        </div>
    );
}
