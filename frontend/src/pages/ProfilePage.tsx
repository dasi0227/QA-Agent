import { zodResolver } from "@hookform/resolvers/zod";
import { useCallback, useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { z } from "zod";
import Cropper, { type Area } from "react-easy-crop";
import "react-easy-crop/react-easy-crop.css";
import { BaseButton } from "@/components/base/button";
import { Field, TextInput } from "@/components/base/field";
import { cn } from "@/lib/cn";
import { clearAccessToken, useAuthState } from "@/lib/auth";
import { useProfileQuery, useSaveProfileMutation, useUploadAvatarMutation } from "@/lib/api/hooks";

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

function getCroppedImg(imageSrc: string, crop: Area): Promise<Blob> {
    return new Promise((resolve, reject) => {
        const image = new Image();
        image.src = imageSrc;
        image.onload = () => {
            const canvas = document.createElement("canvas");
            const scaleX = image.naturalWidth / image.width;
            const scaleY = image.naturalHeight / image.height;
            canvas.width = crop.width * scaleX;
            canvas.height = crop.height * scaleY;
            const ctx = canvas.getContext("2d");
            if (!ctx) {
                reject(new Error("无法创建 canvas"));
                return;
            }
            ctx.drawImage(
                image,
                crop.x * scaleX,
                crop.y * scaleY,
                crop.width * scaleX,
                crop.height * scaleY,
                0,
                0,
                canvas.width,
                canvas.height,
            );
            canvas.toBlob((blob) => {
                if (blob) resolve(blob);
                else reject(new Error("裁剪失败"));
            }, "image/png");
        };
        image.onerror = () => reject(new Error("图片加载失败"));
    });
}

export function ProfilePage() {
    const navigate = useNavigate();
    const { data, isError, error, refetch } = useProfileQuery();
    const saveMutation = useSaveProfileMutation();
    const uploadAvatarMutation = useUploadAvatarMutation();
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

    const fileInputRef = useRef<HTMLInputElement>(null);
    const [avatarPreview, setAvatarPreview] = useState("");
    const avatarUrl = avatarPreview || currentUser?.avatar?.trim() || "";

    const [cropSrc, setCropSrc] = useState("");
    const [crop, setCrop] = useState({ x: 0, y: 0 });
    const [zoom, setZoom] = useState(1);
    const [croppedArea, setCroppedArea] = useState<Area | null>(null);

    const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file) return;
        if (!file.type.startsWith("image/")) {
            alert("请选择图片文件");
            return;
        }
        const url = URL.createObjectURL(file);
        setCropSrc(url);
        setCrop({ x: 0, y: 0 });
        setZoom(1);
        setCroppedArea(null);
    };

    const handleCropComplete = useCallback((_: Area, cropped: Area) => {
        setCroppedArea(cropped);
    }, []);

    const handleCropConfirm = async () => {
        if (!cropSrc || !croppedArea) return;
        try {
            const blob = await getCroppedImg(cropSrc, croppedArea);
            const file = new File([blob], "avatar.png", { type: "image/png" });
            setAvatarPreview(URL.createObjectURL(blob));
            await uploadAvatarMutation.mutateAsync(file);
        } catch {
            alert("裁剪失败，请重试");
        } finally {
            setCropSrc("");
            URL.revokeObjectURL(cropSrc);
        }
    };

    const handleCropCancel = () => {
        setCropSrc("");
        URL.revokeObjectURL(cropSrc);
        if (fileInputRef.current) {
            fileInputRef.current.value = "";
        }
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
                        <div className="profile-grid profile-grid--two" style={{ alignItems: "start" }}>
                            <Field label="用户名">
                                <TextInput readOnly value={currentUser?.username ?? ""} />
                            </Field>
                            <div style={{ gridRow: "span 2", alignSelf: "stretch", display: "flex", flexDirection: "column", gap: 8 }}>
                                <span className="field__label">头像</span>
                                <button
                                    type="button"
                                    onClick={() => fileInputRef.current?.click()}
                                    style={{
                                        width: 128,
                                        borderRadius: 24,
                                        overflow: "hidden",
                                        border: "1px solid var(--line)",
                                        background: "var(--bg-glass)",
                                        cursor: "pointer",
                                        padding: 0,
                                    }}
                                    aria-label="更换头像"
                                >
                                    {avatarUrl ? (
                                        <img
                                            src={avatarUrl}
                                            alt=""
                                            style={{ width: "100%", height: "100%", objectFit: "cover" }}
                                        />
                                    ) : (
                                        <span style={{ fontFamily: "var(--font-sans)", fontSize: 32, color: "var(--ink-soft)" }}>
                                            {currentUser?.username?.charAt(0)?.toUpperCase() || "U"}
                                        </span>
                                    )}
                                </button>
                                <input
                                    ref={fileInputRef}
                                    type="file"
                                    accept="image/*"
                                    style={{ display: "none" }}
                                    onChange={handleFileSelect}
                                />
                            </div>
                            <Field label="邮箱">
                                <TextInput readOnly value={currentUser?.email ?? ""} />
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

            {cropSrc ? (
                <div className="quiz-action-sheet" role="presentation" onClick={handleCropCancel}>
                    <div
                        className="modal-card"
                        role="dialog"
                        aria-modal="true"
                        aria-label="裁剪头像"
                        style={{ width: "min(520px, 100%)" }}
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="modal-card__header">
                            <h3 className="modal-card__title">裁剪头像</h3>
                        </div>
                        <div className="modal-card__body" style={{ position: "relative", minHeight: 320 }}>
                            <Cropper
                                image={cropSrc}
                                crop={crop}
                                zoom={zoom}
                                aspect={1}
                                cropShape="rect"
                                onCropChange={setCrop}
                                onZoomChange={setZoom}
                                onCropComplete={handleCropComplete}
                            />
                        </div>
                        <div className="modal-card__footer">
                            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                                <BaseButton variant="ghost" type="button" onClick={handleCropCancel}>
                                    取消
                                </BaseButton>
                                <BaseButton
                                    variant="primary"
                                    type="button"
                                    disabled={uploadAvatarMutation.isPending}
                                    onClick={handleCropConfirm}
                                >
                                    {uploadAvatarMutation.isPending ? "上传中" : "确认裁剪"}
                                </BaseButton>
                            </div>
                        </div>
                    </div>
                </div>
            ) : null}
        </div>
    );
}
