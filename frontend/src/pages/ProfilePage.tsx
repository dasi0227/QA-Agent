import { zodResolver } from "@hookform/resolvers/zod";
import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { NavLink, Navigate, Outlet, useNavigate, useOutletContext } from "react-router";
import { z } from "zod";
import { BaseButton } from "@/components/base/button";
import { Tag } from "@/components/base/tag";
import { emitDasiBubble } from "@/components/dasi/DasiChatWidget";
import { Field, TextInput } from "@/components/base/field";
import { LoadingCard } from "@/components/base/loading-card";
import type { CropArea } from "@/components/profile/ProfileAvatarCropper";
import { clearAccessToken, useAuthState } from "@/lib/auth";
import {
    useChangePasswordMutation,
    useHideMemoryMutation,
    useMemoryDetailQuery,
    useMemoryListQuery,
    useProfileQuery,
    useSaveProfileMutation,
    useUploadAvatarMutation,
} from "@/lib/api/hooks";
import type { AuthUser, Profile, UserMemory, UserMemoryEvidence } from "@/lib/api/types";
import { cn } from "@/lib/cn";
import { useGlobalErrorDialog } from "@/lib/error/ErrorDialogProvider";

const personalSchema = z.object({
    targetRole: z.string().min(1, "请输入目标岗位"),
    targetDomain: z.string().min(1, "请选择目标方向"),
    targetCompany: z.string().min(1, "请输入目标公司"),
    grade: z.string().min(1, "请输入年级"),
    major: z.string().min(1, "请输入专业"),
    stage: z.string().min(1, "请输入准备阶段"),
});

const agentSchema = z.object({
    allowReferMemory: z.boolean(),
    allowWebSearch: z.boolean(),
    allowFallback: z.boolean(),
    answerStyle: z.string().min(1, "请输入答案风格"),
    feedbackStyle: z.string().min(1, "请输入反馈风格"),
    llmBaseUrl: z.string(),
    llmApiKey: z.string(),
    llmModelName: z.string(),
});

const passwordSchema = z.object({
    currentPassword: z.string().min(1, "请输入当前密码"),
    newPassword: z.string().min(6, "新密码至少 6 位"),
    confirmPassword: z.string().min(1, "请再次输入新密码"),
}).superRefine((value, context) => {
    if (value.currentPassword === value.newPassword) {
        context.addIssue({
            code: z.ZodIssueCode.custom,
            message: "新密码不能和当前密码相同",
            path: ["newPassword"],
        });
    }
    if (value.newPassword !== value.confirmPassword) {
        context.addIssue({
            code: z.ZodIssueCode.custom,
            message: "两次输入的新密码不一致",
            path: ["confirmPassword"],
        });
    }
});

type PersonalForm = z.infer<typeof personalSchema>;
type AgentForm = z.infer<typeof agentSchema>;
type PasswordForm = z.infer<typeof passwordSchema>;

const defaultProfile: Profile = {
    targetRole: "Java 后端开发",
    targetDomain: "Java 后端",
    targetCompany: "互联网公司",
    allowReferMemory: true,
    allowWebSearch: false,
    allowFallback: false,
    answerStyle: "口语化但逻辑清晰",
    feedbackStyle: "直接指出问题并给建议",
    grade: "大四",
    major: "计算机科学与技术",
    stage: "秋招准备",
    llmBaseUrl: "",
    llmApiKey: "",
    llmModelName: "",
};

const defaultPassword: PasswordForm = {
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
};

const memoryGroups = [
    { type: "MASTER", label: "表现稳定", emoji: "✅", tone: "master" },
    { type: "UNCLEAR", label: "需要巩固", emoji: "⚠️", tone: "unclear" },
    { type: "AWFUL", label: "明显缺口", emoji: "❌", tone: "awful" },
] as const;

type MemoryGroupType = (typeof memoryGroups)[number]["type"];

type ProfileSettingsContext = {
    profile: Profile;
    currentUser?: AuthUser;
};

const LazyProfileAvatarCropper = lazy(() =>
    import("@/components/profile/ProfileAvatarCropper").then((module) => ({
        default: module.ProfileAvatarCropper,
    })),
);

function useProfileSettingsContext() {
    return useOutletContext<ProfileSettingsContext>();
}

function getCroppedImg(imageSrc: string, crop: CropArea): Promise<Blob> {
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
    const { data, isError, error, refetch, isLoading } = useProfileQuery();
    const authState = useAuthState();
    const profile = data ?? defaultProfile;
    const errorMessage = error instanceof Error ? error.message : "";
    const outletContext: ProfileSettingsContext = { profile, currentUser: authState.user ?? undefined };

    const handleLogout = () => {
        clearAccessToken();
        navigate("/login", { replace: true });
    };

    return (
        <div className="page-frame profile-page">
            <div className="profile-settings">
                <aside className="profile-settings__rail" aria-label="Profile 设置目录">
                    <div className="profile-settings__brand">
                        <strong>个人设置</strong>
                    </div>
                    <nav className="profile-settings__nav" aria-label="个人设置">
                        <NavLink to="/profile/info" className={({ isActive }) => cn("profile-settings__nav-item", isActive && "profile-settings__nav-item--active")}>
                            个人
                        </NavLink>
                        <NavLink to="/profile/memory" className={({ isActive }) => cn("profile-settings__nav-item", isActive && "profile-settings__nav-item--active")}>
                            记忆
                        </NavLink>
                        <NavLink to="/profile/config" className={({ isActive }) => cn("profile-settings__nav-item", isActive && "profile-settings__nav-item--active")}>
                            智能体
                        </NavLink>
                    </nav>
                    <BaseButton variant="outline" className="profile-settings__logout" type="button" onClick={handleLogout}>
                        退出登录
                    </BaseButton>
                </aside>
                <main className="profile-settings__content">
                    {isError ? (
                        <div className="status-card">
                            <strong>Profile 加载失败</strong>
                            <div className="qa-text">{errorMessage || "请重试后继续编辑。"}</div>
                            <div>
                                <BaseButton variant="soft" type="button" onClick={() => refetch()}>
                                    重试
                                </BaseButton>
                            </div>
                        </div>
                    ) : isLoading ? (
                        <LoadingCard />
                    ) : (
                        <Outlet context={outletContext} />
                    )}
                </main>
            </div>
        </div>
    );
}

export function ProfileIndexRedirect() {
    return <Navigate to="/profile/info" replace />;
}

export function ProfileInfoPage() {
    const { profile, currentUser } = useProfileSettingsContext();
    const saveMutation = useSaveProfileMutation();
    const uploadAvatarMutation = useUploadAvatarMutation();
    const changePasswordMutation = useChangePasswordMutation();
    const { showErrorDialog } = useGlobalErrorDialog();
    const personalForm = useForm<PersonalForm>({
        resolver: zodResolver(personalSchema),
        defaultValues: pickPersonalDefaults(profile),
    });
    const passwordForm = useForm<PasswordForm>({
        resolver: zodResolver(passwordSchema),
        defaultValues: defaultPassword,
    });

    useEffect(() => {
        personalForm.reset(pickPersonalDefaults(profile));
    }, [personalForm, profile]);

    const fileInputRef = useRef<HTMLInputElement>(null);
    const accountFieldsRef = useRef<HTMLDivElement>(null);
    const [avatarPreview, setAvatarPreview] = useState("");
    const avatarUrl = avatarPreview || currentUser?.avatar?.trim() || "";
    const [avatarSize, setAvatarSize] = useState<number | null>(null);
    const [cropSrc, setCropSrc] = useState("");
    const [crop, setCrop] = useState({ x: 0, y: 0 });
    const [zoom, setZoom] = useState(1);
    const [croppedArea, setCroppedArea] = useState<CropArea | null>(null);

    useEffect(() => {
        const fieldsNode = accountFieldsRef.current;
        if (!fieldsNode) return;

        const syncAvatarSize = () => {
            const nextSize = Math.round(fieldsNode.getBoundingClientRect().height);
            setAvatarSize((current) => (current === nextSize ? current : nextSize));
        };

        syncAvatarSize();

        if (typeof ResizeObserver === "undefined") {
            return;
        }

        const observer = new ResizeObserver(() => {
            syncAvatarSize();
        });
        observer.observe(fieldsNode);

        return () => {
            observer.disconnect();
        };
    }, []);

    const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file) return;
        if (!file.type.startsWith("image/")) {
            showErrorDialog({
                title: "文件类型错误",
                message: "请选择图片文件后再上传头像。",
            });
            return;
        }
        const url = URL.createObjectURL(file);
        setCropSrc(url);
        setCrop({ x: 0, y: 0 });
        setZoom(1);
        setCroppedArea(null);
    };

    const handleCropComplete = useCallback((_: CropArea, cropped: CropArea) => {
        setCroppedArea(cropped);
    }, []);

    const handleCropConfirm = async () => {
        if (!cropSrc || !croppedArea) return;
        try {
            const blob = await getCroppedImg(cropSrc, croppedArea);
            const file = new File([blob], "avatar.png", { type: "image/png" });
            setAvatarPreview(URL.createObjectURL(blob));
            await uploadAvatarMutation.mutateAsync(file);
            emitDasiBubble("📷 头像已更新，帅气！");
        } catch {
            showErrorDialog({
                title: "裁剪失败",
                message: "头像裁剪失败，请稍后重试。",
            });
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
        <div className="profile-pane">
            <section className="profile-section">
                <div className="profile-section__title">账户信息</div>
                <div className="profile-account">
                    <button
                        className="profile-avatar-editor"
                        type="button"
                        onClick={() => fileInputRef.current?.click()}
                        aria-label="更换头像"
                        style={avatarSize ? { width: `${avatarSize}px`, height: `${avatarSize}px` } : undefined}
                    >
                        {avatarUrl ? (
                            <img src={avatarUrl} alt="" />
                        ) : (
                            <span>{currentUser?.username?.charAt(0)?.toUpperCase() || "U"}</span>
                        )}
                    </button>
                    <input ref={fileInputRef} type="file" accept="image/*" style={{ display: "none" }} onChange={handleFileSelect} />
                    <div ref={accountFieldsRef} className="profile-account__fields">
                        <Field label="用户名">
                            <TextInput readOnly value={currentUser?.username ?? ""} />
                        </Field>
                        <Field label="邮箱">
                            <TextInput readOnly value={currentUser?.email ?? ""} />
                        </Field>
                    </div>
                </div>
            </section>

            <form
                className="profile-form"
                onSubmit={personalForm.handleSubmit(async (values) => {
                    await saveMutation.mutateAsync({ ...profile, ...values });
                    emitDasiBubble("✅ 信息已保存，Dasi 已经记录下来了。");
                })}
            >
                <section className="profile-section">
                    <div className="profile-section__title">求职信息</div>
                    <div className="profile-grid profile-grid--two">
                        <Field label="目标岗位" error={personalForm.formState.errors.targetRole?.message}>
                            <TextInput {...personalForm.register("targetRole")} />
                        </Field>
                        <Field label="目标领域" error={personalForm.formState.errors.targetDomain?.message}>
                            <TextInput {...personalForm.register("targetDomain")} />
                        </Field>
                        <Field label="目标公司" error={personalForm.formState.errors.targetCompany?.message}>
                            <TextInput {...personalForm.register("targetCompany")} />
                        </Field>
                        <Field label="当前阶段" error={personalForm.formState.errors.stage?.message}>
                            <TextInput {...personalForm.register("stage")} />
                        </Field>
                        <Field label="专业" error={personalForm.formState.errors.major?.message}>
                            <TextInput {...personalForm.register("major")} />
                        </Field>
                        <Field label="年级" error={personalForm.formState.errors.grade?.message}>
                            <TextInput {...personalForm.register("grade")} />
                        </Field>
                    </div>
                    <div className="profile-form__actions">
                        <BaseButton variant="primary" className="btn--profile-save" type="submit" disabled={saveMutation.isPending}>
                            {saveMutation.isPending ? "保存中" : "保存信息"}
                        </BaseButton>
                    </div>
                </section>
            </form>

            <form
                className="profile-form"
                onSubmit={passwordForm.handleSubmit(async (values) => {
                    await changePasswordMutation.mutateAsync({
                        currentPassword: values.currentPassword,
                        newPassword: values.newPassword,
                    });
                    emitDasiBubble("🔐 密码已更新，请妥善保管。");
                    passwordForm.reset(defaultPassword);
                })}
            >
                <section className="profile-section">
                    <div className="profile-section__title">修改密码</div>
                    <div className="profile-grid profile-grid--two">
                        <Field label="当前密码" error={passwordForm.formState.errors.currentPassword?.message}>
                            <TextInput type="password" {...passwordForm.register("currentPassword")} />
                        </Field>
                        <Field label="新密码" error={passwordForm.formState.errors.newPassword?.message}>
                            <TextInput type="password" {...passwordForm.register("newPassword")} />
                        </Field>
                        <Field label="确认新密码" error={passwordForm.formState.errors.confirmPassword?.message}>
                            <TextInput type="password" {...passwordForm.register("confirmPassword")} />
                        </Field>
                    </div>
                    <div className="profile-form__actions">
                        <BaseButton variant="primary" type="submit" disabled={changePasswordMutation.isPending}>
                            {changePasswordMutation.isPending ? "修改中" : "修改密码"}
                        </BaseButton>
                    </div>
                </section>
            </form>

            {cropSrc ? (
                <div className="quiz-action-sheet" role="presentation" onClick={handleCropCancel}>
                    <div className="modal-card" role="dialog" aria-modal="true" aria-label="裁剪" style={{ width: "min(520px, 100%)" }} onClick={(e) => e.stopPropagation()}>
                        <div className="modal-card__header">
                            <h3 className="modal-card__title">裁剪</h3>
                        </div>
                        <div className="modal-card__body" style={{ position: "relative", minHeight: 320 }}>
                            <Suspense
                                fallback={
                                    <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center" }}>
                                        <LoadingCard />
                                    </div>
                                }
                            >
                                <LazyProfileAvatarCropper
                                    image={cropSrc}
                                    crop={crop}
                                    zoom={zoom}
                                    onCropChange={setCrop}
                                    onZoomChange={setZoom}
                                    onCropComplete={handleCropComplete}
                                />
                            </Suspense>
                        </div>
                        <div className="modal-card__footer">
                            <div className="profile-form__actions">
                                <BaseButton variant="primary" type="button" disabled={uploadAvatarMutation.isPending} onClick={handleCropConfirm}>
                                    {uploadAvatarMutation.isPending ? "上传中" : "确认"}
                                </BaseButton>
                                <BaseButton variant="ghost" type="button" onClick={handleCropCancel}>
                                    取消
                                </BaseButton>
                            </div>
                        </div>
                    </div>
                </div>
            ) : null}
        </div>
    );
}

export function ProfileMemoryPage() {
    const { data = [], isLoading, isError, error, refetch } = useMemoryListQuery();
    const [selectedMemoryId, setSelectedMemoryId] = useState("");
    const [selectedMastery, setSelectedMastery] = useState<Set<string>>(new Set());
    const [selectedTargetTypes, setSelectedTargetTypes] = useState<Set<string>>(new Set());
    const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());

    const masteryOptions = useMemo(() => memoryGroups.map((g) => ({
        key: g.type,
        label: g.label,
        emoji: g.emoji,
        tone: g.tone,
        count: data.filter((item) => item.memoryType === g.type).length,
    })), [data]);

    const targetTypeOptions = useMemo(() => {
        const seen = new Set<string>();
        return data.filter((item) => {
            if (seen.has(item.targetType)) return false;
            seen.add(item.targetType);
            return true;
        }).map((item) => ({
            key: item.targetType,
            label: memoryTargetTypeLabel(item.targetType),
            emoji: "📦",
            tone: "module" as const,
            count: data.filter((i) => i.targetType === item.targetType).length,
        }));
    }, [data]);

    const isAllMastery = selectedMastery.size === 0;
    const isAllTargetTypes = selectedTargetTypes.size === 0;
    const isAllKeys = selectedKeys.size === 0;

    const selectedGroupMemories = useMemo(() => {
        return data.filter((item) => {
            if (!isAllMastery && !selectedMastery.has(item.memoryType)) return false;
            if (!isAllTargetTypes && !selectedTargetTypes.has(item.targetType)) return false;
            if (!isAllKeys && !selectedKeys.has(item.targetKey)) return false;
            return true;
        });
    }, [data, isAllMastery, selectedMastery, isAllTargetTypes, selectedTargetTypes, isAllKeys, selectedKeys]);

    const keyOptions = useMemo(() => {
        const base = data.filter((item) => {
            if (!isAllMastery && !selectedMastery.has(item.memoryType)) return false;
            if (!isAllTargetTypes && !selectedTargetTypes.has(item.targetType)) return false;
            return true;
        });
        const seen = new Set<string>();
        return base.filter((item) => {
            if (!item.targetKey || seen.has(item.targetKey)) return false;
            seen.add(item.targetKey);
            return true;
        }).map((item) => ({
            key: item.targetKey,
            count: base.filter((i) => i.targetKey === item.targetKey).length,
        }));
    }, [data, isAllMastery, selectedMastery, isAllTargetTypes, selectedTargetTypes]);

    const selectedMemory = selectedGroupMemories.find((item) => item.id === selectedMemoryId) ?? null;
    const detailQuery = useMemoryDetailQuery(selectedMemory?.id ?? "", { enabled: Boolean(selectedMemory?.id) });
    const hideMemoryMutation = useHideMemoryMutation();
    const { showErrorDialog } = useGlobalErrorDialog();

    useEffect(() => {
        setSelectedMemoryId("");
    }, [selectedMastery, selectedTargetTypes, selectedKeys]);

    useEffect(() => {
        if (!data.length) {
            setSelectedMemoryId("");
            return;
        }
        if (!selectedMemoryId || !selectedGroupMemories.some((item) => item.id === selectedMemoryId)) {
            setSelectedMemoryId("");
        }
    }, [data, selectedMemoryId, selectedGroupMemories]);

    const handleHideMemory = async (memory: UserMemory) => {
        try {
            await hideMemoryMutation.mutateAsync(memory.id);
            if (selectedMemoryId === memory.id) {
                setSelectedMemoryId("");
            }
        } catch (mutationError) {
            showErrorDialog({
                title: "隐藏记忆失败",
                message: mutationError instanceof Error ? mutationError.message : "请稍后重试。",
            });
        }
    };

    if (isLoading) {
        return <LoadingCard />;
    }

    return (
        <div className="profile-pane profile-pane--memory">
            {isError ? (
                <div className="status-card">
                    <strong>记忆加载失败</strong>
                    <div className="qa-text">{error instanceof Error ? error.message : "请重试后继续查看。"}</div>
                    <div>
                        <BaseButton variant="soft" type="button" onClick={() => refetch()}>
                            重试
                        </BaseButton>
                    </div>
                </div>
            ) : data.length === 0 ? (
                <div className="profile-empty-state">
                    <strong>暂无长期记忆</strong>
                    <p>完成练习评估后，系统会基于真实作答和评分沉淀学习画像。</p>
                </div>
            ) : (
                <div className="profile-memory">
                    <aside className="profile-memory__types" aria-label="学习画像筛选">
                        <div className="profile-memory__types-title">筛选</div>
                        <div className="profile-memory__filter-group">
                            <div className="profile-memory__filter-label">掌握程度</div>
                            <div className="profile-memory__filter-chips">
                                <button
                                    type="button"
                                    className={cn("profile-memory__chip", isAllMastery && "profile-memory__chip--active")}
                                    onClick={() => setSelectedMastery(new Set())}
                                >
                                    全部<span className="profile-memory__chip-count">{data.length}</span>
                                </button>
                                {masteryOptions.map((opt) => (
                                    <button
                                        key={opt.key}
                                        type="button"
                                        className={cn("profile-memory__chip", selectedMastery.has(opt.key) && "profile-memory__chip--active")}
                                        onClick={() => {
                                            const next = new Set(selectedMastery);
                                            if (next.has(opt.key)) { next.delete(opt.key); } else { next.add(opt.key); }
                                            setSelectedMastery(next);
                                        }}
                                    >
                                        {opt.emoji} <span className="profile-memory__chip-text">{opt.label}</span><span className="profile-memory__chip-count">{opt.count}</span>
                                    </button>
                                ))}
                            </div>
                        </div>
                        <div className="profile-memory__filter-divider" />
                        <div className="profile-memory__filter-group">
                            <div className="profile-memory__filter-label">对象类型</div>
                            <div className="profile-memory__filter-chips">
                                <button
                                    type="button"
                                    className={cn("profile-memory__chip", isAllTargetTypes && "profile-memory__chip--active")}
                                    onClick={() => setSelectedTargetTypes(new Set())}
                                >
                                    全部<span className="profile-memory__chip-count">{data.length}</span>
                                </button>
                                {targetTypeOptions.map((opt) => (
                                    <button
                                        key={opt.key}
                                        type="button"
                                        className={cn("profile-memory__chip", selectedTargetTypes.has(opt.key) && "profile-memory__chip--active")}
                                        onClick={() => {
                                            const next = new Set(selectedTargetTypes);
                                            if (next.has(opt.key)) { next.delete(opt.key); } else { next.add(opt.key); }
                                            setSelectedTargetTypes(next);
                                        }}
                                    >
                                        {opt.emoji} <span className="profile-memory__chip-text">{opt.label}</span><span className="profile-memory__chip-count">{opt.count}</span>
                                    </button>
                                ))}
                            </div>
                        </div>
                        <div className="profile-memory__filter-divider" />
                        <div className="profile-memory__filter-group">
                            <div className="profile-memory__filter-label">对象名称</div>
                            <div className="profile-memory__filter-chips">
                                <button
                                    type="button"
                                    className={cn("profile-memory__chip", isAllKeys && "profile-memory__chip--active")}
                                    onClick={() => setSelectedKeys(new Set())}
                                >
                                    全部<span className="profile-memory__chip-count">{selectedGroupMemories.length}</span>
                                </button>
                                {keyOptions.map((opt) => (
                                    <button
                                        key={opt.key}
                                        type="button"
                                        className={cn("profile-memory__chip", selectedKeys.has(opt.key) && "profile-memory__chip--active")}
                                        onClick={() => {
                                            const next = new Set(selectedKeys);
                                            if (next.has(opt.key)) { next.delete(opt.key); } else { next.add(opt.key); }
                                            setSelectedKeys(next);
                                        }}
                                    >
                                        <span className="profile-memory__chip-text">{opt.key}</span><span className="profile-memory__chip-count">{opt.count}</span>
                                    </button>
                                ))}
                            </div>
                        </div>
                    </aside>
                    <section className="profile-memory__list" aria-label="长期记忆列表">
                        <div className="profile-memory__scroll">
                            {selectedGroupMemories.length === 0 ? (
                                <div className="profile-empty-state profile-empty-state--compact">
                                    <strong>当前分类暂无画像</strong>
                                    <p>可以切换其它分类查看已有记忆。</p>
                                </div>
                            ) : selectedGroupMemories.map((memory) => (
                                <button
                                    key={memory.id}
                                    type="button"
                                    className={cn("profile-memory-card", selectedMemory?.id === memory.id && "profile-memory-card--active")}
                                    onClick={() => setSelectedMemoryId(memory.id)}
                                >
                                    <strong>{memoryTitle(memory)}</strong>
                                    <p>{memory.summary || memory.content}</p>
                                    <div className="profile-memory-card__stats">
                                        <span>证据 {memory.supportCount}</span>
                                        <span>{formatDateTime(memory.lastSeenAt)}</span>
                                    </div>
                                </button>
                            ))}
                        </div>
                    </section>
                    <div className="profile-memory__detail">
                        {detailQuery.isLoading ? (
                            <LoadingCard />
                        ) : detailQuery.data?.memory ? (
                            <>
                                <div className="profile-memory-detail__header">
                                    <h2>{memoryTitle(detailQuery.data.memory)}</h2>
                                </div>
                                <p className="profile-memory-detail__summary">{detailQuery.data.memory.content}</p>
                                <BaseButton
                                    variant="outline"
                                    type="button"
                                    disabled={hideMemoryMutation.isPending}
                                    onClick={() => handleHideMemory(detailQuery.data.memory)}
                                >
                                    隐藏记忆
                                </BaseButton>
                                <section className="profile-section profile-memory-evidence-section">
                                    <div className="profile-section__title">证据记录</div>
                                    <div className="profile-memory-evidence">
                                        {detailQuery.data.evidenceList.length === 0 ? (
                                            <p>暂无证据记录。</p>
                                        ) : detailQuery.data.evidenceList.map((evidence) => {
                                            const meta = memoryEvidenceResultMeta(evidence);
                                            return (
                                            <div key={evidence.id} className={cn("profile-memory-evidence__item", `profile-memory-evidence__item--${meta.tone}`)}>
                                                <div className="profile-memory-evidence__result">
                                                    <strong>{evidence.score == null ? "-" : `${evidence.score} 分`}</strong>
                                                    <span className={cn("profile-memory-result", `profile-memory-result--${meta.tone}`)}>{meta.label}</span>
                                                </div>
                                                <strong>{evidence.questionSnapshot || "题目快照缺失"}</strong>
                                                <p>{evidence.evidenceSummary}</p>
                                                <div className="profile-memory-card__stats">
                                                    <span>{evidence.moduleTag || "未标记模块"}</span>
                                                    <span>{formatDateTime(evidence.createdAt ?? "")}</span>
                                                </div>
                                            </div>
                                        );
                                        })}
                                    </div>
                                </section>
                            </>
                        ) : (
                            <div className="profile-memory__placeholder">
                                <div>📋</div>
                                <div>请选择一条记忆卡片查看详情</div>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}

export function ProfileConfigPage() {
    const { profile } = useProfileSettingsContext();
    const saveMutation = useSaveProfileMutation();
    const form = useForm<AgentForm>({
        resolver: zodResolver(agentSchema),
        defaultValues: pickAgentDefaults(profile),
    });

    useEffect(() => {
        form.reset(pickAgentDefaults(profile));
    }, [form, profile]);

    const allowReferMemory = form.watch("allowReferMemory");
    const allowWebSearch = form.watch("allowWebSearch");
    const allowFallback = form.watch("allowFallback");

    return (
        <form
            className="profile-pane"
            onSubmit={form.handleSubmit(async (values) => {
                await saveMutation.mutateAsync({ ...profile, ...values });
                emitDasiBubble("✅ 配置已保存，模型参数已生效。");
            })}
        >
            <section className="profile-section">
                <div className="profile-section__title">模型配置</div>
                <div className="profile-grid">
                    <Field label="Base URL">
                        <TextInput placeholder="接口需要遵循 OpenAI 协议" {...form.register("llmBaseUrl")} />
                    </Field>
                    <Field label="API Key">
                        <TextInput type="password" {...form.register("llmApiKey")} />
                    </Field>
                    <Field label="模型名称">
                        <TextInput {...form.register("llmModelName")} />
                    </Field>
                </div>
            </section>

            <section className="profile-section">
                <div className="profile-section__title">风格提示词</div>
                <div className="profile-grid">
                    <Field label="答案风格" error={form.formState.errors.answerStyle?.message}>
                        <textarea className="textarea profile-textarea" rows={4} {...form.register("answerStyle")} />
                    </Field>
                    <Field label="反馈风格" error={form.formState.errors.feedbackStyle?.message}>
                        <textarea className="textarea profile-textarea" rows={4} {...form.register("feedbackStyle")} />
                    </Field>
                </div>
            </section>

            <section className="profile-section">
                <div className="profile-section__title">能力开关</div>
                <button
                    type="button"
                    className={cn("profile-switch", allowReferMemory && "profile-switch--active")}
                    onClick={() => form.setValue("allowReferMemory", !allowReferMemory, { shouldDirty: true })}
                >
                    <span className="profile-switch__copy">
                        <strong>参考记忆</strong>
                        <small>允许生成规划参考长期记忆，调整模块题量、难度起点和检索方向。</small>
                    </span>
                    <span className="profile-switch__track" aria-hidden="true">
                        <span className="profile-switch__thumb" />
                    </span>
                </button>
                <button
                    type="button"
                    className={cn("profile-switch", allowWebSearch && "profile-switch--active")}
                    onClick={() => form.setValue("allowWebSearch", !allowWebSearch, { shouldDirty: true })}
                >
                    <span className="profile-switch__copy">
                        <strong>面经搜索</strong>
                        <small>允许生成链路补充网络搜索面经结果作为额外参考。</small>
                    </span>
                    <span className="profile-switch__track" aria-hidden="true">
                        <span className="profile-switch__thumb" />
                    </span>
                </button>
                <button
                    type="button"
                    className={cn("profile-switch", allowFallback && "profile-switch--active")}
                    onClick={() => form.setValue("allowFallback", !allowFallback, { shouldDirty: true })}
                >
                    <span className="profile-switch__copy">
                        <strong>错误回退</strong>
                        <small>LLM 出现错误未知错误时，允许使用 FALLBACK 处理而不是直接返回错误。</small>
                    </span>
                    <span className="profile-switch__track" aria-hidden="true">
                        <span className="profile-switch__thumb" />
                    </span>
                </button>
            </section>

            <div className="profile-form__actions">
                <BaseButton variant="primary" className="btn--profile-save" type="submit" disabled={saveMutation.isPending}>
                    {saveMutation.isPending ? "保存中" : "保存智能体配置"}
                </BaseButton>
            </div>
        </form>
    );
}

function pickPersonalDefaults(profile: Profile): PersonalForm {
    return {
        targetRole: profile.targetRole,
        targetDomain: profile.targetDomain,
        targetCompany: profile.targetCompany,
        grade: profile.grade,
        major: profile.major,
        stage: profile.stage,
    };
}

function pickAgentDefaults(profile: Profile): AgentForm {
    return {
        allowReferMemory: profile.allowReferMemory,
        allowWebSearch: profile.allowWebSearch,
        allowFallback: profile.allowFallback,
        answerStyle: profile.answerStyle,
        feedbackStyle: profile.feedbackStyle,
        llmBaseUrl: profile.llmBaseUrl,
        llmApiKey: profile.llmApiKey,
        llmModelName: profile.llmModelName,
    };
}

function memoryTitle(memory: UserMemory) {
    return memory.targetKey || "未命名对象";
}

function memoryTargetTypeLabel(targetType: string) {
    if (targetType === "MODULE_TAG") {
        return "知识模块";
    }
    if (targetType === "ANSWER_SKILL") {
        return "回答能力";
    }
    return targetType || "目标对象";
}

function memoryEvidenceResultMeta(evidence: UserMemoryEvidence) {
    const raw = (evidence.result || "").toUpperCase();
    if (raw === "PERFECT") {
        return { label: "完美", tone: "perfect" };
    }
    if (raw === "CORRECT") {
        return { label: "正确", tone: "correct" };
    }
    if (raw === "DEFICIENT") {
        return { label: "缺漏", tone: "deficient" };
    }
    if (raw === "WRONG") {
        return { label: "错误", tone: "wrong" };
    }
    if (raw === "UNKNOWN") {
        return { label: "不会", tone: "unknown" };
    }
    return { label: raw || "未评分", tone: "pending" };
}

function formatDateTime(value: string) {
    if (!value) {
        return "暂无";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value.replace("T", " ");
    }
    return date.toLocaleString("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
    });
}
