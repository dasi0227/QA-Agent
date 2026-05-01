import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate } from "react-router";
import { z } from "zod";
import { BaseButton } from "@/components/base/button";
import { Field, TextInput } from "@/components/base/field";
import { GlassCard } from "@/components/base/card";
import { useLoginMutation } from "@/lib/api/hooks";
import { getRememberPreference } from "@/lib/auth";
import { resolveAuthRedirectTarget, type AuthRedirectLocation } from "@/lib/authRedirect";

const loginSchema = z.object({
  account: z.string().min(1, "请输入邮箱或用户名"),
  password: z.string().min(6, "密码至少 6 位"),
  remember: z.boolean().optional(),
});

type LoginForm = z.infer<typeof loginSchema>;

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const loginMutation = useLoginMutation();
  const form = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      account: "",
      password: "",
      remember: getRememberPreference(),
    },
  });

  const errorMessage = loginMutation.error instanceof Error ? loginMutation.error.message : "";
  const from = (location.state as { from?: AuthRedirectLocation } | null | undefined)?.from;

  return (
    <GlassCard className="auth-form">
      <div>
        <h2 className="auth-form__title">登录</h2>
      </div>
      <form
        className="auth-form__grid"
        onSubmit={form.handleSubmit(async (values) => {
          const session = await loginMutation.mutateAsync(values);
          navigate(
            resolveAuthRedirectTarget(from, session.user?.profileCompleted ? "/repository" : "/profile"),
            { replace: true },
          );
        })}
      >
        <Field label="邮箱或用户名" error={form.formState.errors.account?.message}>
          <TextInput placeholder="teacher@example.com" {...form.register("account")} />
        </Field>
        <Field label="密码" error={form.formState.errors.password?.message}>
          <TextInput type="password" placeholder="至少 6 位" {...form.register("password")} />
        </Field>
        <label className="auth-form__check">
          <input type="checkbox" {...form.register("remember")} />
          <span>记住登录</span>
        </label>
        {errorMessage ? (
          <div className="page-copy" style={{ color: "var(--ink)" }}>
            登录失败：{errorMessage}
          </div>
        ) : null}
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
  );
}
