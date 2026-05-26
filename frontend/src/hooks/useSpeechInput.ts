import { useCallback, useEffect, useRef, useState } from "react";

type SpeechRecognitionEventLike = Event & {
    resultIndex: number;
    results: {
        length: number;
        [index: number]: {
            isFinal: boolean;
            [index: number]: {
                transcript: string;
            };
        };
    };
};

type SpeechRecognitionErrorEventLike = Event & {
    error?: string;
    message?: string;
};

type SpeechRecognitionLike = EventTarget & {
    lang: string;
    continuous: boolean;
    interimResults: boolean;
    start: () => void;
    stop: () => void;
    abort: () => void;
    onstart: (() => void) | null;
    onend: (() => void) | null;
    onresult: ((event: SpeechRecognitionEventLike) => void) | null;
    onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null;
};

type SpeechRecognitionConstructor = new () => SpeechRecognitionLike;

type SpeechWindow = Window & {
    SpeechRecognition?: SpeechRecognitionConstructor;
    webkitSpeechRecognition?: SpeechRecognitionConstructor;
};

type UseSpeechInputOptions = {
    lang?: string;
    onText?: (text: string) => void;
};

function recognitionConstructor() {
    if (typeof window === "undefined") {
        return null;
    }
    const speechWindow = window as SpeechWindow;
    return speechWindow.SpeechRecognition ?? speechWindow.webkitSpeechRecognition ?? null;
}

export function useSpeechInput({ lang = "zh-CN", onText }: UseSpeechInputOptions = {}) {
    const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
    const [supported, setSupported] = useState(false);
    const [listening, setListening] = useState(false);
    const [transcript, setTranscript] = useState("");
    const [error, setError] = useState("");

    useEffect(() => {
        setSupported(Boolean(recognitionConstructor()));
        return () => {
            recognitionRef.current?.abort();
            recognitionRef.current = null;
        };
    }, []);

    const stop = useCallback(() => {
        recognitionRef.current?.stop();
        setListening(false);
    }, []);

    const start = useCallback(() => {
        const Recognition = recognitionConstructor();
        if (!Recognition) {
            setError("当前浏览器不支持语音输入");
            setSupported(false);
            return;
        }
        recognitionRef.current?.abort();
        const recognition = new Recognition();
        recognition.lang = lang;
        recognition.continuous = true;
        recognition.interimResults = true;
        recognition.onstart = () => {
            setError("");
            setListening(true);
        };
        recognition.onend = () => {
            setListening(false);
        };
        recognition.onerror = (event) => {
            setError(event.message || event.error || "语音识别失败");
            setListening(false);
        };
        recognition.onresult = (event) => {
            let finalText = "";
            let interimText = "";
            for (let index = event.resultIndex; index < event.results.length; index += 1) {
                const result = event.results[index];
                const text = result[0]?.transcript ?? "";
                if (result.isFinal) {
                    finalText += text;
                } else {
                    interimText += text;
                }
            }
            const nextTranscript = `${finalText}${interimText}`.trim();
            setTranscript(nextTranscript);
            if (finalText.trim()) {
                onText?.(finalText.trim());
            }
        };
        recognitionRef.current = recognition;
        recognition.start();
    }, [lang, onText]);

    const reset = useCallback(() => {
        setTranscript("");
        setError("");
    }, []);

    return {
        supported,
        listening,
        transcript,
        error,
        start,
        stop,
        reset,
    };
}
