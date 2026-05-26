export type NavigationKey = "quiz" | "repository" | "create";

export type OverviewStat = {
  label: string;
  value: string;
  detail: string;
};

export type QuestionSet = {
  id: string;
  title: string;
  modules: string[];
  questionCount: number;
  practiceCount: number;
  averageScore: number;
  lastPracticedAt: string;
};

export type DocumentItem = {
  id: string;
  name: string;
  kind: "markdown" | "text";
  state: string;
  updatedAt: string;
};

export type TimelineItem = {
  stage: string;
  title: string;
  copy: string;
  badge?: string;
};

export type PracticeModule = {
  name: string;
  status: string;
};

export type PracticeSession = {
  questionIndex: number;
  questionTotal: number;
  title: string;
  tags: string[];
  hint: string;
  answer: string;
  answerGuide: string;
  feedback: string;
  modules: PracticeModule[];
};

export type PracticeResult = {
  score: number;
  summary: string;
  strengths: string[];
  gaps: string[];
  sessions: Array<{ label: string; score: number; detail: string }>;
};

export type ProfileDraft = {
  targetRole: string;
  targetDirection: string;
  allowReferMemory: boolean;
  answerStyle: string;
  feedbackStyle: string;
  grade: string;
  education: string;
  stage: string;
  companyType: string;
  note: string;
};

const delay = <T,>(value: T, ms = 120) => new Promise<T>((resolve) => {
  window.setTimeout(() => resolve(value), ms);
});

export const navigationItems: Array<{ key: NavigationKey; label: string; to: string }> = [
  { key: "repository", label: "仓库", to: "/repository/qa-set" },
  { key: "quiz", label: "测试", to: "/quiz" },
  { key: "create", label: "创建", to: "/create" },
];

export const overviewStats: OverviewStat[] = [
  { label: "问答集", value: "2", detail: "按模块组织，可反复练习" },
  { label: "资料文件", value: "4", detail: "Markdown / 纯文本为主" },
  { label: "最近评分", value: "88", detail: "来自最近一次模块练习" },
];

export const questionSets: QuestionSet[] = [
  {
    id: "java-backend",
    title: "Java 后端项目问答集",
    modules: ["Redis", "并发", "MQ", "Spring"],
    questionCount: 43,
    practiceCount: 12,
    averageScore: 78,
    lastPracticedAt: "今天",
  },
  {
    id: "redis-focus",
    title: "Redis 高频题问答集",
    modules: ["缓存", "持久化", "集群"],
    questionCount: 18,
    practiceCount: 7,
    averageScore: 82,
    lastPracticedAt: "昨天",
  },
];

export const documentItems: DocumentItem[] = [
  {
    id: "doc-1",
    name: "redis-note.md",
    kind: "markdown",
    state: "在线",
    updatedAt: "2 分钟前",
  },
  {
    id: "doc-2",
    name: "project-summary.md",
    kind: "markdown",
    state: "已入库",
    updatedAt: "18 分钟前",
  },
  {
    id: "doc-3",
    name: "backend-text.txt",
    kind: "text",
    state: "已解析",
    updatedAt: "今天 09:32",
  },
];

export const timelineItems: TimelineItem[] = [
  {
    stage: "活动 · 17s",
    title: "Parsing uploaded materials",
    copy: "正在读取本次上传的项目资料和技术笔记，先确认哪些内容能进入本轮问答集生成范围。",
    badge: "redis-note.md · project-summary.md",
  },
  {
    stage: "Planning",
    title: "Structuring modules and question batches",
    copy: "先按 Redis、消息队列、并发和项目经历拆模块，再决定每一批生成多少题，避免一次性输出过散。",
  },
  {
    stage: "Generating",
    title: "Creating interview-friendly Q&A",
    copy: "题目同时包含知识笔记版本和可直接口述的面试回答版本；阶段消息只读展示，不渲染实时日志流。",
  },
];

export const profileDefaults: ProfileDraft = {
  targetRole: "Java 后端开发",
  targetDirection: "校招",
  allowReferMemory: true,
  answerStyle: "口语化但逻辑清晰",
  feedbackStyle: "直接指出问题并给建议",
  grade: "大四",
  education: "本科",
  stage: "秋招准备",
  companyType: "互联网 / 中厂",
  note: "优先围绕项目和高频八股做专项训练。",
};

export const practiceSession: PracticeSession = {
  questionIndex: 12,
  questionTotal: 40,
  title: "Redis 为什么适合承担热点缓存，而不适合直接替代数据库作为主要数据存储方案？",
  tags: ["缓存", "中等", "已作答 3 次"],
  hint: "请从访问延迟、事务能力、复杂查询能力和持久性边界四个角度回答。",
  answer: "",
  answerGuide: "先说明 Redis 的定位，再对比数据库能力，最后落回面试中的典型取舍。",
  feedback:
    "回答方向正确，但还可以把“缓存命中率”和“复杂查询成本”讲得更具体一些，避免只停留在概念层。",
  modules: [
    { name: "缓存命中", status: "待强化" },
    { name: "持久化边界", status: "稳定" },
    { name: "事务模型", status: "波动" },
  ],
};

export const practiceResult: PracticeResult = {
  score: 88,
  summary: "这轮练习覆盖了 3 个模块，整体回答节奏稳定，但对持久化边界和幂等设计的表述还不够完整。",
  strengths: ["Redis 基础定位清楚", "能主动补充项目例子", "回答结构完整"],
  gaps: ["持久化策略还要再细化", "消息幂等设计表达偏泛", "个别追问响应速度偏慢"],
  sessions: [
    { label: "Redis / 缓存", score: 91, detail: "命中率、淘汰策略回答稳定" },
    { label: "消息队列", score: 84, detail: "消费幂等说明不够完整" },
    { label: "并发基础", score: 86, detail: "可见性和原子性还有提升空间" },
  ],
};

export async function fetchOverview() {
  return delay({
    title: "把资料变成高可信技术面试问答集",
    copy:
      "上传项目资料和技术笔记，生成可长期练习的问答集，并在持续测试中发现薄弱点。",
    cta: "登录并开始",
    stats: overviewStats,
  });
}

export async function fetchRepository() {
  return delay({
    questionSets,
    documentItems,
    activeSetId: questionSets[0]?.id ?? "",
  });
}

export async function fetchQuizContext() {
  return delay({
    title: "先设定这一轮测试，再从已有问答集里抽取最值得练的题。",
    copy:
      "这里不堆叠后台信息，只保留开始练习前必须做的配置，确保用户能快速进入正式作答。",
    modes: ["顺序练习", "随机练习", "按模块练习"],
    feedbackModes: ["逐题反馈", "整轮反馈"],
    scopes: ["Redis", "并发", "MQ", "Spring"],
    sets: questionSets,
  });
}

export async function fetchCreateContext() {
  return delay({
    timelineItems,
  });
}

export async function fetchPracticeSession() {
  return delay(practiceSession);
}

export async function fetchPracticeResult() {
  return delay(practiceResult);
}

export async function fetchProfile() {
  return delay(profileDefaults);
}
