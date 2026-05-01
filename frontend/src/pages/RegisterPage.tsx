import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate } from "react-router";
import { z } from "zod";
import { BaseButton } from "@/components/base/button";
import { Field, TextInput } from "@/components/base/field";
import { GlassCard } from "@/components/base/card";
import { useRegisterMutation } from "@/lib/api/hooks";
import { getRememberPreference } from "@/lib/auth";
import { resolveAuthRedirectTarget, type AuthRedirectLocation } from "@/lib/authRedirect";

const registerSchema = z
  .object({
    name: z.string().min(1, "请输入昵称"),
    email: z.string().email("请输入有效邮箱"),
    password: z.string().min(8, "密码至少 8 位"),
    confirmPassword: z.string().min(8, "请再次输入密码"),
    remember: z.boolean().optional(),
  })
  .refine((value) => value.password === value.confirmPassword, {
    message: "两次密码不一致",
    path: ["confirmPassword"],
  });

type RegisterForm = z.infer<typeof registerSchema>;

export function RegisterPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const registerMutation = useRegisterMutation();
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

  const errorMessage = registerMutation.error instanceof Error ? registerMutation.error.message : "";
  const from = (location.state as { from?: AuthRedirectLocation } | null | undefined)?.from;

  return (
    <GlassCard className="auth-form">
      <div>
        <h2 className="auth-form__title">注册</h2>
      </div>
      <form
        className="auth-form__grid"
        onSubmit={form.handleSubmit(async (values) => {
          const session = await registerMutation.mutateAsync(values);
          navigate(
            resolveAuthRedirectTarget(from, session.user?.profileCompleted ? "/repository" : "/profile"),
            { replace: true },
          );
        })}
      >
        <Field label="昵称" error={form.formState.errors.name?.message}>
          <TextInput placeholder="wyw" {...form.register("name")} />
        </Field>
        <Field label="邮箱" error={form.formState.errors.email?.message}>
          <TextInput placeholder="teacher@example.com" {...form.register("email")} />
        </Field>
        <Field label="密码" error={form.formState.errors.password?.message}>
          <TextInput type="password" placeholder="至少 8 位" {...form.register("password")} />
        </Field>
        <Field label="确认密码" error={form.formState.errors.confirmPassword?.message}>
          <TextInput type="password" placeholder="再输入一次" {...form.register("confirmPassword")} />
        </Field>
        <label className="auth-form__check">
          <input type="checkbox" {...form.register("remember")} />
          <span>记住登录</span>
        </label>
        {errorMessage ? (
          <div className="page-copy" style={{ color: "var(--ink)" }}>
            注册失败：{errorMessage}
          </div>
        ) : null}
        <div className="auth-form__footer">
          <BaseButton variant="primary" type="submit" disabled={registerMutation.isPending}>
            {registerMutation.isPending ? "创建中" : "创建账号"}
          </BaseButton>
          <Link className="btn btn--link" to="/login" state={from ? { from } : undefined}>
            已有账号，去登录
          </Link>
        </div>
      </form>
    </GlassCard>
  );
}
