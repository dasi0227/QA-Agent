import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest, getApiBaseUrl, ApiError } from "./client";
import { getAccessToken } from "../auth";
import type {
    AuthSession,
    AuthUser,
    DeleteQuestionItemInput,
    DocumentRecord,
    LoginInput,
    Profile,
    QuestionItem,
    QuestionItemDraft,
    QuestionSet,
    RegisterInput,
    SendVerifyCodeInput,
    UpdateQuestionItemInput,
    UpdateQuestionSetInput,
    CreateQuestionSetInput,
    SseEvent,
    TaskListItem,
    TaskMessage,
    TaskStatus,
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
    taskStatus: (taskId: string) => ["task-status", taskId] as const,
    taskMessages: (taskId: string) => ["task-messages", taskId] as const,
    taskList: ["task-list"] as const,
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
        allowFallback: toBooleanValue(pick(raw, "allowFallback", "allow_fallback")),
        answerStyle: toStringValue(pick(raw, "answerStyle", "answer_style")),
        feedbackStyle: toStringValue(pick(raw, "feedbackStyle", "feedback_style")),
        grade: toStringValue(pick(raw, "grade")),
        major: toStringValue(pick(raw, "major")),
        stage: toStringValue(pick(raw, "stage")),
        llmBaseUrl: toStringValue(pick(raw, "llmBaseUrl", "llm_base_url")),
        llmApiKey: toStringValue(pick(raw, "llmApiKey", "llm_api_key")),
        llmModelName: toStringValue(pick(raw, "llmModelName", "llm_model_name")),
    };
}

export function normalizeTaskStatus(raw: unknown): TaskStatus {
    return {
        taskId: toStringValue(pick(raw, "taskId", "task_id")),
        userId: toStringValue(pick(raw, "userId", "user_id")),
        title: toStringValue(pick(raw, "title")),
        userPrompt: toStringValue(pick(raw, "userPrompt", "user_prompt")),
        documentIdsJson: toStringValue(pick(raw, "documentIdsJson", "document_ids_json")),
        documentNamesJson: toStringValue(pick(raw, "documentNamesJson", "document_names_json")),
        qaSetId: toStringValue(pick(raw, "qaSetId", "qa_set_id")),
        status: toStringValue(pick(raw, "status"), "PENDING"),
        stage: toStringValue(pick(raw, "stage"), "INIT"),
        errorCode: toStringValue(pick(raw, "errorCode", "error_code")),
        errorMessage: toStringValue(pick(raw, "errorMessage", "error_message")),
        requestedQuestionCount: toNumberValue(pick(raw, "requestedQuestionCount", "requested_question_count")),
        createdAt: toStringValue(pick(raw, "createdAt", "created_at")),
        startedAt: toStringValue(pick(raw, "startedAt", "started_at")),
        completedAt: toStringValue(pick(raw, "completedAt", "completed_at")),
    };
}

export function normalizeTaskMessage(raw: unknown): TaskMessage {
    return {
        id: toStringValue(pick(raw, "id")),
        taskId: toStringValue(pick(raw, "taskId", "task_id")),
        stage: toStringValue(pick(raw, "stage")),
        message: toStringValue(pick(raw, "message")),
        content: toStringValue(pick(raw, "content")),
        createdAt: toStringValue(pick(raw, "createdAt", "created_at")),
    };
}

export function normalizeTaskListItem(raw: unknown): TaskListItem {
    return {
        taskId: toStringValue(pick(raw, "taskId", "task_id")),
        title: toStringValue(pick(raw, "title")),
        status: toStringValue(pick(raw, "status")),
        stage: toStringValue(pick(raw, "stage")),
        qaSetId: toStringValue(pick(raw, "qaSetId", "qa_set_id")),
        createdAt: toStringValue(pick(raw, "createdAt", "created_at")),
    };
}

export function normalizeDocument(raw: unknown): DocumentRecord {
    return {
        id: toStringValue(pick(raw, "id")),
        fileName: toStringValue(pick(raw, "fileName", "file_name")),
        fileType: toStringValue(pick(raw, "fileType", "file_type")),
        filePath: toStringValue(pick(raw, "filePath", "file_path")),
        rawContent: toStringValue(pick(raw, "rawContent", "raw_content")),
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
        allowFallback: profile.allowFallback,
        answerStyle: profile.answerStyle,
        feedbackStyle: profile.feedbackStyle,
        grade: profile.grade,
        major: profile.major,
        stage: profile.stage,
        llmBaseUrl: profile.llmBaseUrl,
        llmApiKey: profile.llmApiKey,
        llmModelName: profile.llmModelName,
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
                return normalizeProfile(await apiRequest<unknown>("/identity/profile/me"));
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
                return normalizeProfile(await apiRequest<unknown>("/identity/profile/update", {
                    method: "POST",
                    body: toProfilePayload(profile),
                }));
            } catch (error) {
                if (error instanceof ApiError && error.code === "40400") {
                    return normalizeProfile(await apiRequest<unknown>("/identity/profile/create", {
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

export function useSendVerifyCodeMutation() {
    return useMutation({
        mutationFn: async (input: SendVerifyCodeInput) => {
            await apiRequest<void>("/auth/send-verify-code", {
                method: "POST",
                body: { email: input.email },
                auth: false,
            });
        },
    });
}

export function useRegisterMutation() {
    return useMutation({
        mutationFn: async (input: RegisterInput) => {
            const session = await apiRequest<AuthSession>("/auth/register", {
                method: "POST",
                body: {
                    username: input.name,
                    email: input.email,
                    password: input.password,
                    verifyCode: input.verifyCode,
                },
                auth: false,
            });
            return {
                user: normalizeAuthUser(session),
            };
        },
    });
}

export function useDocumentsQuery(options: QueryControlOptions = {}) {
    return useQuery({
        queryKey: apiKeys.documents,
        enabled: options.enabled ?? true,
        queryFn: async () => (await apiRequest<unknown[]>("/document/source/query", {
            method: "POST",
            body: {},
        })).map(normalizeDocument),
    });
}

export function useDocumentQuery(documentId?: string) {
    return useQuery({
        queryKey: apiKeys.document(documentId ?? ""),
        enabled: Boolean(documentId),
        queryFn: async () => normalizeDocument(await apiRequest<unknown>("/document/source/detail", {
            query: { id: documentId ?? "" },
        })),
    });
}

export function useUpdateDocumentMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (document: DocumentRecord) => normalizeDocument(await apiRequest<unknown>("/document/source/update", {
            method: "POST",
            body: {
                id: document.id,
                fileName: document.fileName,
                fileType: document.fileType,
                filePath: document.filePath,
                rawContent: document.rawContent,
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
            await apiRequest<void>("/document/source/delete", {
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
        queryFn: async () => (await apiRequest<unknown[]>("/qa/set/query", {
            method: "POST",
            body: {},
        })).map(normalizeQuestionSet),
    });
}

export function useQuestionSetQuery(questionSetId?: string) {
    return useQuery({
        queryKey: apiKeys.questionSet(questionSetId ?? ""),
        enabled: Boolean(questionSetId),
        queryFn: async () => normalizeQuestionSet(await apiRequest<unknown>("/qa/set/detail", {
            query: { id: questionSetId ?? "" },
        })),
    });
}

export function useQuestionSetItemsQuery(questionSetId?: string) {
    return useQuery({
        queryKey: apiKeys.questionSetItems(questionSetId ?? ""),
        enabled: Boolean(questionSetId),
        queryFn: async () => (await apiRequest<unknown[]>("/qa/item/query", {
            method: "POST",
            body: { qaSetId: questionSetId },
        })).map(normalizeQuestionItem),
    });
}

export function useDeleteQuestionSetMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (questionSetId: string) => {
            await apiRequest<void>("/qa/set/delete", {
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
        mutationFn: async (input: UpdateQuestionSetInput) => normalizeQuestionSet(await apiRequest<unknown>("/qa/set/update", {
            method: "POST",
            body: toQuestionSetPayload(input),
        })),
        onSuccess: async (_result, variables) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSet(variables.questionSetId) });
        },
    });
}

export function useUpdateQuestionItemMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: UpdateQuestionItemInput) => normalizeQuestionItem(await apiRequest<unknown>("/qa/item/update", {
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
            await apiRequest<void>("/qa/item/delete", {
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
            return normalizeAuthUser(await apiRequest<unknown>("/identity/account/avatar", {
                method: "POST",
                body: formData,
            }));
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.currentUser });
        },
    });
}

export function useUploadDocumentMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (file: File) => {
            const rawContent = await file.text();
            const fileType = file.name.split(".").pop()?.toLowerCase() || "txt";
            return normalizeDocument(await apiRequest<unknown>("/document/source/upload", {
                method: "POST",
                body: {
                    fileName: file.name,
                    fileType,
                    rawContent,
                },
            }));
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.documents });
        },
    });
}

export function useCreateQuestionSetStream() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: CreateQuestionSetInput & { onEvent: (event: SseEvent) => void }) => {
            const token = getAccessToken();

            const response = await fetch(`${getApiBaseUrl()}/qa/set/create`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    ...(token ? { Authorization: `Bearer ${token}` } : {}),
                },
                body: JSON.stringify({
                    title: input.title,
                    userPrompt: input.userPrompt,
                    documentIds: input.documentIds,
                    requestedQuestionCount: input.requestedQuestionCount,
                    jobDescription: input.jobDescription || "暂无",
                }),
            });

            if (!response.ok) {
                throw new ApiError(`生成请求失败（${response.status}）`, { status: response.status });
            }

            if (!response.body) {
                throw new ApiError("响应体为空");
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let lineBuffer = "";
            let dataLines: string[] = [];

            const flushEvent = () => {
                if (dataLines.length === 0) return;
                const json = dataLines.join("");
                dataLines = [];
                try {
                    if (json.trim()) {
                        const event: SseEvent = JSON.parse(json);
                        input.onEvent(event);
                    }
                } catch {
                    // skip unparseable event
                }
            };

            try {
                while (true) {
                    const { done, value } = await reader.read();
                    if (done) break;

                    lineBuffer += decoder.decode(value, { stream: true });
                    const lines = lineBuffer.split("\n");
                    lineBuffer = lines.pop() || "";

                    for (const line of lines) {
                        const trimmed = line.trim();
                        if (trimmed.startsWith("data:")) {
                            dataLines.push(trimmed.slice(5));
                        } else if (!trimmed && dataLines.length > 0) {
                            // Empty line after data → event boundary
                            flushEvent();
                        }
                        // ignore other lines (event:, id:, etc.)
                    }
                }
                flushEvent();
            } finally {
                reader.releaseLock();
            }
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
        },
    });
}

export function useTaskStatusQuery(taskId?: string, opts?: { poll?: boolean }) {
    const shouldPoll = opts?.poll ?? true;
    const id = taskId || "";
    return useQuery({
        queryKey: apiKeys.taskStatus(id),
        enabled: Boolean(taskId),
        refetchInterval: (query) => {
            if (!shouldPoll || !taskId) return false;
            const status = query.state.data?.status;
            return status === "PROCESSING" || status === "PENDING" ? 2000 : false;
        },
        queryFn: async () => normalizeTaskStatus(await apiRequest<unknown>("/qa/set/task-status", {
            query: { taskId: id },
        })),
    });
}

export function useTaskMessagesQuery(taskId?: string, opts?: { poll?: boolean }) {
    const shouldPoll = opts?.poll ?? true;
    const id = taskId || "";
    return useQuery({
        queryKey: apiKeys.taskMessages(id),
        enabled: Boolean(taskId),
        refetchInterval: shouldPoll && taskId ? 2000 : false,
        queryFn: async () => (await apiRequest<unknown[]>("/qa/set/task-messages", {
            query: { taskId: id },
        })).map(normalizeTaskMessage),
    });
}

export function parseTaskMessagesToEvents(messages: TaskMessage[]): SseEvent[] {
    return messages
        .map((m) => {
            if (!m.content) return null;
            try {
                return JSON.parse(m.content) as SseEvent;
            } catch {
                return null;
            }
        })
        .filter((e): e is SseEvent => e !== null);
}

export function useTaskListQuery() {
    return useQuery({
        queryKey: apiKeys.taskList,
        queryFn: async () => (await apiRequest<unknown[]>("/qa/set/task-list")).map(normalizeTaskListItem),
    });
}
