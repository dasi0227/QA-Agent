import { Mic, MicOff } from "lucide-react";
import { useSpeechInput } from "@/hooks/useSpeechInput";
import { BaseButton } from "@/components/base/button";

type VoiceAnswerButtonProps = {
    disabled?: boolean;
    onText: (text: string) => void;
};

export function VoiceAnswerButton({ disabled, onText }: VoiceAnswerButtonProps) {
    const speech = useSpeechInput({ onText });
    const unavailable = disabled || !speech.supported;
    const title = !speech.supported ? "当前浏览器不支持语音输入" : speech.error || "";

    return (
        <div className="voice-answer">
            <BaseButton
                type="button"
                variant={speech.listening ? "primary" : "soft"}
                className="voice-answer__button"
                leadingIcon={speech.listening ? <MicOff size={16} /> : <Mic size={16} />}
                disabled={unavailable}
                title={title}
                onClick={() => {
                    if (speech.listening) {
                        speech.stop();
                    } else {
                        speech.reset();
                        speech.start();
                    }
                }}
            >
                {speech.listening ? "停止语音" : "语音输入"}
            </BaseButton>
            {speech.listening ? <span className="voice-answer__status">识别中</span> : null}
            {!speech.supported ? <span className="voice-answer__status voice-answer__status--muted">浏览器不支持</span> : null}
            {speech.error && speech.supported ? <span className="voice-answer__status voice-answer__status--error">{speech.error}</span> : null}
        </div>
    );
}
