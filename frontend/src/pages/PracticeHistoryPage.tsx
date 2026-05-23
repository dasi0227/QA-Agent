import { Link, useParams } from "react-router";
import { LinkButton } from "@/components/base/button";
import { usePracticeHistoryQuery, useQuestionSetQuery } from "@/lib/api/hooks";

function formatDuration(value?: number) {
    const total = Math.max(0, Math.floor(value ?? 0));
    const hours = Math.floor(total / 3600);
    const minutes = Math.floor((total % 3600) / 60);
    const seconds = total % 60;
    if (hours > 0) {
        return `${hours}小时 ${String(minutes).padStart(2, "0")}分`;
    }
    return `${minutes}分 ${String(seconds).padStart(2, "0")}秒`;
}

function formatDateTime(value?: string) {
    if (!value) return "已完成练习";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleString("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
    });
}

function modeLabel(value?: string) {
    return value === "RANDOM" ? "随机练习" : "顺序练习";
}

function feedbackModeLabel(value?: string) {
    return value === "AFTER_ALL" ? "整轮反馈" : "逐题反馈";
}

export function PracticeHistoryPage() {
    const { qaSetId = "" } = useParams();
    const qaSetQuery = useQuestionSetQuery(qaSetId);
    const historyQuery = usePracticeHistoryQuery(qaSetId);
    const records = historyQuery.data ?? [];

    return (
        <div className="page-frame practice-history-page">
            <div className="practice-history-page__head">
                <div>
                    <span>练习历史</span>
                    <h1>{qaSetQuery.data?.title || "问答集练习历史"}</h1>
                </div>
                <LinkButton to={`/repository/qa-set/${qaSetId}`} variant="ghost">返回问答集</LinkButton>
            </div>

            {historyQuery.isLoading ? (
                <div className="status-card">正在读取练习历史...</div>
            ) : null}

            {historyQuery.isError ? (
                <div className="status-card">练习历史加载失败，请稍后重试。</div>
            ) : null}

            {!historyQuery.isLoading && !historyQuery.isError && records.length === 0 ? (
                <div className="status-card">暂无已完成练习。</div>
            ) : null}

            {records.length ? (
                <div className="practice-history-list">
                    {records.map((record) => (
                        <Link key={record.id} to={`/practice/${record.id}/result`} className="practice-history-card">
                            <div className="practice-history-card__main">
                                <strong>{formatDateTime(record.finishedAt)}</strong>
                                <span>{modeLabel(record.mode)} · {feedbackModeLabel(record.feedbackMode)} · {formatDuration(record.durationSeconds)}</span>
                            </div>
                            <div className="practice-history-card__metrics">
                                <span>分数 {record.score ?? "-"}</span>
                                <span>达标率 {record.accuracy ?? "-"}%</span>
                                <span>正确 {record.correctCount}</span>
                                <span>缺漏 {record.deficientCount}</span>
                                <span>错误 {record.wrongCount}</span>
                                <span>不会 {record.unknownCount}</span>
                            </div>
                        </Link>
                    ))}
                </div>
            ) : null}
        </div>
    );
}
