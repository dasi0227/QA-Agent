import { useMemo } from "react";
import { GlassCard } from "@/components/base/card";
import { useAuthState } from "@/lib/auth";

export function IndexPage() {
  const authState = useAuthState();
  const isAuthenticated = authState.status === "authenticated";

  const hero = useMemo(() => {
    const copy =
      "基于你的真实资料，QA Agent 会构建高可信面试题库，进一步总结知识笔记和标准回答，同时支持自动识别薄弱知识和自定义表达模式，把每轮练习沉淀为可回看、可修正、可持续迭代的成长闭环。";

    if (authState.status === "loading") {
      return {
        title: "QA Agent--从个人笔记到面试题库",
        copy,
      };
    }

    if (!isAuthenticated) {
      return {
        title: "QA Agent--从个人笔记到面试题库",
        copy,
      };
    }

    return {
      title: "QA Agent--从个人笔记到面试题库",
      copy,
    };
  }, [authState.status, isAuthenticated]);

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
      title: "开始测试复习",
      copy: "用同一份问答集反复练习，在反馈里持续修正薄弱点。",
    },
  ];

  return (
    <div className="page-frame">
      <GlassCard className="hero-card hero-card--plain">
        <div style={{ display: "grid", justifyItems: "center" }}>
          <h1 className="hero-title">{hero.title}</h1>
          <p className="hero-copy hero-copy--left" style={{ marginTop: 34, maxWidth: 760, marginInline: "auto" }}>
            {hero.copy}
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
