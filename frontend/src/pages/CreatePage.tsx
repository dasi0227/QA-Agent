import { useMemo, useState } from "react";
import { BaseButton, ChoiceButton, LinkButton } from "@/components/base/button";
import { GlassCard, MetricCard } from "@/components/base/card";
import { Chip, Tag } from "@/components/base/tag";
import { useDocumentsQuery, useProfileQuery } from "@/lib/api/hooks";

export function CreatePage() {
    const profileQuery = useProfileQuery();
    const documentsQuery = useDocumentsQuery();
    const [selectedDocumentIds, setSelectedDocumentIds] = useState<string[]>([]);

    const uploadedDocuments = documentsQuery.data ?? [];
    const selectedDocuments = useMemo(
        () => uploadedDocuments.filter((item) => selectedDocumentIds.includes(item.id)),
        [selectedDocumentIds, uploadedDocuments],
    );
    const selectedDocumentChips = useMemo(
        () => selectedDocuments.map((item) => item.fileName),
        [selectedDocuments],
    );

    return (
        <div className="page-frame">
            <GlassCard className="panel" style={{ width: "min(1180px, 86vw)", margin: "0 auto", padding: 22 }}>
                <div className="page-grid">
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
                        当前状态
                    </div>

                    <div className="qa-feedback">
                        <strong>第一版当前仅打通资产查询与维护</strong>
                        <div className="qa-text">问答集生成任务、资料上传链路暂未接入，本页仅保留资料范围预览与能力说明。</div>
                    </div>

                    <div style={{ display: "flex", justifyContent: "space-between", gap: 16, flexWrap: "wrap", marginTop: 4 }}>
                        <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                            <Tag>问答规模 · 中等</Tag>
                            <Tag>补充通用知识 · {profileQuery.data?.allowGeneralKnowledge ? "允许" : "关闭"}</Tag>
                            <Tag>答案风格 · {profileQuery.data?.answerStyle || "口语化"}</Tag>
                            <Tag>已选资料 · {selectedDocumentChips.length}</Tag>
                        </div>
                        <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                            <BaseButton variant="ghost" type="button" disabled>
                                资料上传未接入
                            </BaseButton>
                            <BaseButton variant="primary" type="button" disabled>
                                生成任务未接入
                            </BaseButton>
                        </div>
                    </div>

                    {profileQuery.isError ? (
                        <div className="page-copy" style={{ color: "var(--ink)" }}>
                            Profile 加载失败：{profileQuery.error instanceof Error ? profileQuery.error.message : "请重试"}
                        </div>
                    ) : null}

                    <div className="result-grid" style={{ gridTemplateColumns: "repeat(3, minmax(0, 1fr))" }}>
                        <MetricCard label="状态" value="已降级" />
                        <MetricCard label="资料" value={`${selectedDocumentChips.length}`} />
                        <MetricCard label="链路" value="未接入" />
                    </div>

                    <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                        <LinkButton to="/repository" variant="primary">
                            去仓库维护资产
                        </LinkButton>
                        <BaseButton variant="soft" type="button" disabled>
                            生成问答集未接入
                        </BaseButton>
                    </div>
                </div>
            </GlassCard>
        </div>
    );
}
