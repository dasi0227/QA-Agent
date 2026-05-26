import type { CSSProperties } from "react";
import { useNavigate, useParams } from "react-router";
import { BarChart, Bar, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from "recharts";
import { BaseButton, LinkButton } from "@/components/base/button";
import { usePracticeDetailQuery, usePracticeHistoryQuery, useRestartPracticeMutation } from "@/lib/api/hooks";
import type { AssessPoint, PracticeFlowItem } from "@/lib/api/types";

const CHART_COLORS = {
    perfect: "#c8853b",
    correct: "#4f8a67",
    deficient: "#d7b957",
    wrong: "#b55a4c",
    unknown: "#7b8ca8",
    module: "#b7a27d",
} as const;

function pointTitle(point: AssessPoint) {
    return point.title || point.moduleTag || "未命名要点";
}

function pointBody(point: AssessPoint) {
    return point.analysis || "暂无详细分析。";
}

function resultMeta(item: Pick<PracticeFlowItem, "result" | "status" | "unknown">) {
    const raw = item.unknown ? "UNKNOWN" : (item.result || item.status || "").toUpperCase();
    if (raw === "PERFECT") {
        return { label: "完美", tone: "perfect" };
    }
    if (raw === "CORRECT") {
        return { label: "正确", tone: "correct" };
    }
    if (raw === "DEFICIENT") {
        return { label: "缺漏", tone: "deficient" };
    }
    if (raw === "WRONG") {
        return { label: "错误", tone: "wrong" };
    }
    if (raw === "UNKNOWN") {
        return { label: "不会", tone: "unknown" };
    }
    if (raw === "SUBMITTED") {
        return { label: "已提交", tone: "submitted" };
    }
    return { label: raw || "未作答", tone: "pending" };
}

function feedbackModeLabel(mode?: string) {
    if (mode === "AFTER_ALL") {
        return "整轮反馈";
    }
    if (mode === "ITEM_BY_ITEM") {
        return "逐题反馈";
    }
    return mode || "练习模式";
}

function formatDateTime(value?: string) {
    if (!value) {
        return "";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }
    return date.toLocaleString("zh-CN", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
    });
}

function formatScore(value: number | null) {
    return value == null ? "-" : `${value}`;
}

function formatAccuracy(value: number | null) {
    return value == null ? "-" : `${value}%`;
}

function formatDurationSeconds(value?: number | null) {
    if (value == null) {
        return "";
    }
    const totalSeconds = Math.max(0, Math.floor(value));
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    if (hours > 0) {
        return `${hours}小时 ${String(minutes).padStart(2, "0")}分`;
    }
    return `${String(minutes).padStart(2, "0")}分 ${String(seconds).padStart(2, "0")}秒`;
}

export function ResultPage() {
    const { sessionId = "" } = useParams();
    const navigate = useNavigate();
    const detailQuery = usePracticeDetailQuery(sessionId);
    const restartPracticeMutation = useRestartPracticeMutation();
    const detail = detailQuery.data;
    const session = detail?.session;
    const historyQuery = usePracticeHistoryQuery(session?.qaSetId, { enabled: Boolean(session?.qaSetId) });
    const items = detail?.items ?? [];
    const assessDetail = session?.assessDetail;
    const strengths = assessDetail?.strengths ?? [];
    const weakPoints = assessDetail?.weaknesses ?? [];
    const reviewGuidance = assessDetail?.reviewGuidance || "";
    const title = session?.qaSetTitle || "本轮练习结果";
    const summary = session?.summary || assessDetail?.overallComment || "本轮练习已完成，系统已保存你的作答记录。";
    const accuracyValue = session?.accuracy == null ? 0 : Math.min(100, Math.max(0, session.accuracy));
    const finishedAt = formatDateTime(session?.finishedAt);
    const totalDuration = formatDurationSeconds(session?.durationSeconds);
    const totalQuestions = session?.totalQuestions || items.length || 1;

    const distData = [
        { name: "完美", value: session?.perfectCount ?? 0, color: CHART_COLORS.perfect },
        { name: "正确", value: session?.correctCount ?? 0, color: CHART_COLORS.correct },
        { name: "缺漏", value: session?.deficientCount ?? 0, color: CHART_COLORS.deficient },
        { name: "错误", value: session?.wrongCount ?? 0, color: CHART_COLORS.wrong },
        { name: "不会", value: session?.unknownCount ?? 0, color: CHART_COLORS.unknown },
    ];

    const moduleData = (() => {
        const map = new Map<string, { total: number; totalScore: number }>();
        items.forEach((item) => {
            const tags = (item.moduleTag || "").trim().split(/[,，、|]/).filter(Boolean);
            if (tags.length === 0) tags.push("未标注");
            tags.slice(0, 3).forEach((tag) => {
                const cur = map.get(tag) ?? { total: 0, totalScore: 0 };
                cur.total += 1;
                cur.totalScore += item.score ?? 0;
                map.set(tag, cur);
            });
        });
        return Array.from(map.entries())
            .map(([name, { total, totalScore }]) => ({ name, score: total ? Math.round(totalScore / total) : 0, total }))
            .sort((a, b) => b.total - a.total || b.score - a.score)
            .slice(0, 8);
    })();

    const trendData = (historyQuery.data ?? []).slice(0, 8).reverse().map((item) => ({
        date: formatDateTime(item.finishedAt),
        score: item.score ?? 0,
        accuracy: item.accuracy ?? 0,
    }));

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

    if (session.status !== "FINISHED") {
        return (
            <div className="page-frame">
                <div className="status-card result-status-card">
                    <strong>本轮尚未完成</strong>
                    <div className="qa-text">完成全部题目并提交整轮后，系统会生成完整的评估报告。</div>
                </div>
                <div className="result-actions">
                    <LinkButton to={`/practice/${session.id || sessionId}`} variant="primary">返回练习</LinkButton>
                    <LinkButton to="/quiz" variant="ghost">返回测试页</LinkButton>
                </div>
            </div>
        );
    }

    const handleRestartPractice = async () => {
        const restarted = await restartPracticeMutation.mutateAsync({
            qaSetId: session.qaSetId,
            mode: session.mode,
            feedbackMode: session.feedbackMode,
            selectedModule: session.selectedModule || undefined,
        });
        navigate(`/practice/${restarted.session.id}`);
    };

    return (
        <div className="page-frame result-page">
            <section className="result-hero-report">
                <div className="result-hero-report__main">
                    <h1>{title}</h1>
                    <p>{summary}</p>
                    <div className="result-hero-report__meta">
                        <span>{feedbackModeLabel(session.feedbackMode)}</span>
                        {finishedAt ? <span>完成于 {finishedAt}</span> : null}
                        {totalDuration ? <span>总耗时 {totalDuration}</span> : null}
                    </div>
                </div>

                <aside className="result-score-panel">
                    <div
                        className="result-score-ring"
                        style={{ "--result-accuracy": `${accuracyValue}%` } as CSSProperties}
                        aria-label={`达标率 ${formatAccuracy(session.accuracy)}`}
                    >
                        <div>
                            <strong>{formatAccuracy(session.accuracy)}</strong>
                            <span>达标率</span>
                        </div>
                    </div>
                    <div className="result-score-panel__stats">
                        <div>
                            <strong>{formatScore(session.score)}</strong>
                            <span>平均分</span>
                        </div>
                        <div>
                            <strong>{(session.perfectCount ?? 0) + (session.correctCount ?? 0)} / {session.totalQuestions || items.length}</strong>
                            <span>正确情况</span>
                        </div>
                    </div>
                </aside>
            </section>

            <div className="result-charts-row">
                <div className="result-report-panel">
                    <div className="result-section-head">
                        <h2>结果分布</h2>
                        <span className="result-soft-badge">{totalQuestions} 题</span>
                    </div>
                    <ResponsiveContainer width="100%" height={220}>
                        <BarChart data={distData} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.04)" vertical={false} />
                            <XAxis dataKey="name" tick={{ fill: "rgba(41,37,32,0.4)", fontSize: 12, fontFamily: "-apple-system, sans-serif" }} axisLine={false} tickLine={false} />
                            <YAxis hide />
                            <Tooltip contentStyle={{ borderRadius: 12, border: "1px solid rgba(67,59,48,0.1)", backgroundColor: "#fffcf7", fontFamily: "-apple-system, sans-serif", fontSize: 13 }} formatter={(val) => [`${val} 题`, "数量"]} cursor={{ fill: "transparent" }} />
                            <Bar dataKey="value" radius={[8, 8, 3, 3]} maxBarSize={64}>
                                {distData.map((entry, idx) => (<Cell key={idx} fill={entry.color} />))}
                            </Bar>
                        </BarChart>
                    </ResponsiveContainer>
                </div>

                <div className="result-report-panel">
                    <div className="result-section-head">
                        <h2>模块表现</h2>
                        <span className="result-soft-badge">{moduleData.length} 组</span>
                    </div>
                    {moduleData.length ? (
                        <ResponsiveContainer width="100%" height={220}>
                            <BarChart data={moduleData} layout="vertical" margin={{ top: 0, right: 0, bottom: 0, left: 72 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.04)" horizontal={false} />
                                <XAxis type="number" domain={[0, 100]} hide />
                                <YAxis type="category" dataKey="name" tick={{ fill: "#292520", fontSize: 14, fontFamily: "-apple-system, sans-serif" }} axisLine={false} tickLine={false} width={70} />
                                <Tooltip contentStyle={{ borderRadius: 12, border: "1px solid rgba(67,59,48,0.1)", backgroundColor: "#fffcf7", fontFamily: "-apple-system, sans-serif", fontSize: 13 }} formatter={(val) => [`${val} 分`]} cursor={{ fill: "transparent" }} />
                                <Bar dataKey="score" radius={[0, 6, 6, 0]} fill={CHART_COLORS.module} maxBarSize={16} />
                            </BarChart>
                        </ResponsiveContainer>
                    ) : <div className="result-empty">暂无模块数据。</div>}
                </div>
            </div>

            <div className="result-report-panel">
                <div className="result-section-head">
                    <h2>历史趋势</h2>
                    <span className="result-soft-badge">最近 {trendData.length} 次</span>
                </div>
                {trendData.length > 1 ? (
                    <ResponsiveContainer width="100%" height={220}>
                        <LineChart data={trendData} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.04)" vertical={false} />
                            <XAxis dataKey="date" tick={{ fill: "rgba(41,37,32,0.4)", fontSize: 12, fontFamily: "-apple-system, sans-serif" }} axisLine={false} tickLine={false} />
                            <YAxis hide domain={[0, 100]} />
                            <Tooltip contentStyle={{ borderRadius: 12, border: "1px solid rgba(67,59,48,0.1)", backgroundColor: "#fffcf7", fontFamily: "-apple-system, sans-serif", fontSize: 13 }} cursor={{ stroke: "rgba(0,0,0,0.08)", strokeDasharray: "3 3" }} />
                            <Line type="monotone" dataKey="score" name="分数" stroke={CHART_COLORS.correct} strokeWidth={2.5} dot={{ r: 4, fill: "#fff", stroke: CHART_COLORS.correct, strokeWidth: 2 }} activeDot={{ r: 6, fill: CHART_COLORS.correct, stroke: "#fff", strokeWidth: 2 }} />
                            <Line type="monotone" dataKey="accuracy" name="达标率" stroke={CHART_COLORS.perfect} strokeWidth={2} strokeDasharray="6 4" dot={{ r: 3, fill: "#fff", stroke: CHART_COLORS.perfect, strokeWidth: 1.5 }} activeDot={{ r: 5, fill: CHART_COLORS.perfect, stroke: "#fff", strokeWidth: 2 }} />
                        </LineChart>
                    </ResponsiveContainer>
                ) : <div className="result-empty">至少完成两次练习后展示趋势。</div>}
            </div>

            <section className="result-analysis-grid">
                <article className="result-report-panel">
                    <div className="result-section-head">
                        <h2>做得好的地方</h2>
                        <span className="result-soft-badge">{strengths.length} 项</span>
                    </div>
                    <div className="result-insight-list">
                        {strengths.length ? strengths.map((item, index) => (
                            <div key={`${pointTitle(item)}-${index}`} className="result-insight">
                                <span className="result-insight__mark result-insight__mark--good">✓</span>
                                <div>
                                    <strong>{pointTitle(item)}</strong>
                                    {item.moduleTag ? <span className="result-insight__tag">{item.moduleTag}</span> : null}
                                    <p>{pointBody(item)}</p>
                                </div>
                            </div>
                        )) : <div className="result-empty">暂无优势明细，系统仍已保存本轮统计。</div>}
                    </div>
                </article>

                <article className="result-report-panel result-report-panel--focus">
                    <div className="result-section-head">
                        <h2>需要补的地方</h2>
                        <span className="result-soft-badge">{weakPoints.length} 项</span>
                    </div>
                    <div className="result-insight-list">
                        {weakPoints.length ? weakPoints.map((item, index) => (
                            <div key={`${pointTitle(item)}-${index}`} className="result-insight">
                                <span className="result-insight__mark result-insight__mark--warn">!</span>
                                <div>
                                    <strong>{pointTitle(item)}</strong>
                                    {item.moduleTag ? <span className="result-insight__tag">{item.moduleTag}</span> : null}
                                    <p>{pointBody(item)}</p>
                                </div>
                            </div>
                        )) : <div className="result-empty">暂无薄弱点明细，可以继续通过错题复盘巩固。</div>}
                    </div>
                </article>
            </section>

            <section className="result-review-plan">
                <div className="result-section-head">
                    <h2>复习建议</h2>
                </div>
                {reviewGuidance ? (
                    <div className="result-guidance">
                        <p>{reviewGuidance}</p>
                    </div>
                ) : (
                    <div className="result-empty">暂无复习建议，可以先从错误和不会的题目开始复盘。</div>
                )}
            </section>

            {items.length ? (
                <section className="result-question-panel">
                    <div className="result-section-head">
                        <h2>题目结果明细</h2>
                        <span className="result-soft-badge">{items.length} 题</span>
                    </div>
                    <div className="result-question-list">
                        {items.map((item, index) => {
                            const meta = resultMeta(item);
                            return (
                                <button
                                    key={item.sessionItemId}
                                    type="button"
                                    className="result-question-row result-question-row--clickable"
                                    onClick={() => navigate(`/practice/${session.id || sessionId}/review?index=${index}`)}
                                >
                                    <span className="result-question-row__number">{index + 1}</span>
                                    <div className="result-question-row__body">
                                        <strong>{item.question || "未命名题目"}</strong>
                                        <span>{item.feedbackSummary || item.status || "暂无反馈摘要"}</span>
                                    </div>
                                    <span className={`result-chip result-chip--${meta.tone}`}>{meta.label}</span>
                                    <strong className="result-question-row__score">{item.score == null ? "-" : item.score}</strong>
                                </button>
                            );
                        })}
                    </div>
                </section>
            ) : null}

            <div className="result-actions">
                <LinkButton to="/quiz" variant="ghost">
                    回到主页
                </LinkButton>
                <BaseButton variant="primary" onClick={handleRestartPractice} disabled={restartPracticeMutation.isPending}>
                    {restartPracticeMutation.isPending ? "重新开始中" : "重新练习"}
                </BaseButton>
            </div>
            <div className="result-spacer" />
        </div>
    );
}
