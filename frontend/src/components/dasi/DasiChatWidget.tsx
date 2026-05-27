import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { CornerDownLeft, Loader2, X } from "lucide-react";
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

const INITIAL_BUBBLE = "你好，我是 Dasi，有什么问题可以问我～";

const RANDOM_BUBBLES = [
    "累了吗？要不先喝口水。",
    "卡住也正常，慢慢拆。",
    "哪里没想通？可以直接问我。",
    "答案太长？我可以帮你拆结构。",
    "不确定怎么表达？丢给我看看。",
    "还是不理解？没关系，我会出手。",
    "这波可以先稳一手。",
    "问题不大，我们逐层分析。",
    "啊，是关中王来了。",
];

function createTempChatId() {
    const random = typeof crypto !== "undefined" && "randomUUID" in crypto
        ? crypto.randomUUID()
        : Math.random().toString(36).slice(2);
    return `dasi_${Date.now()}_${random}`;
}

function createMessageId() {
    return `msg_${Date.now()}_${Math.random().toString(36).slice(2)}`;
}

function randomBubbleText() {
    return RANDOM_BUBBLES[Math.floor(Math.random() * RANDOM_BUBBLES.length)] || RANDOM_BUBBLES[0];
}

function randomBubbleDelay() {
    return 60000 + Math.floor(Math.random() * 120000);
}

function shouldShowDasi(pathname: string) {
    if (
        pathname === "/repository/document" ||
        pathname === "/repository/question" ||
        pathname === "/repository/qa-set"
    ) {
        return true;
    }
    if (pathname.startsWith("/result/")) {
        return true;
    }
    if (pathname.startsWith("/practice/") && !pathname.endsWith("/review")) {
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
    const [bubbleVisible, setBubbleVisible] = useState(false);
    const [bubbleText, setBubbleText] = useState(INITIAL_BUBBLE);
    const timersRef = useRef<number[]>([]);
    const openRef = useRef(open);
    const bottomRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
        openRef.current = open;
    }, [open]);

    const clearBubbleTimers = useCallback(() => {
        timersRef.current.forEach((timerId) => window.clearTimeout(timerId));
        timersRef.current = [];
    }, []);

    const scheduleRandomBubble = useCallback(() => {
        if (openRef.current) {
            return;
        }
        const timerId = window.setTimeout(() => {
            if (openRef.current) {
                return;
            }
            setBubbleText(randomBubbleText());
            setBubbleVisible(true);
            const hideTimerId = window.setTimeout(() => {
                setBubbleVisible(false);
                scheduleRandomBubble();
            }, 3000);
            timersRef.current.push(hideTimerId);
        }, randomBubbleDelay());
        timersRef.current.push(timerId);
    }, []);

    useEffect(() => {
        clearBubbleTimers();
        setTempChatId(createTempChatId());
        setMessages([]);
        setInput("");
        setInputError("");
        setOpen(false);
        setBubbleVisible(false);
        setBubbleText(INITIAL_BUBBLE);
        if (!visible) {
            return undefined;
        }
        const showTimerId = window.setTimeout(() => {
            if (openRef.current) {
                return;
            }
            setBubbleVisible(true);
            const hideTimerId = window.setTimeout(() => {
                setBubbleVisible(false);
                scheduleRandomBubble();
            }, 3000);
            timersRef.current.push(hideTimerId);
        }, 500);
        timersRef.current.push(showTimerId);
        return clearBubbleTimers;
    }, [clearBubbleTimers, routeKey, scheduleRandomBubble, visible]);

    useEffect(() => {
        bottomRef.current?.scrollIntoView({ block: "end" });
    }, [messages, open, tempChatMutation.isPending]);

    const toggleOpen = useCallback(() => {
        setOpen((current) => {
            const next = !current;
            if (next) {
                clearBubbleTimers();
                setBubbleVisible(false);
            } else {
                scheduleRandomBubble();
            }
            return next;
        });
    }, [clearBubbleTimers, scheduleRandomBubble]);

    const openChat = useCallback(() => {
        clearBubbleTimers();
        setBubbleVisible(false);
        setOpen(true);
    }, [clearBubbleTimers]);

    const closeChat = useCallback(() => {
        setOpen(false);
        scheduleRandomBubble();
    }, [scheduleRandomBubble]);

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

    const handleInputKeyDown = useCallback((event: KeyboardEvent<HTMLTextAreaElement>) => {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            void sendMessage();
        }
    }, [sendMessage]);

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
                            <strong>可以直接问我。</strong>
                            <span>我不会读取当前页面；需要我分析的内容，可以粘贴到这里。</span>
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
                        placeholder="输入问题，按 Enter 发送"
                        rows={1}
                        onChange={(event) => {
                            setInput(event.target.value);
                            if (inputError) {
                                setInputError("");
                            }
                        }}
                        onKeyDown={handleInputKeyDown}
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

            <button type="button" className="dasi-mascot" onClick={toggleOpen} aria-label={open ? "关闭临时对话" : "打开临时对话"}>
                <DasiMascotSvg />
            </button>

            <button type="button" className={bubbleClassName} onClick={openChat} aria-label="打开临时对话">
                {bubbleText}
            </button>
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
