import { LinkButton } from "@/components/base/button";
import { GlassCard, MetricCard } from "@/components/base/card";

const mockResult = {
    score: 78,
    completedCount: 8,
    totalCount: 10,
    summary: "本轮练习整体表现良好，Redis 模块掌握扎实，但多线程与 JVM 调优模块仍有提升空间。",
    strengths: [
        "Redis 过期策略与持久化机制回答完整",
        "MySQL 索引优化思路清晰",
        "项目经历描述结构良好",
    ],
    gaps: [
        "多线程可见性与有序性理解不够深入",
        "JVM GC 调优参数回答不完整",
        "分布式事务方案需要补充",
    ],
    reviewOrder: ["多线程与并发", "JVM 调优", "分布式事务", "Redis 集群", "MySQL 锁机制"],
    moduleResults: [
        { label: "Redis", score: "92", detail: "核心机制掌握扎实" },
        { label: "MySQL", score: "85", detail: "索引与锁机制良好" },
        { label: "多线程", score: "60", detail: "可见性/有序性需加强" },
        { label: "JVM", score: "65", detail: "GC 调优参数不足" },
        { label: "分布式", score: "68", detail: "事务方案需补充" },
    ],
    evidenceRefs: ["chunk-redis-003", "chunk-mysql-012", "chunk-thread-007"],
};

export function ResultPage() {
    return (
        <div className="page-frame">
            <GlassCard className="hero-card">
                <div className="eyebrow">Practice Result</div>
                <h1 className="hero-title" style={{ fontSize: "clamp(34px, 3vw, 48px)" }}>
                    本轮练习完成
                </h1>
                <p className="hero-copy" style={{ maxWidth: 720 }}>
                    {mockResult.summary}
                </p>
            </GlassCard>

            <section className="result-grid">
                <MetricCard label="总分" value={`${mockResult.score}`} detail="百分制评分" />
                <MetricCard
                    label="完成情况"
                    value={`${mockResult.completedCount} / ${mockResult.totalCount}`}
                    detail="本轮答题覆盖"
                />
                <MetricCard label="题集" value="技术面试问答集" detail="来自当前练习会话" />
                <MetricCard
                    label="下一步"
                    value={mockResult.reviewOrder[0]}
                    detail="先补最薄弱模块，再继续刷题"
                />
            </section>

            <div className="layout-two-col">
                <GlassCard className="panel" style={{ padding: 18 }}>
                    <h3 style={{ margin: "0 0 14px", fontSize: 18 }}>做得好的地方</h3>
                    <div className="result-list">
                        {mockResult.strengths.map((item) => (
                            <div key={item} className="result-item">
                                <strong>{item}</strong>
                            </div>
                        ))}
                    </div>
                </GlassCard>

                <GlassCard className="panel" style={{ padding: 18 }}>
                    <h3 style={{ margin: "0 0 14px", fontSize: 18 }}>需要补的地方</h3>
                    <div className="result-list">
                        {mockResult.gaps.map((item) => (
                            <div key={item} className="result-item">
                                <strong>{item}</strong>
                            </div>
                        ))}
                    </div>
                </GlassCard>
            </div>

            <GlassCard className="panel" style={{ padding: 18 }}>
                <h3 style={{ margin: "0 0 14px", fontSize: 18 }}>建议复习顺序</h3>
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    {mockResult.reviewOrder.map((item, index) => (
                        <div key={item} className="result-item" style={{ minWidth: 140 }}>
                            <strong>{index + 1}. {item}</strong>
                        </div>
                    ))}
                </div>
            </GlassCard>

            <GlassCard className="panel" style={{ padding: 18 }}>
                <h3 style={{ margin: "0 0 14px", fontSize: 18 }}>模块分布</h3>
                <div className="result-list">
                    {mockResult.moduleResults.map((item) => (
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

            <GlassCard className="panel" style={{ padding: 18 }}>
                <h3 style={{ margin: "0 0 14px", fontSize: 18 }}>评分证据引用</h3>
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    {mockResult.evidenceRefs.map((item) => (
                        <div key={item} className="result-item">
                            <strong>{item}</strong>
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

            <div className="qa-feedback" style={{ width: "min(480px, 100%)", margin: "0 auto" }}>
                <strong>评分链路尚未接入</strong>
                <div className="qa-text">以上为静态预览数据。评分 Agent、反馈汇总与复习建议将在后续版本接入真实链路。</div>
            </div>
        </div>
    );
}
