import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ChevronLeft, CornerDownLeft, Loader2, X } from "lucide-react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { useLocation } from "react-router";
import { useTempChatMutation } from "@/lib/api/hooks";

type DasiMessage = {
    id: string;
    role: "user" | "assistant";
    content: string;
    tone?: "normal" | "error";
};

const GLOBAL_BUBBLES = [
    "你好，我是 Dasi，有什么问题可以点击询问哦 🤓",
    "如果不想看到 Dasi，可以点击左上角的折叠哦（但我会伤心的 😭）",
    "🔔 注意点击右上角设置 LLM 参数，你将会赋予 Dasi 生命！",
];

const PAGE_BUBBLES: Record<string, string[]> = {
    "/repository/qa-set": [
        "看够了嘛？别等了，现在就开始练习吧 📦",
        "题目有问题？点击题目可以进入详情页修改哦～",
        "📊 戳一下「练习历史」，看看你的战绩如何～",
        "随机练习还是顺序练习？选一个喜欢的姿势开始 🎲",
        "据我观察，多练几次，分数就会慢慢涨上去 🏆",
    ],
    "/repository/document": [
        "⚠️ Dasi 当前只支持 Markdown 格式，不要传错了哦～",
        "上传后 Dasi 会自动帮你建立专属 RAG 知识库，放心交给我 🔍",
        "⚠️ 注意哦，资料上传后内容不支持修改，请确认内容无误再上传～",
        "资料越丰富，生成的题目就越精准，不妨多传几篇。",
        "资料准备好了，我的出题引擎已经饥渴难耐了 💪",
    ],
    "/repository/question": [
        "🛠️ 点击补全填入问题和答案，剩下的交给 Dasi～",
        "每一道题目都是知识的凝聚，掌握它们，offer 到手 💎",
        "Dasi 生成的题目不满意？别骂了别骂了，自己动手改改，我也会进步的 🙇",
        "给题目加上模块标签和难度，复习的时候更好分类哦 🏷️",
        "关键词 🔑 设得好，以后检索更方便，别偷懒填个「其他」～",
    ],
    "/practice": [
        "慢慢来不要急，想清楚再写 🧘",
        "不确定怎么写？没关系，点「提示」看看思路 💡",
        "每道题都是积累，只要在练习就是在进步！",
        "这题真有水平，不愧是我出的，把 Dasi 自己都难到了 😤",
        "Offer 进度 99%，就差这题了 🎯",
        "先喝口水，换个思路再回来，答案就浮现了 ☕",
        "慢就是快，每个细节都值得推敲 🐢",
        "纠结不定？先写下第一反应，回头再改也不迟。",
        "我觉得你有点进步了～说真的 🌱",
        "批改中……开个玩笑，Dasi 还没那么智能 ✍️",
        "偷偷告诉你 🥷，AI 判题也会看走眼，感觉不对可以质疑。",
        "答完了？看看 Dasi 给你的反馈，每条都值得看 💬",
    ],
    "/result": [
        "🎉 太棒了！本轮已经完成，来看看你的表现吧。",
        "薄弱模块建议回头再看一眼资料，确保真的记住了～ 🔁",
        "达标率不理想？别焦虑，Dasi 陪你反复练，直到滚瓜烂熟 📉",
        "知识记住了不算完，能在题目里用出来才是你的。继续练习，形成肌肉记忆 🧠",
    ],
    "/quiz": [
        "选择一个题集，开始今天的练习吧 🎯",
        "每次练习都是成长，Dasi 帮你记录每一次进步 🏆",
        "完成练习后可以在这里看到你的历史表现 📊",
        "随机练习还是顺序练习？选一个喜欢的姿势开始 🎲",
        "准备好了吗？刷题才是检验知识的唯一标准 💪",
    ],
    "/profile": [
        "🤖 开始使用系统之前，别忘了配置你的模型参数哦～",
        "点击「个人记忆」，Dasi 有一份对你的观察报告 👀",
        "模型参数影响判题和出题质量，建议用最新模型效果更好 ⚙️",
        "完善个人信息，Dasi 会给你更贴合的题目和反馈 🧩",
    ],
    "/create": [
        "不知道怎么写需求？试试「生成 10 道关于 SpringBoot 的面试题目」",
        "⚠️ 生成过程可能需要几分钟，请耐心等待哦～",
        "好需求是高质量题目的关键，花点时间打磨值得 🧠",
        "📂 先添加资料吧——偷偷告诉你，Dasi 生成的题目都是基于你的资料哦",
        "丢失进度了？点击时钟图标即可恢复之前的任务 ⏱️",
        "⚙️ 发送前别忘去设置里配置生成需求，这步很重要！",
    ],
};

function createTempChatId() {
    const random = typeof crypto !== "undefined" && "randomUUID" in crypto
        ? crypto.randomUUID()
        : Math.random().toString(36).slice(2);
    return `dasi_${Date.now()}_${random}`;
}

function createMessageId() {
    return `msg_${Date.now()}_${Math.random().toString(36).slice(2)}`;
}

function shuffleArray<T>(arr: T[]): T[] {
    const shuffled = [...arr];
    for (let i = shuffled.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }
    return shuffled;
}

function getPageBubblePool(pathname: string): string[] {
    const key = Object.keys(PAGE_BUBBLES).find((k) => pathname.startsWith(k)) ?? "";
    return [...GLOBAL_BUBBLES, ...(PAGE_BUBBLES[key] ?? [])];
}

let shuffleBuffer: string[] = [];
let shuffleIndex = 0;

function randomBubbleText(pathname: string): string {
    const pool = getPageBubblePool(pathname);
    if (shuffleIndex >= shuffleBuffer.length) {
        shuffleBuffer = shuffleArray(pool);
        shuffleIndex = 0;
    }
    return shuffleBuffer[shuffleIndex++] ?? GLOBAL_BUBBLES[0];
}

function pageBubbleDelay() {
    return 40000 + Math.floor(Math.random() * 20000);
}

function globalBubbleDelay() {
    return 30000;
}

function randomGlobalBubbleText() {
    return GLOBAL_BUBBLES[Math.floor(Math.random() * GLOBAL_BUBBLES.length)] ?? GLOBAL_BUBBLES[0];
}

function shouldShowDasi(pathname: string) {
    if (
        pathname === "/repository/document" ||
        pathname === "/repository/question" ||
        pathname === "/repository/qa-set" ||
        pathname === "/quiz" ||
        pathname === "/create"
    ) {
        return true;
    }
    if (pathname.startsWith("/result/")) {
        return true;
    }
    if (pathname.startsWith("/practice/") && !pathname.endsWith("/review")) {
        return true;
    }
    if (pathname.startsWith("/profile")) {
        return true;
    }
    return false;
}

export function DasiChatWidget() {
    const location = useLocation();
    const routeKey = `${location.pathname}${location.search}`;
    const visible = shouldShowDasi(location.pathname);
    const tempChatMutation = useTempChatMutation();
    const [tempChatId, setTempChatId] = useState(() => createTempChatId());
    const [messages, setMessages] = useState<DasiMessage[]>([]);
    const [input, setInput] = useState("");
    const [inputError, setInputError] = useState("");
    const [open, setOpen] = useState(false);
    const [collapsed, setCollapsed] = useState(false);
    const [bubbleVisible, setBubbleVisible] = useState(false);
    const [bubbleText, setBubbleText] = useState("");
    const timersRef = useRef<number[]>([]);
    const openRef = useRef(open);
    const bubbleVisibleRef = useRef(false);
    const bottomRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
        openRef.current = open;
    }, [open]);

    useEffect(() => {
        bubbleVisibleRef.current = bubbleVisible;
    }, [bubbleVisible]);

    const clearBubbleTimers = useCallback(() => {
        timersRef.current.forEach((timerId) => window.clearTimeout(timerId));
        timersRef.current = [];
    }, []);

    const popBubble = useCallback((text: string) => {
        clearBubbleTimers();
        setBubbleText(text);
        setBubbleVisible(true);
    }, [clearBubbleTimers]);

    const scheduleHideAndNext = useCallback((nextFn: () => void) => {
        const hideId = window.setTimeout(() => {
            setBubbleVisible(false);
            nextFn();
        }, 3900);
        timersRef.current.push(hideId);
    }, []);

    const schedulePageBubble = useCallback(() => {
        const run = () => {
            if (openRef.current || bubbleVisibleRef.current) {
                const retryId = window.setTimeout(run, 2000);
                timersRef.current.push(retryId);
                return;
            }
            popBubble(randomBubbleText(location.pathname));
            scheduleHideAndNext(schedulePageBubble);
        };
        const timerId = window.setTimeout(run, pageBubbleDelay());
        timersRef.current.push(timerId);
    }, [location.pathname, popBubble, scheduleHideAndNext]);

    const scheduleGlobalBubble = useCallback(() => {
        const run = () => {
            if (openRef.current || bubbleVisibleRef.current) {
                const retryId = window.setTimeout(run, 2000);
                timersRef.current.push(retryId);
                return;
            }
            popBubble(randomGlobalBubbleText());
            scheduleHideAndNext(scheduleGlobalBubble);
        };
        const timerId = window.setTimeout(run, globalBubbleDelay());
        timersRef.current.push(timerId);
    }, [popBubble, scheduleHideAndNext]);

    useEffect(() => {
        clearBubbleTimers();
        setTempChatId(createTempChatId());
        setMessages([]);
        setInput("");
        setInputError("");
        setOpen(false);
        setBubbleVisible(false);
        setBubbleText("");
        if (!visible) {
            return undefined;
        }
        schedulePageBubble();
        scheduleGlobalBubble();
        return clearBubbleTimers;
    }, [clearBubbleTimers, routeKey, schedulePageBubble, scheduleGlobalBubble, visible]);

    useEffect(() => {
        bottomRef.current?.scrollIntoView({ block: "end" });
    }, [messages, open, tempChatMutation.isPending]);

    // Listen for behavior-triggered bubbles from other components
    useEffect(() => {
        const handler = (e: Event) => {
            const text = (e as CustomEvent<string>).detail;
            if (!text || openRef.current) return;
            popBubble(text);
            scheduleHideAndNext(() => {
                schedulePageBubble();
                scheduleGlobalBubble();
            });
        };
        window.addEventListener("dasi:bubble", handler);
        return () => window.removeEventListener("dasi:bubble", handler);
    }, [popBubble, scheduleHideAndNext, schedulePageBubble, scheduleGlobalBubble]);

    const restartBubbleSchedulers = useCallback(() => {
        schedulePageBubble();
        scheduleGlobalBubble();
    }, [schedulePageBubble, scheduleGlobalBubble]);

    const toggleOpen = useCallback(() => {
        setOpen((current) => {
            const next = !current;
            if (next) {
                clearBubbleTimers();
                setBubbleVisible(false);
            } else {
                restartBubbleSchedulers();
            }
            return next;
        });
    }, [clearBubbleTimers, restartBubbleSchedulers]);

    const openChat = useCallback(() => {
        clearBubbleTimers();
        setBubbleVisible(false);
        setOpen(true);
    }, [clearBubbleTimers]);

    const closeChat = useCallback(() => {
        setOpen(false);
        restartBubbleSchedulers();
    }, [restartBubbleSchedulers]);

    const sendMessage = useCallback(async () => {
        const message = input.trim();
        if (!message || tempChatMutation.isPending) {
            return;
        }
        if (message.length > 4000) {
            setInputError("单次提问最多 4000 个字符。");
            return;
        }
        setInputError("");
        const userMessage: DasiMessage = {
            id: createMessageId(),
            role: "user",
            content: message,
        };
        setMessages((current) => [...current, userMessage]);
        setInput("");
        try {
            const response = await tempChatMutation.mutateAsync({ tempChatId, message });
            setMessages((current) => [
                ...current,
                {
                    id: createMessageId(),
                    role: "assistant",
                    content: response.content || "Dasi 暂时没有回复，请稍后再试。",
                },
            ]);
        } catch {
            setMessages((current) => [
                ...current,
                {
                    id: createMessageId(),
                    role: "assistant",
                    tone: "error",
                    content: "Dasi 暂时没有回复，请稍后再试。",
                },
            ]);
        }
    }, [input, tempChatId, tempChatMutation]);


    const panelClassName = useMemo(() => `dasi-chat-panel${open ? " dasi-chat-panel--open" : ""}`, [open]);
    const bubbleClassName = useMemo(() => `dasi-bubble${bubbleVisible && !open ? " dasi-bubble--visible" : ""}`, [bubbleVisible, open]);

    if (!visible) {
        return null;
    }

    return (
        <div className="dasi-widget" aria-live="polite">
            <section className={panelClassName} aria-label="临时对话">
                <header className="dasi-chat-panel__header">
                    <strong>临时对话</strong>
                    <button type="button" className="dasi-chat-panel__close" onClick={closeChat} aria-label="关闭临时对话">
                        <X size={16} />
                    </button>
                </header>

                <div className="dasi-chat-panel__body">
                    {messages.length === 0 ? (
                        <div className="dasi-chat-empty">
                            <span>⚠️⚠️⚠️ 临时对话不会持久保存，但你在当前页面的对话历史会短暂得保留，刷新页面后将会丢失。请留意重要内容及时备份。</span>
                        </div>
                    ) : null}
                    {messages.map((message) => (
                        <DasiMessageBubble key={message.id} message={message} />
                    ))}
                    {tempChatMutation.isPending ? (
                        <div className="dasi-message dasi-message--assistant">
                            <span className="dasi-message__typing">
                                <Loader2 size={14} />
                                Dasi 正在思考
                            </span>
                        </div>
                    ) : null}
                    <div ref={bottomRef} />
                </div>

                <div className="dasi-chat-panel__input-area">
                    <textarea
                        className="dasi-chat-input"
                        value={input}
                        maxLength={4000}
                        placeholder="给 Dasi 发送消息"
                        rows={1}
                        onChange={(event) => {
                            setInput(event.target.value);
                            if (inputError) {
                                setInputError("");
                            }
                        }}
                        disabled={tempChatMutation.isPending}
                    />
                    <button
                        type="button"
                        className="dasi-chat-send"
                        onClick={() => void sendMessage()}
                        disabled={!input.trim() || tempChatMutation.isPending}
                        aria-label="发送"
                    >
                        <CornerDownLeft size={20} />
                    </button>
                    {inputError ? <span className="dasi-chat-error">{inputError}</span> : null}
                </div>
            </section>

            {collapsed ? (
                <button type="button" className="dasi-collapsed-btn" onClick={() => setCollapsed(false)} aria-label="展开 Dasi">
                    Dasi
                </button>
            ) : (
                <div className="dasi-mascot-area" onMouseEnter={() => {}} onMouseLeave={() => {}}>
                    <button
                        type="button"
                        className="dasi-collapse-btn"
                        onClick={(e) => { e.stopPropagation(); setOpen(false); setCollapsed(true); }}
                        aria-label="收起 Dasi"
                    >
                        <ChevronLeft size={18} />
                    </button>
                    <button type="button" className="dasi-mascot" onClick={toggleOpen} aria-label={open ? "关闭临时对话" : "打开临时对话"}>
                        <DasiMascotSvg />
                    </button>
                    <button type="button" className={bubbleClassName} onClick={openChat} aria-label="打开临时对话">
                        {bubbleText}
                    </button>
                </div>
            )}

            {collapsed ? null : null}
        </div>
    );
}

function DasiMessageBubble({ message }: { message: DasiMessage }) {
    const className = [
        "dasi-message",
        message.role === "user" ? "dasi-message--user" : "dasi-message--assistant",
        message.tone === "error" ? "dasi-message--error" : "",
    ].filter(Boolean).join(" ");

    if (message.role === "user") {
        return <div className={className}>{message.content}</div>;
    }

    return (
        <div className={className}>
            <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                components={{
                    a: ({ ...props }) => <a {...props} target="_blank" rel="noreferrer" />,
                }}
            >
                {message.content}
            </ReactMarkdown>
        </div>
    );
}

function DasiMascotSvg() {
    return (
        <svg className="dasi-mascot__svg" viewBox="0 0 228 214" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
            <ellipse cx="112" cy="190" rx="92" ry="14" fill="#2a261f" opacity=".12" />
            <g className="dasi-mascot__body">
                <path d="M34 178H196" stroke="#222721" strokeWidth="6" strokeLinecap="round" />
                <path d="M50 178C48 135 65 107 91 107H108C135 107 154 135 151 178H50Z" fill="#222721" />
                <path d="M75 119L83 166" stroke="#F8F1E5" strokeWidth="4" strokeLinecap="round" opacity=".92" />
                <path d="M126 121L118 166" stroke="#F8F1E5" strokeWidth="4" strokeLinecap="round" opacity=".92" />
                <path d="M74 166H112" stroke="#F8F1E5" strokeWidth="5" strokeLinecap="round" />
                <g className="dasi-mascot__head">
                    <path d="M75 82C72 62 80 42 99 39C119 36 132 49 132 69C132 93 117 106 96 105C85 104 77 96 75 82Z" fill="#F8F1E5" stroke="#222721" strokeWidth="5" />
                    <path d="M75 67C78 45 92 33 113 35C128 36 137 43 139 54C126 58 109 56 95 47C91 60 84 70 75 76V67Z" fill="#222721" stroke="#222721" strokeWidth="4" strokeLinejoin="round" />
                    <path d="M75 76C66 75 65 91 76 94" stroke="#222721" strokeWidth="5" strokeLinecap="round" />
                    <path className="dasi-mascot__eyes" d="M96 76V83M119 75V82" stroke="#222721" strokeWidth="5" strokeLinecap="round" />
                    <path d="M108 81L105 89H112" stroke="#222721" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
                    <path d="M94 96C100 100 109 99 115 94" stroke="#222721" strokeWidth="4" strokeLinecap="round" />
                    <path d="M91 67C96 64 101 64 106 67" stroke="#222721" strokeWidth="4" strokeLinecap="round" />
                    <path d="M115 67C120 64 126 65 130 68" stroke="#222721" strokeWidth="4" strokeLinecap="round" />
                </g>
                <text x="110" y="28" textAnchor="middle" fill="#000" fontFamily="-apple-system, sans-serif" fontSize="25" fontWeight="1000" letterSpacing="0.08em">Dasi</text>
                <path className="dasi-mascot__left-hand" d="M82 164C92 158 100 158 108 166" stroke="#F8F1E5" strokeWidth="8" strokeLinecap="round" />
                <path className="dasi-mascot__right-hand" d="M110 164C120 157 132 158 138 166" stroke="#F8F1E5" strokeWidth="8" strokeLinecap="round" />
            </g>
            <g>
                <path d="M118 174L144 122H195L178 174H118Z" fill="#222721" stroke="#222721" strokeWidth="5" strokeLinejoin="round" />
                <circle cx="166" cy="148" r="7" fill="#F8F1E5" />
                <path className="dasi-mascot__screen-glow" d="M139 148H151M136 158H154M133 168H158" stroke="#8C8E82" strokeWidth="5" strokeLinecap="round" />
            </g>
            <g>
                <path d="M24 166H47V187H24V166Z" fill="#222721" stroke="#222721" strokeWidth="4" strokeLinejoin="round" />
                <path d="M24 171C12 170 12 183 24 182" stroke="#222721" strokeWidth="4" strokeLinecap="round" />
                <path className="dasi-mascot__steam-one" d="M31 161C25 153 35 150 30 142" stroke="#B79B73" strokeWidth="3" strokeLinecap="round" />
                <path className="dasi-mascot__steam-two" d="M42 161C36 153 47 150 41 142" stroke="#B79B73" strokeWidth="3" strokeLinecap="round" />
            </g>
        </svg>
    );
}

export function emitDasiBubble(text: string) {
    window.dispatchEvent(new CustomEvent("dasi:bubble", { detail: text }));
}
