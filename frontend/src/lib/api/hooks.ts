import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest, ApiError } from "./client";
import type {
    AuthSession,
    AuthUser,
    CreateQuestionItemInput,
    DeleteQuestionItemInput,
    DocumentRecord,
    LoginInput,
    Profile,
    QuestionItem,
    QuestionItemDraft,
    QuestionSet,
    RegisterInput,
    UpdateQuestionItemInput,
    UpdateQuestionSetInput,
} from "./types";
import { setAuthSession } from "../auth";

const isObject = (value: unknown): value is Record<string, unknown> => typeof value === "object" && value !== null;

const pick = (value: unknown, ...keys: string[]) => {
    if (!isObject(value)) {
        return undefined;
    }
    for (const key of keys) {
        if (key in value) {
            return value[key];
        }
    }
    return undefined;
};

const toStringValue = (value: unknown, fallback = "") => {
    if (typeof value === "string") {
        return value;
    }
    if (typeof value === "number" || typeof value === "boolean") {
        return String(value);
    }
    return fallback;
};

const toNumberValue = (value: unknown, fallback = 0) => {
    if (typeof value === "number" && Number.isFinite(value)) {
        return value;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
};

const toBooleanValue = (value: unknown, fallback = false) => {
    if (typeof value === "boolean") {
        return value;
    }
    if (typeof value === "string") {
        return value === "true";
    }
    return fallback;
};

export const apiKeys = {
    currentUser: ["auth", "me"] as const,
    profile: ["profile"] as const,
    documents: ["documents"] as const,
    document: (id: string) => ["documents", id] as const,
    questionSets: ["question-sets"] as const,
    questionSet: (id: string) => ["question-sets", id] as const,
    questionSetItems: (id: string) => ["question-sets", id, "items"] as const,
} as const;

type QueryControlOptions = {
    enabled?: boolean;
};

export function normalizeAuthUser(raw: unknown): AuthUser {
    return {
        id: toStringValue(pick(raw, "id", "userId", "user_id")),
        username: toStringValue(pick(raw, "username")),
        email: toStringValue(pick(raw, "email")),
        avatar: toStringValue(pick(raw, "avatar")),
        status: toStringValue(pick(raw, "status"), "ACTIVE"),
        profileCompleted: toBooleanValue(pick(raw, "profileCompleted", "profile_completed"), false),
    };
}

export function normalizeProfile(raw: unknown): Profile {
    return {
        targetRole: toStringValue(pick(raw, "targetRole", "target_role")),
        targetDomain: toStringValue(pick(raw, "targetDomain", "target_domain")),
        targetCompany: toStringValue(pick(raw, "targetCompany", "target_company")),
        allowGeneralKnowledge: toBooleanValue(pick(raw, "allowGeneralKnowledge", "allow_general_knowledge")),
        allowWebSearch: toBooleanValue(pick(raw, "allowWebSearch", "allow_web_search")),
        answerStyle: toStringValue(pick(raw, "answerStyle", "answer_style")),
        feedbackStyle: toStringValue(pick(raw, "feedbackStyle", "feedback_style")),
        age: toStringValue(pick(raw, "age")),
        grade: toStringValue(pick(raw, "grade")),
        major: toStringValue(pick(raw, "major")),
        stage: toStringValue(pick(raw, "stage")),
    };
}

export function normalizeDocument(raw: unknown): DocumentRecord {
    return {
        id: toStringValue(pick(raw, "id")),
        fileName: toStringValue(pick(raw, "fileName", "file_name")),
        fileType: toStringValue(pick(raw, "fileType", "file_type")),
        filePath: toStringValue(pick(raw, "filePath", "file_path")),
        rawContent: toStringValue(pick(raw, "rawContent", "raw_content")),
        normalizedContent: toStringValue(pick(raw, "normalizedContent", "normalized_content")),
        summary: toStringValue(pick(raw, "summary")),
        moduleTagsJson: toStringValue(pick(raw, "moduleTagsJson", "module_tags_json")),
        referenceCount: toNumberValue(pick(raw, "referenceCount", "reference_count")),
        deleted: toBooleanValue(pick(raw, "deleted")),
        createdAt: toStringValue(pick(raw, "createdAt", "created_at")),
        updatedAt: toStringValue(pick(raw, "updatedAt", "updated_at")),
    };
}

export function normalizeQuestionSet(raw: unknown): QuestionSet {
    return {
        id: toStringValue(pick(raw, "id")),
        taskId: toStringValue(pick(raw, "taskId", "task_id")),
        title: toStringValue(pick(raw, "title")),
        description: toStringValue(pick(raw, "description")),
        moduleTagsJson: toStringValue(pick(raw, "moduleTagsJson", "module_tags_json")),
        questionCount: toNumberValue(pick(raw, "questionCount", "question_count")),
        practiceCount: toNumberValue(pick(raw, "practiceCount", "practice_count")),
        averageScore: toNumberValue(pick(raw, "averageScore", "average_score")),
        bestScore: toNumberValue(pick(raw, "bestScore", "best_score")),
        averageAccuracy: toNumberValue(pick(raw, "averageAccuracy", "average_accuracy")),
        bestAccuracy: toNumberValue(pick(raw, "bestAccuracy", "best_accuracy")),
        lastPracticedAt: toStringValue(pick(raw, "lastPracticedAt", "last_practiced_at")),
        createdAt: toStringValue(pick(raw, "createdAt", "created_at")),
        updatedAt: toStringValue(pick(raw, "updatedAt", "updated_at")),
    };
}

export function normalizeQuestionItem(raw: unknown): QuestionItem {
    return {
        id: toStringValue(pick(raw, "id")),
        qaSetId: toStringValue(pick(raw, "qaSetId", "qa_set_id")),
        question: toStringValue(pick(raw, "question")),
        knowledgeNote: toStringValue(pick(raw, "knowledgeNote", "knowledge_note")),
        answer: toStringValue(pick(raw, "answer")),
        moduleTag: toStringValue(pick(raw, "moduleTag", "module_tag")),
        difficulty: toStringValue(pick(raw, "difficulty")),
        conflictTip: toStringValue(pick(raw, "conflictTip", "conflict_tip")),
        sourceChunkIdsJson: toStringValue(pick(raw, "sourceChunkIdsJson", "source_chunk_ids_json")),
        sortOrder: toNumberValue(pick(raw, "sortOrder", "sort_order")),
    };
}

export function parseModuleTags(value?: string) {
    if (!value?.trim()) {
        return [];
    }
    try {
        const parsed = JSON.parse(value);
        return Array.isArray(parsed) ? parsed.map((item) => toStringValue(item)).filter(Boolean) : [];
    } catch {
        return value.split(",").map((item) => item.trim()).filter(Boolean);
    }
}

function toProfilePayload(profile: Profile) {
    return {
        targetRole: profile.targetRole,
        targetDomain: profile.targetDomain,
        targetCompany: profile.targetCompany,
        allowGeneralKnowledge: profile.allowGeneralKnowledge,
        allowWebSearch: profile.allowWebSearch,
        answerStyle: profile.answerStyle,
        feedbackStyle: profile.feedbackStyle,
        age: profile.age,
        grade: profile.grade,
        major: profile.major,
        stage: profile.stage,
    };
}

function toQuestionSetPayload(input: UpdateQuestionSetInput) {
    return {
        id: input.questionSetId,
        title: input.title,
        description: input.description,
    };
}

function toQuestionItemPayload(input: QuestionItemDraft & { qaSetId?: string; questionItemId?: string }) {
    return {
        id: input.questionItemId,
        qaSetId: input.qaSetId,
        question: input.question,
        knowledgeNote: input.knowledgeNote,
        answer: input.answer,
        moduleTag: input.moduleTag,
        difficulty: input.difficulty,
        conflictTip: input.conflictTip,
        sourceChunkIdsJson: input.sourceChunkIdsJson,
    };
}

export function useCurrentUserQuery(options: QueryControlOptions = {}) {
    return useQuery({
        queryKey: apiKeys.currentUser,
        enabled: options.enabled ?? true,
        retry: false,
        queryFn: async () => normalizeAuthUser(await apiRequest<AuthSession>("/auth/me")),
    });
}

export function useProfileQuery() {
    return useQuery({
        queryKey: apiKeys.profile,
        queryFn: async () => {
            try {
                return normalizeProfile(await apiRequest<unknown>("/user-profile/me"));
            } catch (error) {
                if (error instanceof ApiError && error.code === "40400") {
                    return null;
                }
                throw error;
            }
        },
    });
}

export function useSaveProfileMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (profile: Profile) => {
            try {
                return normalizeProfile(await apiRequest<unknown>("/user-profile/update", {
                    method: "POST",
                    body: toProfilePayload(profile),
                }));
            } catch (error) {
                if (error instanceof ApiError && error.code === "40400") {
                    return normalizeProfile(await apiRequest<unknown>("/user-profile/create", {
                        method: "POST",
                        body: toProfilePayload(profile),
                    }));
                }
                throw error;
            }
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.profile });
            await queryClient.invalidateQueries({ queryKey: apiKeys.currentUser });
        },
    });
}

export function useLoginMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: LoginInput) => {
            const session = await apiRequest<AuthSession>("/auth/login", {
                method: "POST",
                body: {
                    username: input.account,
                    password: input.password,
                },
                auth: false,
            });
            const token = session.accessToken ?? "";
            const refreshToken = session.refreshToken ?? "";
            if (token) {
                setAuthSession({
                    token,
                    refreshToken,
                    remember: input.remember ?? true,
                    user: normalizeAuthUser(session),
                });
                queryClient.clear();
            }
            return {
                token,
                user: normalizeAuthUser(session),
            };
        },
    });
}

export function useRegisterMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: RegisterInput) => {
            const session = await apiRequest<AuthSession>("/auth/register", {
                method: "POST",
                body: {
                    username: input.name,
                    email: input.email,
                    password: input.password,
                },
                auth: false,
            });
            const token = session.accessToken ?? "";
            const refreshToken = session.refreshToken ?? "";
            if (token) {
                setAuthSession({
                    token,
                    refreshToken,
                    remember: input.remember ?? true,
                    user: normalizeAuthUser(session),
                });
                queryClient.clear();
            }
            return {
                token,
                user: normalizeAuthUser(session),
            };
        },
    });
}

export function useDocumentsQuery(options: QueryControlOptions = {}) {
    return useQuery({
        queryKey: apiKeys.documents,
        enabled: options.enabled ?? true,
        queryFn: async () => (await apiRequest<unknown[]>("/source-document/query", {
            method: "POST",
            body: {},
        })).map(normalizeDocument),
    });
}

export function useDocumentQuery(documentId?: string) {
    return useQuery({
        queryKey: apiKeys.document(documentId ?? ""),
        enabled: Boolean(documentId),
        queryFn: async () => normalizeDocument(await apiRequest<unknown>("/source-document/detail", {
            query: { id: documentId ?? "" },
        })),
    });
}

export function useUpdateDocumentMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (document: DocumentRecord) => normalizeDocument(await apiRequest<unknown>("/source-document/update", {
            method: "POST",
            body: {
                id: document.id,
                fileName: document.fileName,
                fileType: document.fileType,
                filePath: document.filePath,
                rawContent: document.rawContent,
                normalizedContent: document.normalizedContent,
                summary: document.summary,
                moduleTagsJson: document.moduleTagsJson,
                referenceCount: document.referenceCount,
                deleted: document.deleted,
            },
        })),
        onSuccess: async (document) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.documents });
            await queryClient.invalidateQueries({ queryKey: apiKeys.document(document.id) });
        },
    });
}

export function useDeleteDocumentMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (documentId: string) => {
            await apiRequest<void>("/source-document/delete", {
                method: "POST",
                body: { id: documentId },
            });
            return documentId;
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.documents });
        },
    });
}

export function useQuestionSetsQuery(options: QueryControlOptions = {}) {
    return useQuery({
        queryKey: apiKeys.questionSets,
        enabled: options.enabled ?? true,
        queryFn: async () => (await apiRequest<unknown[]>("/qa-set/query", {
            method: "POST",
            body: {},
        })).map(normalizeQuestionSet),
    });
}

export function useQuestionSetQuery(questionSetId?: string) {
    return useQuery({
        queryKey: apiKeys.questionSet(questionSetId ?? ""),
        enabled: Boolean(questionSetId),
        queryFn: async () => normalizeQuestionSet(await apiRequest<unknown>("/qa-set/detail", {
            query: { id: questionSetId ?? "" },
        })),
    });
}

export function useQuestionSetItemsQuery(questionSetId?: string) {
    return useQuery({
        queryKey: apiKeys.questionSetItems(questionSetId ?? ""),
        enabled: Boolean(questionSetId),
        queryFn: async () => (await apiRequest<unknown[]>("/qa-item/query", {
            method: "POST",
            body: { qaSetId: questionSetId },
        })).map(normalizeQuestionItem),
    });
}

export function useDeleteQuestionSetMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (questionSetId: string) => {
            await apiRequest<void>("/qa-set/delete", {
                method: "POST",
                body: { id: questionSetId },
            });
            return questionSetId;
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
        },
    });
}

export function useUpdateQuestionSetMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: UpdateQuestionSetInput) => normalizeQuestionSet(await apiRequest<unknown>("/qa-set/update", {
            method: "POST",
            body: toQuestionSetPayload(input),
        })),
        onSuccess: async (_result, variables) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSet(variables.questionSetId) });
        },
    });
}

export function useCreateQuestionItemMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: CreateQuestionItemInput) => normalizeQuestionItem(await apiRequest<unknown>("/qa-item/create", {
            method: "POST",
            body: toQuestionItemPayload(input),
        })),
        onSuccess: async (_result, variables) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSet(variables.qaSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSetItems(variables.qaSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
        },
    });
}

export function useUpdateQuestionItemMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: UpdateQuestionItemInput) => normalizeQuestionItem(await apiRequest<unknown>("/qa-item/update", {
            method: "POST",
            body: toQuestionItemPayload(input),
        })),
        onSuccess: async (_result, variables) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSet(variables.qaSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSetItems(variables.qaSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
        },
    });
}

export function useDeleteQuestionItemMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: DeleteQuestionItemInput) => {
            await apiRequest<void>("/qa-item/delete", {
                method: "POST",
                body: { id: input.questionItemId },
            });
            return input;
        },
        onSuccess: async (_result, variables) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSet(variables.qaSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSetItems(variables.qaSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
        },
    });
}

export function useUploadAvatarMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (file: File) => {
            const formData = new FormData();
            formData.append("file", file);
            return normalizeAuthUser(await apiRequest<unknown>("/user-account/avatar", {
                method: "POST",
                body: formData,
            }));
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.currentUser });
        },
    });
}
