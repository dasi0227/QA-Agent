import { zodResolver } from "@hookform/resolvers/zod";
import { useCallback, useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate } from "react-router";
import { z } from "zod";
import { ArrowLeft } from "lucide-react";
import { BaseButton } from "@/components/base/button";
import { ErrorDialog } from "@/components/base/error-dialog";
import { Field, TextInput } from "@/components/base/field";
import { GlassCard } from "@/components/base/card";
import { useRegisterMutation, useSendVerifyCodeMutation } from "@/lib/api/hooks";
import { ApiError } from "@/lib/api/client";
import { getRememberPreference } from "@/lib/auth";

const registerSchema = z
    .object({
        name: z.string().min(1, "请输入用户名"),
        email: z.string().email("请输入有效邮箱"),
        password: z.string().min(6, "密码至少 6 位"),
        confirmPassword: z.string().min(6, "请再次输入密码"),
        remember: z.boolean().optional(),
    })
    .refine((value) => value.password === value.confirmPassword, {
        message: "两次密码不一致",
        path: ["confirmPassword"],
    });

type RegisterForm = z.infer<typeof registerSchema>;

const CODE_LENGTH = 6;
const COUNTDOWN_SECONDS = 60;

function mapVerifyErrorCode(code?: string, fallback?: string): string {
    switch (code) {
        case "40001":
            return "验证码已过期，请重新发送";
        case "40002":
            return "验证码错误，请重新输入";
        case "40003":
        case "42900":
            return "发送过于频繁，请稍后再试";
        case "40004":
        case "40901":
            return "该邮箱已被注册";
        case "40005":
        case "40900":
            return "用户名已被占用";
        default:
            return fallback || "请求失败";
    }
}

export function RegisterPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const registerMutation = useRegisterMutation();
    const sendCodeMutation = useSendVerifyCodeMutation();

    const [step, setStep] = useState<"form" | "verify">("form");
    const [countdown, setCountdown] = useState(0);
    const [errorDialog, setErrorDialog] = useState<{ title: string; message: string } | null>(null);
    const [successDialog, setSuccessDialog] = useState(false);

    const form = useForm<RegisterForm>({
        resolver: zodResolver(registerSchema),
        defaultValues: {
            name: "",
            email: "",
            password: "",
            confirmPassword: "",
            remember: getRememberPreference(),
        },
    });

    const codeInputRefs = useRef<(HTMLInputElement | null)[]>(Array(CODE_LENGTH).fill(null));
    const [codeDigits, setCodeDigits] = useState<string[]>(Array(CODE_LENGTH).fill(""));
    const formValuesRef = useRef<RegisterForm | null>(null);

    // Countdown timer
    useEffect(() => {
        if (countdown <= 0) return;
        const timer = setInterval(() => {
            setCountdown((prev) => {
                if (prev <= 1) {
                    clearInterval(timer);
                    return 0;
                }
                return prev - 1;
            });
        }, 1000);
        return () => clearInterval(timer);
    }, [countdown]);

    const clearCodeInputs = useCallback(() => {
        setCodeDigits(Array(CODE_LENGTH).fill(""));
        codeInputRefs.current[0]?.focus();
    }, []);

    const handleDigitInput = useCallback((index: number, value: string) => {
        if (!/^\d*$/.test(value)) return;
        const digit = value.slice(-1);
        setCodeDigits((prev) => {
            const next = [...prev];
            next[index] = digit;
            return next;
        });
        if (digit && index < CODE_LENGTH - 1) {
            codeInputRefs.current[index + 1]?.focus();
        }
    }, []);

    const handleDigitKeyDown = useCallback((index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === "Backspace") {
            setCodeDigits((prev) => {
                const next = [...prev];
                if (next[index]) {
                    next[index] = "";
                } else if (index > 0) {
                    next[index - 1] = "";
                    codeInputRefs.current[index - 1]?.focus();
                }
                return next;
            });
            e.preventDefault();
        } else if (e.key === "ArrowLeft" && index > 0) {
            codeInputRefs.current[index - 1]?.focus();
        } else if (e.key === "ArrowRight" && index < CODE_LENGTH - 1) {
            codeInputRefs.current[index + 1]?.focus();
        }
    }, []);

    const handlePaste = useCallback((e: React.ClipboardEvent) => {
        const pasted = e.clipboardData.getData("text").replace(/\D/g, "").slice(0, CODE_LENGTH);
        if (pasted.length === 0) return;
        e.preventDefault();
        const digits = pasted.split("");
        const next = Array(CODE_LENGTH).fill("");
        digits.forEach((d, i) => { next[i] = d; });
        setCodeDigits(next);
        const focusIndex = Math.min(digits.length, CODE_LENGTH - 1);
        codeInputRefs.current[focusIndex]?.focus();
    }, []);

    const handleSendCode = useCallback(async (values: RegisterForm) => {
        try {
            await sendCodeMutation.mutateAsync({ email: values.email });
            formValuesRef.current = values;
            setStep("verify");
            setCountdown(COUNTDOWN_SECONDS);
            clearCodeInputs();
        } catch (error) {
            const msg = error instanceof ApiError
                ? mapVerifyErrorCode(error.code, error.message)
                : "发送验证码失败";
            setErrorDialog({ title: "发送失败", message: msg });
        }
    }, [sendCodeMutation, clearCodeInputs]);

    const handleVerify = useCallback(async () => {
        const values = formValuesRef.current;
        if (!values) return;
        const verifyCode = codeDigits.join("");
        if (verifyCode.length !== CODE_LENGTH) {
            setErrorDialog({ title: "验证码不完整", message: "请输入完整的 6 位验证码" });
            return;
        }
        try {
            await registerMutation.mutateAsync({
                name: values.name,
                email: values.email,
                password: values.password,
                verifyCode,
                remember: values.remember,
            });
            setSuccessDialog(true);
        } catch (error) {
            const msg = error instanceof ApiError
                ? mapVerifyErrorCode(error.code, error.message)
                : "注册失败";
            setErrorDialog({ title: "注册失败", message: msg });
            clearCodeInputs();
        }
    }, [codeDigits, registerMutation, navigate, clearCodeInputs]);

    const handleResendCode = useCallback(async () => {
        const values = formValuesRef.current;
        if (!values || countdown > 0) return;
        try {
            await sendCodeMutation.mutateAsync({ email: values.email });
            setCountdown(COUNTDOWN_SECONDS);
            clearCodeInputs();
        } catch (error) {
            const msg = error instanceof ApiError
                ? mapVerifyErrorCode(error.code, error.message)
                : "重新发送失败";
            setErrorDialog({ title: "发送失败", message: msg });
        }
    }, [sendCodeMutation, countdown, clearCodeInputs]);

    // --- Step 1: Form ---
    if (step === "form") {
        const sendPending = sendCodeMutation.isPending;
        return (
            <>
                <GlassCard className="auth-form">
                    <div>
                        <h2 className="auth-form__title">注册</h2>
                    </div>
                    <form
                        className="auth-form__grid"
                        onSubmit={form.handleSubmit(handleSendCode)}
                    >
                        <Field label="用户名" error={form.formState.errors.name?.message}>
                            <TextInput placeholder="请输入用户名，至少 4 位" {...form.register("name")} />
                        </Field>
                        <Field label="邮箱" error={form.formState.errors.email?.message}>
                            <TextInput placeholder="qa-agent@example.com" {...form.register("email")} />
                        </Field>
                        <Field label="密码" error={form.formState.errors.password?.message}>
                            <TextInput type="password" placeholder="请输入密码，至少 6 位" {...form.register("password")} />
                        </Field>
                        <Field label="确认密码" error={form.formState.errors.confirmPassword?.message}>
                            <TextInput type="password" placeholder="再输入一次" {...form.register("confirmPassword")} />
                        </Field>
                        <label className="auth-form__check">
                            <input type="checkbox" {...form.register("remember")} />
                            <span>记住登录</span>
                        </label>
                        <div className="auth-form__footer">
                            <BaseButton variant="primary" type="submit" disabled={sendPending}>
                                {sendPending ? "发送中" : "创建账号"}
                            </BaseButton>
                            <Link className="btn btn--link" to="/login">
                                已有账号，去登录
                            </Link>
                        </div>
                    </form>
                </GlassCard>
                <ErrorDialog
                    open={errorDialog !== null}
                    title={errorDialog?.title || "错误"}
                    message={errorDialog?.message || ""}
                    onConfirm={() => setErrorDialog(null)}
                />
            </>
        );
    }

    // --- Step 2: Verify ---
    const verifyPending = registerMutation.isPending;
    const codeComplete = codeDigits.join("").length === CODE_LENGTH;
    const email = formValuesRef.current?.email || "";

    return (
        <>
            <GlassCard className="auth-form">
                <div>
                    <button
                        type="button"
                        className="verify-back-btn"
                        onClick={() => setStep("form")}
                        aria-label="返回注册"
                    >
                        <ArrowLeft size={20} />
                    </button>
                    <h2 className="auth-form__title">请输入验证码</h2>
                    <p className="auth-form__copy verify-card__subtitle">
                        验证码已发送至 {email}
                    </p>
                </div>
                <div className="auth-form__grid">
                    <div className="verify-code-group" onPaste={handlePaste}>
                        {codeDigits.map((digit, index) => (
                            <input
                                key={index}
                                ref={(el) => { codeInputRefs.current[index] = el; }}
                                className="verify-code-input"
                                type="text"
                                inputMode="numeric"
                                autoComplete="one-time-code"
                                maxLength={1}
                                value={digit}
                                autoFocus={index === 0}
                                onChange={(e) => handleDigitInput(index, e.target.value)}
                                onKeyDown={(e) => handleDigitKeyDown(index, e)}
                            />
                        ))}
                    </div>
                    <div className="auth-form__footer" style={{ flexDirection: "column", alignItems: "stretch", gap: 12 }}>
                        <BaseButton
                            variant="primary"
                            type="button"
                            disabled={!codeComplete || verifyPending}
                            onClick={handleVerify}
                        >
                            {verifyPending ? "验证中" : "确认"}
                        </BaseButton>
                        <button
                            type="button"
                            className="btn btn--link"
                            disabled={countdown > 0}
                            onClick={handleResendCode}
                            style={{ alignSelf: "center" }}
                        >
                            {countdown > 0 ? `没收到？${countdown} 秒后再次发送` : "没收到？再次发送"}
                        </button>
                    </div>
                </div>
            </GlassCard>
            <ErrorDialog
                open={errorDialog !== null}
                title={errorDialog?.title || "错误"}
                message={errorDialog?.message || ""}
                onConfirm={() => setErrorDialog(null)}
            />
            <ErrorDialog
                open={successDialog}
                title="注册成功"
                message="您的账号已创建成功，请返回登录页面进行登录。"
                confirmLabel="返回登录"
                onConfirm={() => navigate("/login", { replace: true })}
            />
        </>
    );
}
