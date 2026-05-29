import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate } from "react-router";
import { z } from "zod";
import { BaseButton } from "@/components/base/button";
import { ErrorDialog } from "@/components/base/error-dialog";
import { Field, TextInput } from "@/components/base/field";
import { GlassCard } from "@/components/base/card";
import { useLoginMutation } from "@/lib/api/hooks";
import { ApiError } from "@/lib/api/client";
import { getRememberPreference } from "@/lib/auth";
import { parseAuthRedirectTarget, resolveAuthRedirectTarget, type AuthRedirectLocation } from "@/lib/authRedirect";

const loginSchema = z.object({
  account: z.string().min(4, "用户名至少 4 位"),
  password: z.string().min(6, "密码至少 6 位"),
  remember: z.boolean().optional(),
});

type LoginForm = z.infer<typeof loginSchema>;

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const loginMutation = useLoginMutation();
  const [errorDialog, setErrorDialog] = useState<{ title: string; message: string } | null>(null);
  const form = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      account: "",
      password: "",
      remember: getRememberPreference(),
    },
  });

  const from = (location.state as { from?: AuthRedirectLocation } | null | undefined)?.from
    ?? parseAuthRedirectTarget(new URLSearchParams(location.search).get("from"));

  return (
    <>
      <GlassCard className="auth-form">
        <div>
          <h2 className="auth-form__title">登录</h2>
        </div>
        <form
          className="auth-form__grid"
          onSubmit={form.handleSubmit(async (values) => {
            try {
              const session = await loginMutation.mutateAsync(values);
              navigate(
                resolveAuthRedirectTarget(from, session.user?.profileCompleted ? "/quiz" : "/profile"),
                { replace: true },
              );
            } catch (error) {
              const msg = error instanceof ApiError
                ? error.message
                : "登录失败，请稍后重试";
              setErrorDialog({ title: "登录失败", message: msg });
            }
          })}
        >
          <Field label="用户名" error={form.formState.errors.account?.message}>
            <TextInput placeholder="请输入用户名，至少 4 位" {...form.register("account")} />
          </Field>
          <Field label="密码" error={form.formState.errors.password?.message}>
            <TextInput type="password" placeholder="请输入密码，至少 6 位" {...form.register("password")} />
          </Field>
          <label className="auth-form__check">
            <input type="checkbox" {...form.register("remember")} />
            <span>记住登录</span>
          </label>
          <div className="auth-form__footer">
            <BaseButton variant="primary" type="submit" disabled={loginMutation.isPending}>
              {loginMutation.isPending ? "登录中" : "登录"}
            </BaseButton>
            <Link className="btn btn--link" to="/register" state={from ? { from } : undefined}>
              没有账号，去注册
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
