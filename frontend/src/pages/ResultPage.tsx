import { LinkButton } from "@/components/base/button";
import { GlassCard } from "@/components/base/card";

export function ResultPage() {
    return (
        <div className="page-frame">
            <GlassCard className="hero-card">
                <div className="qa-feedback">
                    <strong>结果页未开放</strong>
                    <div className="qa-text">第一版当前未接入练习、反馈和评分链路，因此本页只保留降级提示。</div>
                    <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                        <LinkButton to="/repository" variant="ghost">
                            回仓库
                        </LinkButton>
                        <LinkButton to="/quiz" variant="primary">
                            返回测试页
                        </LinkButton>
                    </div>
                </div>
            </GlassCard>
        </div>
    );
}
