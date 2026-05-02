import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { z } from "zod";
import { BaseButton } from "@/components/base/button";
import { Field, TextInput } from "@/components/base/field";
import { cn } from "@/lib/cn";
import { clearAccessToken, useAuthState } from "@/lib/auth";
import { useProfileQuery, useSaveProfileMutation } from "@/lib/api/hooks";

const profileSchema = z.object({
    targetRole: z.string().min(1, "请输入目标岗位"),
    targetDomain: z.string().min(1, "请选择目标方向"),
    targetCompany: z.string().min(1, "请输入目标公司"),
    allowGeneralKnowledge: z.boolean(),
    allowWebSearch: z.boolean(),
    answerStyle: z.string().min(1, "请输入答案风格"),
    feedbackStyle: z.string().min(1, "请输入反馈风格"),
    age: z.string().min(1, "请输入年龄"),
    grade: z.string().min(1, "请输入年级"),
    major: z.string().min(1, "请输入专业"),
    stage: z.string().min(1, "请输入准备阶段"),
});

type ProfileForm = z.infer<typeof profileSchema>;

const defaultProfile: ProfileForm = {
    targetRole: "Java 后端开发",
    targetDomain: "Java 后端",
    targetCompany: "互联网公司",
    allowGeneralKnowledge: true,
    allowWebSearch: false,
    answerStyle: "口语化但逻辑清晰",
    feedbackStyle: "直接指出问题并给建议",
    age: "22",
    grade: "大四",
    major: "计算机科学与技术",
    stage: "秋招准备",
};

export function ProfilePage() {
    const navigate = useNavigate();
    const { data, isError, error, refetch } = useProfileQuery();
    const saveMutation = useSaveProfileMutation();
    const authState = useAuthState();
    const currentUser = authState.user;

    const form = useForm<ProfileForm>({
        resolver: zodResolver(profileSchema),
        defaultValues: defaultProfile,
    });

    useEffect(() => {
        if (data) {
            form.reset(data);
        }
    }, [data, form]);

    const errorMessage = error instanceof Error ? error.message : "";
    const allowGeneralKnowledge = form.watch("allowGeneralKnowledge");
    const handleLogout = () => {
        clearAccessToken();
        navigate("/login", { replace: true });
    };

    return (
        <div className="page-frame profile-page">
            <form
                className="profile-form profile-form--flat"
                onSubmit={form.handleSubmit(async (values) => {
                    await saveMutation.mutateAsync(values);
                })}
            >
                <div className="profile-form__body profile-form__body--flat">
                    {isError ? (
                        <div className="qa-feedback">
                            <strong>Profile 加载失败</strong>
                            <div className="qa-text">{errorMessage || "请重试后继续编辑。"}</div>
                            <div>
                                <BaseButton variant="soft" type="button" onClick={() => refetch()}>
                                    重试
                                </BaseButton>
                            </div>
                        </div>
                    ) : null}

                    <section className="profile-section">
                        <div className="profile-section__title">账户信息</div>
                        <div className="profile-grid profile-grid--two">
                            <Field label="用户名">
                                <TextInput readOnly value={currentUser?.username ?? ""} />
                            </Field>
                            <Field label="邮箱">
                                <TextInput readOnly value={currentUser?.email ?? ""} />
                            </Field>
                            <Field label="密码">
                                <TextInput readOnly type="password" value="********" />
                            </Field>
                        </div>
                    </section>

                    <section className="profile-section">
                        <div className="profile-section__title">求职意向</div>
                        <div className="profile-grid profile-grid--two">
                            <Field label="目标岗位" error={form.formState.errors.targetRole?.message}>
                                <TextInput {...form.register("targetRole")} />
                            </Field>
                            <Field label="目标领域" error={form.formState.errors.targetDomain?.message}>
                                <TextInput placeholder="Java 后端 / 中间件 / 数据库" {...form.register("targetDomain")} />
                            </Field>
                            <Field label="目标公司" error={form.formState.errors.targetCompany?.message}>
                                <TextInput {...form.register("targetCompany")} />
                            </Field>
                            <Field label="年龄" error={form.formState.errors.age?.message}>
                                <TextInput {...form.register("age")} />
                            </Field>
                            <Field label="年级" error={form.formState.errors.grade?.message}>
                                <TextInput {...form.register("grade")} />
                            </Field>
                            <Field label="专业" error={form.formState.errors.major?.message}>
                                <TextInput {...form.register("major")} />
                            </Field>
                            <Field label="准备阶段" error={form.formState.errors.stage?.message}>
                                <TextInput {...form.register("stage")} />
                            </Field>
                        </div>
                    </section>

                    <section className="profile-section">
                        <div className="profile-section__title">智能体配置</div>
                        <div className="profile-grid profile-grid--two">
                            <Field label="答案风格" error={form.formState.errors.answerStyle?.message}>
                                <TextInput {...form.register("answerStyle")} />
                            </Field>
                            <Field label="反馈风格" error={form.formState.errors.feedbackStyle?.message}>
                                <TextInput {...form.register("feedbackStyle")} />
                            </Field>
                        </div>
                    </section>

                    <section className="profile-section">
                        <button
                            type="button"
                            className={cn("profile-switch", allowGeneralKnowledge && "profile-switch--active")}
                            onClick={() =>
                                form.setValue("allowGeneralKnowledge", !allowGeneralKnowledge, { shouldDirty: true })
                            }
                        >
                            <span className="profile-switch__copy">
                                <strong>通用知识</strong>
                                <small>生成时优先依赖资料，必要时可补通用知识，并标注补充内容。</small>
                            </span>
                            <span className="profile-switch__track" aria-hidden="true">
                                <span className="profile-switch__thumb" />
                            </span>
                        </button>
                        <button
                            type="button"
                            className={cn("profile-switch", form.watch("allowWebSearch") && "profile-switch--active")}
                            onClick={() =>
                                form.setValue("allowWebSearch", !form.getValues("allowWebSearch"), { shouldDirty: true })
                            }
                        >
                            <span className="profile-switch__copy">
                                <strong>Web 搜索</strong>
                                <small>后续生成链路可按配置决定是否允许补充外部搜索结果。</small>
                            </span>
                            <span className="profile-switch__track" aria-hidden="true">
                                <span className="profile-switch__thumb" />
                            </span>
                        </button>
                    </section>
                </div>

                <div className="profile-form__footer">
                    <div className="profile-form__actions profile-form__actions--left">
                        {saveMutation.isError ? (
                            <div className="profile-form__error">
                                保存失败：
                                {saveMutation.error instanceof Error ? saveMutation.error.message : "请稍后重试"}
                            </div>
                        ) : null}
                        <BaseButton variant="primary" className="btn--profile-save" type="submit" disabled={saveMutation.isPending}>
                            {saveMutation.isPending ? "保存中" : "保存设置"}
                        </BaseButton>
                        <BaseButton variant="outline" className="topbar__logout" type="button" onClick={handleLogout}>
                            退出登录
                        </BaseButton>
                    </div>
                </div>
            </form>
        </div>
    );
}
