import { useMemo } from "react";
import { GlassCard } from "@/components/base/card";

export function IndexPage() {
  const description = useMemo(() => {
    return "基于你的真实资料，QA Agent 会构建高可信面试题库，进一步总结知识笔记和标准回答，同时支持自动识别薄弱知识和自定义表达模式，把每轮练习沉淀为可回看、可修正、可持续迭代的成长闭环。";
  }, []);

  const featureCards = [
    {
      index: "01",
      title: "上传笔记",
      copy: "把项目笔记、技术整理或面试资料放进系统，作为训练起点。",
    },
    {
      index: "02",
      title: "生成面试问答集",
      copy: "围绕资料提炼高频追问、知识笔记和可直接口述的答案。",
    },
    {
      index: "03",
      title: "开始练习复习",
      copy: "用同一份问答集反复练习，在反馈里持续修正薄弱点。",
    },
  ];

  return (
    <div className="page-frame" style={{ gap: 32 }}>
      <GlassCard className="hero-card hero-card--plain" style={{ padding: "0 62px" }}>
        <div style={{ display: "grid", justifyItems: "center", gap: 32 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 20, justifyContent: "center" }}>
            <img
              src="/logo.svg"
              alt=""
              style={{ width: "clamp(72px, 11vw, 150px)", height: "clamp(72px, 11vw, 150px)" }}
            />
            <h1
              style={{
                margin: 0,
                fontSize: "clamp(50px, 6.7vw, 80px)",
                lineHeight: 1.04,
                letterSpacing: "-0.05em",
                fontWeight: 500,
                color: "var(--ink)",
              }}
            >
              QA Agent
            </h1>
          </div>
          <p
            style={{
              margin: 0,
              fontSize: "clamp(52px, 8vw, 96px)",
              color: "var(--ink-soft)",
              fontWeight: 700,
              letterSpacing: "0.02em",
            }}
          >
            从个人笔记到面试题库
          </p>
          <p className="hero-copy hero-copy--left" style={{ marginTop: 0, maxWidth: 760, marginInline: "auto" }}>
            {description}
          </p>
        </div>
      </GlassCard>

      <section className="feature-grid" aria-label="产品闭环">
        {featureCards.map((item) => (
          <article key={item.index} className="feature-card">
            <div className="feature-card__index">{item.index}</div>
            <div className="feature-card__title">{item.title}</div>
            <div className="feature-card__copy">{item.copy}</div>
          </article>
        ))}
      </section>
    </div>
  );
}
