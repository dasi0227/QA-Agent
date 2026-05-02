import { Link } from "react-router";
import { ArrowLeft } from "lucide-react";
import { BaseButton } from "@/components/base/button";
import { GlassCard } from "@/components/base/card";

export function QAPage() {
    return (
        <div className="qa-layout">
            <header className="sidebar__split" style={{ marginBottom: 16 }}>
                <Link to="/quiz" className="btn btn--link" style={{ paddingLeft: 0 }}>
                    <ArrowLeft size={14} />
                    返回测试页
                </Link>
                <div className="page-copy" style={{ fontSize: 12 }}>
                    Practice Disabled
                </div>
            </header>

            <GlassCard className="qa-card">
                <div className="qa-feedback">
                    <strong>练习链路未完成</strong>
                    <div className="qa-text">当前版本只打通核心资产查询、编辑和删除。答题、反馈、评分链路暂未接入。</div>
                    <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                        <BaseButton variant="soft" type="button" disabled>
                            提交答案未接入
                        </BaseButton>
                        <Link to="/repository" className="btn btn--link">
                            去仓库维护题目
                        </Link>
                    </div>
                </div>
            </GlassCard>
        </div>
    );
}
