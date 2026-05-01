import { zodResolver } from "@hookform/resolvers/zod";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { ArrowUp, Mic } from "lucide-react";
import { BaseButton, ChoiceButton, LinkButton } from "@/components/base/button";
import { GlassCard, MetricCard } from "@/components/base/card";
import { TextArea } from "@/components/base/field";
import { Chip, Tag } from "@/components/base/tag";
import {
    useDocumentsQuery,
    useGenerateQuestionSetMutation,
    useGenerationTaskQuery,
    useProfileQuery,
} from "@/lib/api/hooks";

const createSchema = z.object({
    note: z.string().min(1, "请输入本轮补充说明"),
});

type CreateForm = z.infer<typeof createSchema>;

export function CreatePage() {
    const profileQuery = useProfileQuery();
    const documentsQuery = useDocumentsQuery();
    const generateQuestionSetMutation = useGenerateQuestionSetMutation();
    const [selectedDocumentIds, setSelectedDocumentIds] = useState<string[]>([]);
    const [taskId, setTaskId] = useState("");
    const taskQuery = useGenerationTaskQuery(taskId || undefined);
    const [taskError, setTaskError] = useState("");

    const form = useForm<CreateForm>({
        resolver: zodResolver(createSchema),
        defaultValues: {
            note: "优先覆盖项目经历和 Redis 高频追问，答案风格偏口语化但逻辑清晰。",
        },
    });

    const uploadedDocuments = documentsQuery.data ?? [];
    const selectedDocuments = useMemo(
        () => uploadedDocuments.filter((item) => selectedDocumentIds.includes(item.id)),
        [selectedDocumentIds, uploadedDocuments],
    );
    const selectedDocumentChips = useMemo(
        () => selectedDocuments.map((item) => item.fileName),
        [selectedDocuments],
    );

    const generationStage = taskQuery.data?.stage ?? "QUEUED";
    const generationMessage = taskQuery.data?.message ?? "";
    const generationProgress = taskQuery.data?.progress ?? 0;
    const stageSteps = [
        { key: "PARSING", label: "资料解析", copy: "确认本轮资料范围，准备进入生成流程。" },
        { key: "PLANNING", label: "规划模块", copy: "按模块和题量分配生成计划。" },
        { key: "GENERATING", label: "检索起草", copy: "基于 RAG 证据起草结构化问答项。" },
        { key: "VALIDATING", label: "结构校验", copy: "检查 schema、证据引用和字段完整性。" },
        { key: "OPTIMIZING", label: "结果收口", copy: "准备落库并生成正式问答集。" },
        { key: "COMPLETED", label: "生成完成", copy: "问答集已经可进入仓库和练习链路。" },
    ] as const;
    const stageIndex = stageSteps.findIndex((item) => item.key === generationStage);

    return (
        <div className="page-frame">
            {taskId ? (
                <GlassCard className="hero-card" style={{ width: "min(1180px, 86vw)" }}>
                    <section className="timeline">
                        {stageSteps.map((activity, index) => (
                            <div key={activity.key} className="timeline__item">
                                <div className="timeline__meta">
                                    {taskQuery.data?.status === "FAILED" && generationStage === activity.key
                                        ? "失败"
                                        : stageIndex >= index && taskQuery.data
                                            ? "已到达"
                                            : "待执行"}
                                </div>
                                <div className="timeline__title">{activity.label}</div>
                                <div className="timeline__copy">
                                    {generationStage === activity.key && generationMessage
                                        ? generationMessage
                                        : activity.copy}
                                </div>
                                {generationStage === activity.key ? <Chip className="fade-in">{activity.key}</Chip> : null}
                            </div>
                        ))}
                    </section>
                </GlassCard>
            ) : null}

            <GlassCard className="panel" style={{ width: "min(1180px, 86vw)", margin: "0 auto", padding: 22 }}>
                <form
                    className="page-grid"
                    onSubmit={form.handleSubmit(async (values) => {
                        setTaskError("");
                        if (selectedDocumentIds.length === 0) {
                            setTaskError("请先在资料库中勾选本次要使用的资料。");
                            return;
                        }

                        const hiddenTitle = selectedDocuments.length
                            ? selectedDocuments.map((item) => item.fileName.replace(/\.[^.]+$/, "")).join(" / ")
                            : "技术面试问答集";

                        const task = await generateQuestionSetMutation.mutateAsync({
                            sourceDocumentIds: selectedDocumentIds,
                            note: values.note,
                            title: hiddenTitle,
                            allowGeneralKnowledge: profileQuery.data?.allowGeneralKnowledge ?? true,
                        });
                        setTaskId(task.id);
                    })}
                >
                    <div style={{ display: "flex", flexWrap: "wrap", gap: 10 }}>
                        {selectedDocumentChips.map((fileName) => <Chip key={fileName}>{fileName}</Chip>)}
                    </div>

                    <div className="page-copy" style={{ marginTop: -4 }}>
                        本次资料范围
                    </div>

                    <div className="selection-panel">
                        <div className="selection-panel__body">
                            {documentsQuery.isLoading ? <Chip>资料加载中</Chip> : null}
                            {uploadedDocuments.map((document) => {
                                const selected = selectedDocumentIds.includes(document.id);
                                return (
                                    <ChoiceButton
                                        key={document.id}
                                        selected={selected}
                                        className="selection-chip"
                                        onClick={() => {
                                            setSelectedDocumentIds((current) => {
                                                if (current.includes(document.id)) {
                                                    return current.filter((item) => item !== document.id);
                                                }
                                                return [...current, document.id];
                                            });
                                        }}
                                    >
                                        <span>{document.fileName}</span>
                                    </ChoiceButton>
                                );
                            })}
                            {!documentsQuery.isLoading && uploadedDocuments.length === 0 ? (
                                <Chip>资料库里还没有可复用资料</Chip>
                            ) : null}
                        </div>
                    </div>

                    <div className="page-copy" style={{ marginTop: -4 }}>
                        本次生成补充说明
                    </div>

                    <TextArea {...form.register("note")} placeholder="补充说明" />
                    {form.formState.errors.note?.message ? (
                        <div className="field__error">{form.formState.errors.note.message}</div>
                    ) : null}

                    <div style={{ display: "flex", justifyContent: "space-between", gap: 16, flexWrap: "wrap", marginTop: 4 }}>
                        <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                            <Tag>问答规模 · 中等</Tag>
                            <Tag>
                                补充通用知识 · {profileQuery.data?.allowGeneralKnowledge ? "允许" : "关闭"}
                            </Tag>
                            <Tag>答案风格 · {profileQuery.data?.answerStyle || "口语化"}</Tag>
                            <Tag>已选资料 · {selectedDocumentChips.length}</Tag>
                        </div>
                        <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                            <button className="btn btn--ghost" type="button" aria-label="语音输入">
                                <Mic size={16} strokeWidth={2} />
                            </button>
                            <button className="btn btn--primary" type="submit" aria-label="发送">
                                <ArrowUp size={16} strokeWidth={2} />
                            </button>
                        </div>
                    </div>

                    {taskError ? <div className="page-copy" style={{ color: "var(--ink)" }}>{taskError}</div> : null}
                    {profileQuery.isError ? (
                        <div className="page-copy" style={{ color: "var(--ink)" }}>
                            Profile 加载失败：{profileQuery.error instanceof Error ? profileQuery.error.message : "请重试"}
                        </div>
                    ) : null}
                    {taskQuery.isLoading || taskQuery.data ? (
                        <div className="qa-feedback">
                            <div className="sidebar__split">
                                <strong>生成进度</strong>
                                <Chip>{generationStage}</Chip>
                            </div>
                            <div className="qa-text">
                                {generationMessage || "任务状态会通过轮询更新，完成后自动进入仓库。"}
                            </div>
                            <div className="result-grid" style={{ gridTemplateColumns: "repeat(3, minmax(0, 1fr))" }}>
                                <MetricCard label="进度" value={`${generationProgress}%`} />
                                <MetricCard label="任务" value={taskQuery.data?.status ?? "等待中"} />
                                <MetricCard label="资料" value={`${taskQuery.data?.documentNames?.length ?? selectedDocumentChips.length}`} />
                            </div>
                            {taskQuery.data ? (
                                <div className="result-grid" style={{ gridTemplateColumns: "repeat(2, minmax(0, 1fr))" }}>
                                    <MetricCard label="标题" value={taskQuery.data.title || "未命名"} detail="本轮生成标题" />
                                    <MetricCard
                                        label="题量"
                                        value={`${taskQuery.data.requestedQuestionCount ?? 0}`}
                                        detail={`通用知识：${taskQuery.data.allowGeneralKnowledge ? "允许" : "关闭"}`}
                                    />
                                </div>
                            ) : null}
                            {taskQuery.data?.documentNames?.length ? (
                                <div>
                                    <strong style={{ display: "block", marginBottom: 8 }}>本轮资料</strong>
                                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                                        {taskQuery.data.documentNames.map((item) => (
                                            <Tag key={item}>{item}</Tag>
                                        ))}
                                    </div>
                                </div>
                            ) : null}
                            {taskQuery.data?.status === "COMPLETED" && taskQuery.data.questionSetId ? (
                                <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                                    <LinkButton to={`/repository/${taskQuery.data.questionSetId}`} variant="primary">
                                        查看问答集
                                    </LinkButton>
                                    <LinkButton to={`/quiz?questionSetId=${taskQuery.data.questionSetId}`} variant="soft">
                                        开始测试
                                    </LinkButton>
                                </div>
                            ) : null}
                            {taskQuery.data?.status === "FAILED" ? (
                                <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                                    <BaseButton variant="soft" type="button" onClick={() => taskQuery.refetch()}>
                                        重新拉取状态
                                    </BaseButton>
                                    <LinkButton to="/repository" variant="ghost">
                                        回仓库
                                    </LinkButton>
                                </div>
                            ) : null}
                        </div>
                    ) : null}

                    {taskQuery.isError ? (
                        <div className="qa-feedback">
                            <strong>任务状态加载失败</strong>
                            <div className="qa-text">
                                {taskQuery.error instanceof Error ? taskQuery.error.message : "请稍后重试。"}
                            </div>
                            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                                <BaseButton variant="soft" type="button" onClick={() => taskQuery.refetch()}>
                                    重试
                                </BaseButton>
                                <LinkButton to="/repository" variant="ghost">
                                    回仓库
                                </LinkButton>
                            </div>
                        </div>
                    ) : null}
                </form>
            </GlassCard>

        </div>
    );
}
