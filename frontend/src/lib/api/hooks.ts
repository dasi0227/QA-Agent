import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest, getApiBaseUrl, ApiError } from "./client";
import { getAccessToken } from "../auth";
import type { ErrorHandlingMeta } from "@/lib/error/types";
import type {
    AuthSession,
    AuthUser,
    ChangePasswordInput,
    CreateEmptyQuestionSetInput,
    CreateSmartQuestionItemBatchInput,
    CreateSmartQuestionItemInput,
    DeleteQuestionItemInput,
    DocumentChunkRecord,
    DocumentRecord,
    ExportQuestionSetFile,
    ImportQuestionSetInput,
    LoginInput,
    Profile,
    PracticeFlowItem,
    PracticeFlowSession,
    PracticeSessionDetail,
    AbandonPracticeInput,
    QuestionItem,
    QuestionItemDraft,
    RetryCompleteQuestionItemInput,
    QuestionSet,
    RegisterInput,
    SaveAnswerInput,
    SendVerifyCodeInput,
    StartPracticeInput,
    UpdateQuestionItemInput,
    UpdateQuestionSetInput,
    UpdateDocumentInput,
    CreateQuestionSetInput,
    SseEvent,
    SubmitItemInput,
    SubmitSessionInput,
    TaskListItem,
    TaskMessage,
    TaskStatus,
    TempChatInput,
    TempChatResponse,
    UserMemory,
    UserMemoryDetail,
    UserMemoryEvidence,
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
    if (typeof value === "number") {
        return value !== 0;
    }
    if (typeof value === "string") {
        const normalized = value.trim().toLowerCase();
        if (normalized === "true" || normalized === "1") {
            return true;
        }
        if (normalized === "false" || normalized === "0") {
            return false;
        }
    }
    return fallback;
};

export const apiKeys = {
    currentUser: ["auth", "me"] as const,
    profile: ["profile"] as const,
    documents: ["documents"] as const,
    finishedDocuments: ["documents", "finished"] as const,
    document: (id: string) => ["documents", id] as const,
    documentChunks: (idsKey: string) => ["document-chunks", idsKey] as const,
    questionSets: ["question-sets"] as const,
    questionSet: (id: string) => ["question-sets", id] as const,
    questionSetItems: (id: string) => ["question-sets", id, "items"] as const,
    questionItem: (id: string) => ["question-items", id] as const,
    taskStatus: (taskId: string) => ["task-status", taskId] as const,
    taskMessages: (taskId: string) => ["task-messages", taskId] as const,
    taskList: ["task-list"] as const,
    memories: ["memories"] as const,
    memory: (memoryId: string) => ["memories", memoryId] as const,
    existingPractice: (qaSetId: string) => ["practice", "session", "exist", qaSetId] as const,
    practiceDetail: (sessionId: string) => ["practice", "session", "detail", sessionId] as const,
    practiceHistory: (qaSetId: string) => ["practice", "session", "history", qaSetId] as const,
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
        allowReferMemory: toBooleanValue(pick(raw, "allowReferMemory", "allow_refer_memory")),
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

export function normalizeUserMemory(raw: unknown): UserMemory {
    return {
        id: toStringValue(pick(raw, "id")),
        memoryType: toStringValue(pick(raw, "memoryType", "memory_type")),
        targetType: toStringValue(pick(raw, "targetType", "target_type")),
        targetKey: toStringValue(pick(raw, "targetKey", "target_key")),
        summary: toStringValue(pick(raw, "summary")),
        content: toStringValue(pick(raw, "content")),
        supportCount: toNumberValue(pick(raw, "supportCount", "support_count")),
        status: toStringValue(pick(raw, "status"), "ACTIVE"),
        firstSeenAt: toStringValue(pick(raw, "firstSeenAt", "first_seen_at")),
        lastSeenAt: toStringValue(pick(raw, "lastSeenAt", "last_seen_at")),
        hiddenAt: toStringValue(pick(raw, "hiddenAt", "hidden_at")),
        latestSessionId: toStringValue(pick(raw, "latestSessionId", "latest_session_id")),
        latestQaSetId: toStringValue(pick(raw, "latestQaSetId", "latest_qa_set_id")),
        createdAt: toStringValue(pick(raw, "createdAt", "created_at")),
        updatedAt: toStringValue(pick(raw, "updatedAt", "updated_at")),
    };
}

export function normalizeUserMemoryEvidence(raw: unknown): UserMemoryEvidence {
    return {
        id: toStringValue(pick(raw, "id")),
        memoryId: toStringValue(pick(raw, "memoryId", "memory_id")),
        sessionId: toStringValue(pick(raw, "sessionId", "session_id")),
        sessionItemId: toStringValue(pick(raw, "sessionItemId", "session_item_id")),
        qaSetId: toStringValue(pick(raw, "qaSetId", "qa_set_id")),
        qaItemId: toStringValue(pick(raw, "qaItemId", "qa_item_id")),
        moduleTag: toStringValue(pick(raw, "moduleTag", "module_tag")),
        questionSnapshot: toStringValue(pick(raw, "questionSnapshot", "question_snapshot")),
        result: toStringValue(pick(raw, "result")),
        score: toNumberValue(pick(raw, "score")),
        sourceChunkIdsJson: toStringValue(pick(raw, "sourceChunkIdsJson", "source_chunk_ids_json")),
        evidenceSummary: toStringValue(pick(raw, "evidenceSummary", "evidence_summary")),
        createdAt: toStringValue(pick(raw, "createdAt", "created_at")),
    };
}

export function normalizeUserMemoryDetail(raw: unknown): UserMemoryDetail {
    const evidenceList = pick(raw, "evidenceList", "evidence_list");
    return {
        memory: normalizeUserMemory(pick(raw, "memory")),
        evidenceList: Array.isArray(evidenceList) ? evidenceList.map(normalizeUserMemoryEvidence) : [],
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
        indexStatus: toStringValue(pick(raw, "indexStatus", "index_status"), "UNSOLVED"),
        referenceCount: toNumberValue(pick(raw, "referenceCount", "reference_count")),
        deleted: toBooleanValue(pick(raw, "deleted")),
        createdAt: toStringValue(pick(raw, "createdAt", "created_at")),
        updatedAt: toStringValue(pick(raw, "updatedAt", "updated_at")),
    };
}

export function normalizeDocumentChunk(raw: unknown): DocumentChunkRecord {
    return {
        id: toStringValue(pick(raw, "id")),
        documentId: toStringValue(pick(raw, "documentId", "document_id")),
        fileName: toStringValue(pick(raw, "fileName", "file_name")),
        chunkIndex: toNumberValue(pick(raw, "chunkIndex", "chunk_index")),
        headingPath: toStringValue(pick(raw, "headingPath", "heading_path")),
        content: toStringValue(pick(raw, "content")),
        summary: toStringValue(pick(raw, "summary")),
        moduleTagsJson: toStringValue(pick(raw, "moduleTagsJson", "module_tags_json")),
    };
}

export function normalizeQuestionSet(raw: unknown): QuestionSet {
    return {
        id: toStringValue(pick(raw, "id")),
        taskId: toStringValue(pick(raw, "taskId", "task_id")),
        title: toStringValue(pick(raw, "title")),
        description: toStringValue(pick(raw, "description")),
        moduleTagsJson: toStringValue(pick(raw, "moduleTagsJson", "module_tags_json")),
        documentCount: toNumberValue(pick(raw, "documentCount", "document_count")),
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
        keywords: toStringValue(pick(raw, "keywords")),
        hint: toStringValue(pick(raw, "hint")),
        sourceReliable: toBooleanValue(pick(raw, "sourceReliable", "source_reliable"), true),
        sourceChunkIdsJson: toStringValue(pick(raw, "sourceChunkIdsJson", "source_chunk_ids_json")),
        completeStatus: toStringValue(pick(raw, "completeStatus", "complete_status"), "SOLVED"),
        sortOrder: toNumberValue(pick(raw, "sortOrder", "sort_order")),
    };
}

function normalizeList(value: unknown): string[] {
    if (!Array.isArray(value)) {
        return [];
    }
    return value.map((item) => toStringValue(item)).filter(Boolean);
}

function normalizeJudgeDetail(raw: unknown) {
    if (!isObject(raw)) {
        return null;
    }
    return {
        missingPoints: normalizeList(pick(raw, "missingPoints", "missing_points")),
        wrongPoints: normalizeList(pick(raw, "wrongPoints", "wrong_points")),
        improvementAdvice: toStringValue(pick(raw, "improvementAdvice", "improvement_advice")),
        betterAnswer: toStringValue(pick(raw, "betterAnswer", "better_answer")),
    };
}

function normalizeHintDetail(raw: unknown) {
    if (!isObject(raw)) {
        return null;
    }
    return {
        memoryTip: toStringValue(pick(raw, "memoryTip", "memory_tip")),
        encouragement: toStringValue(pick(raw, "encouragement")),
    };
}

function normalizeAssessPoint(raw: unknown) {
    if (!isObject(raw)) {
        return {};
    }
    return {
        title: toStringValue(pick(raw, "title")),
        analysis: toStringValue(pick(raw, "analysis", "detail")),
        moduleTag: toStringValue(pick(raw, "moduleTag", "module_tag")),
    };
}

function normalizeAssessDetail(raw: unknown) {
    if (!isObject(raw)) {
        return null;
    }
    const strengths = pick(raw, "strengths");
    const weaknesses = pick(raw, "weaknesses", "weak_points", "weakPoints");
    const reviewGuidance = toStringValue(pick(raw, "reviewGuidance", "review_guidance"));
    return {
        overallComment: toStringValue(pick(raw, "overallComment", "overall_comment")),
        reviewGuidance,
        strengths: Array.isArray(strengths) ? strengths.map(normalizeAssessPoint) : [],
        weaknesses: Array.isArray(weaknesses) ? weaknesses.map(normalizeAssessPoint) : [],
        reviewSuggestions: normalizeList(pick(raw, "reviewSuggestions", "review_suggestions")).concat(reviewGuidance ? [reviewGuidance] : []),
    };
}

export function normalizePracticeFlowSession(raw: unknown): PracticeFlowSession {
    return {
        id: toStringValue(pick(raw, "id")),
        qaSetId: toStringValue(pick(raw, "qaSetId", "qa_set_id")),
        qaSetTitle: toStringValue(pick(raw, "qaSetTitle", "qa_set_title")),
        mode: toStringValue(pick(raw, "mode"), "SEQUENTIAL"),
        feedbackMode: toStringValue(pick(raw, "feedbackMode", "feedback_mode"), "ITEM_BY_ITEM"),
        status: toStringValue(pick(raw, "status"), "IN_PROGRESS"),
        selectedModule: toStringValue(pick(raw, "selectedModule", "selectedModuleTag", "selected_module")),
        currentIndex: toNumberValue(pick(raw, "currentIndex", "current_index")),
        durationSeconds: toNumberValue(pick(raw, "durationSeconds", "duration_seconds")),
        totalQuestions: toNumberValue(pick(raw, "totalQuestions", "total_questions")),
        answeredCount: toNumberValue(pick(raw, "answeredCount", "answered_count")),
        score: pick(raw, "score") == null ? null : toNumberValue(pick(raw, "score")),
        accuracy: pick(raw, "accuracy") == null ? null : toNumberValue(pick(raw, "accuracy")),
        perfectCount: toNumberValue(pick(raw, "perfectCount", "perfect_count")),
        correctCount: toNumberValue(pick(raw, "correctCount", "correct_count")),
        deficientCount: toNumberValue(pick(raw, "deficientCount", "deficient_count")),
        wrongCount: toNumberValue(pick(raw, "wrongCount", "wrong_count")),
        unknownCount: toNumberValue(pick(raw, "unknownCount", "unknown_count")),
        summary: toStringValue(pick(raw, "summary")),
        assessDetail: normalizeAssessDetail(pick(raw, "assessDetail", "assess_detail")),
        startedAt: toStringValue(pick(raw, "startedAt", "started_at")),
        lastActiveAt: toStringValue(pick(raw, "lastActiveAt", "last_active_at")),
        finishedAt: toStringValue(pick(raw, "finishedAt", "finished_at")),
    };
}

export function normalizePracticeFlowItem(raw: unknown): PracticeFlowItem {
    return {
        sessionItemId: toStringValue(pick(raw, "sessionItemId", "session_item_id")),
        qaItemId: toStringValue(pick(raw, "qaItemId", "qa_item_id")),
        sortOrder: toNumberValue(pick(raw, "sortOrder", "sort_order")),
        question: toStringValue(pick(raw, "question")),
        knowledgeNote: toStringValue(pick(raw, "knowledgeNote", "knowledge_note")),
        standardAnswer: toStringValue(pick(raw, "standardAnswer", "standard_answer")),
        moduleTag: toStringValue(pick(raw, "moduleTag", "module_tag")),
        difficulty: toStringValue(pick(raw, "difficulty")),
        keywords: toStringValue(pick(raw, "keywords")),
        hint: toStringValue(pick(raw, "hint")),
        sourceChunkIdsJson: toStringValue(pick(raw, "sourceChunkIdsJson", "source_chunk_ids_json")),
        userAnswer: toStringValue(pick(raw, "userAnswer", "user_answer")),
        status: toStringValue(pick(raw, "status"), "UNANSWERED"),
        unknown: toBooleanValue(pick(raw, "unknown")),
        result: toStringValue(pick(raw, "result")),
        score: pick(raw, "score") == null ? null : toNumberValue(pick(raw, "score")),
        feedbackSummary: toStringValue(pick(raw, "feedbackSummary", "feedback_summary")),
        judgeDetail: normalizeJudgeDetail(pick(raw, "judgeDetail", "judge_detail")),
        hintDetail: normalizeHintDetail(pick(raw, "hintDetail", "hint_detail")),
        answeredAt: toStringValue(pick(raw, "answeredAt", "answered_at")),
        submittedAt: toStringValue(pick(raw, "submittedAt", "submitted_at")),
    };
}

export function normalizePracticeSessionDetail(raw: unknown): PracticeSessionDetail {
    const items = pick(raw, "items");
    return {
        session: normalizePracticeFlowSession(pick(raw, "session")),
        items: Array.isArray(items) ? items.map(normalizePracticeFlowItem) : [],
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

export function parseDelimitedValues(value?: string) {
    if (!value?.trim()) {
        return [];
    }
    try {
        const parsed = JSON.parse(value);
        return Array.isArray(parsed) ? parsed.map((item) => toStringValue(item).trim()).filter(Boolean) : [];
    } catch {
        return value.split(",").map((item) => item.trim()).filter(Boolean);
    }
}

function toProfilePayload(profile: Profile) {
    return {
        targetRole: profile.targetRole,
        targetDomain: profile.targetDomain,
        targetCompany: profile.targetCompany,
        allowReferMemory: profile.allowReferMemory,
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
        moduleTagsJson: input.moduleTagsJson,
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
        keywords: parseDelimitedValues(input.keywords).join(","),
        hint: input.hint,
        sourceReliable: input.sourceReliable,
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

export function useChangePasswordMutation() {
    return useMutation({
        mutationFn: async (input: ChangePasswordInput) => apiRequest<void>("/identity/account/password", {
            method: "POST",
            body: input,
        }),
    });
}

export function useTempChatMutation() {
    return useMutation({
        mutationFn: async (input: TempChatInput) => apiRequest<TempChatResponse>("/chat/temp", {
            method: "POST",
            body: input,
        }),
        meta: {
            errorMode: "silent",
        } satisfies ErrorHandlingMeta,
    });
}

export function useMemoryListQuery() {
    return useQuery({
        queryKey: apiKeys.memories,
        queryFn: async () => (await apiRequest<unknown[]>("/memory/list")).map(normalizeUserMemory),
    });
}

export function useMemoryDetailQuery(memoryId: string, options: QueryControlOptions = {}) {
    return useQuery({
        queryKey: apiKeys.memory(memoryId),
        enabled: (options.enabled ?? true) && Boolean(memoryId),
        queryFn: async () => normalizeUserMemoryDetail(await apiRequest<unknown>("/memory/detail", {
            query: { memoryId },
        })),
    });
}

export function useHideMemoryMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (memoryId: string) => apiRequest<void>("/memory/hide", {
            method: "POST",
            body: { memoryId },
        }),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.memories });
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
        meta: {
            errorMode: "silent",
        } satisfies ErrorHandlingMeta,
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
        meta: {
            errorMode: "silent",
        } satisfies ErrorHandlingMeta,
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

export function useFinishedDocumentsQuery(options: QueryControlOptions = {}) {
    return useQuery({
        queryKey: apiKeys.finishedDocuments,
        enabled: options.enabled ?? true,
        queryFn: async () => (await apiRequest<unknown[]>("/document/source/finished")).map(normalizeDocument),
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

export function useDocumentChunksQuery(chunkIds: string[]) {
    const normalizedChunkIds = Array.from(new Set(chunkIds.map((item) => item.trim()).filter(Boolean)));
    const queryKey = normalizedChunkIds.join(",");
    return useQuery({
        queryKey: apiKeys.documentChunks(queryKey),
        enabled: normalizedChunkIds.length > 0,
        queryFn: async () => (await apiRequest<unknown[]>("/document/chunk/query", {
            method: "POST",
            body: normalizedChunkIds,
        })).map(normalizeDocumentChunk),
    });
}

export function useUpdateDocumentMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (document: UpdateDocumentInput) => normalizeDocument(await apiRequest<unknown>("/document/source/update", {
            method: "POST",
            body: {
                id: document.id,
                fileName: document.fileName,
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

export function useQuestionItemQuery(questionItemId?: string) {
    return useQuery({
        queryKey: apiKeys.questionItem(questionItemId ?? ""),
        enabled: Boolean(questionItemId),
        queryFn: async () => normalizeQuestionItem(await apiRequest<unknown>("/qa/item/detail", {
            query: { id: questionItemId ?? "" },
        })),
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

export function useCreateEmptyQuestionSetMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: CreateEmptyQuestionSetInput) => normalizeQuestionSet(await apiRequest<unknown>("/qa/set/empty", {
            method: "POST",
            body: input,
        })),
        onSuccess: async (result) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSet(result.id) });
        },
    });
}

function getFileNameFromDisposition(disposition: string | null, fallback: string) {
    if (!disposition) {
        return fallback;
    }
    const encodedMatch = disposition.match(/filename\*=UTF-8''([^;]+)/i);
    if (encodedMatch?.[1]) {
        return decodeURIComponent(encodedMatch[1]);
    }
    const plainMatch = disposition.match(/filename="?([^";]+)"?/i);
    return plainMatch?.[1] ? plainMatch[1] : fallback;
}

export function useImportQuestionSetMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: ImportQuestionSetInput) => {
            const formData = new FormData();
            formData.append("file", input.file);
            return normalizeQuestionSet(await apiRequest<unknown>("/qa/set/import", {
                method: "POST",
                body: formData,
            }));
        },
        onSuccess: async (result) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSet(result.id) });
        },
    });
}

export function useExportQuestionSetMutation() {
    return useMutation({
        mutationFn: async (questionSetId: string): Promise<ExportQuestionSetFile> => {
            const token = getAccessToken();
            const url = new URL(`${getApiBaseUrl()}/qa/set/export`);
            url.searchParams.set("id", questionSetId);
            const response = await fetch(url, {
                headers: token ? { Authorization: `Bearer ${token}` } : {},
            });
            if (!response.ok) {
                const text = await response.text();
                let message = `请求失败（${response.status}）`;
                try {
                    const parsed = JSON.parse(text) as { msg?: string };
                    message = parsed.msg || message;
                } catch {
                    if (text) {
                        message = text;
                    }
                }
                throw new ApiError(message, { status: response.status });
            }
            return {
                fileName: getFileNameFromDisposition(response.headers.get("Content-Disposition"), "qa-set.dasi"),
                blob: await response.blob(),
            };
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
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionItem(variables.questionItemId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
        },
    });
}

export function useCreateSmartQuestionItemMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: CreateSmartQuestionItemInput) => normalizeQuestionItem(await apiRequest<unknown>("/qa/item/create/single", {
            method: "POST",
            body: input,
        })),
        onSuccess: async (result, variables) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSet(variables.qaSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSetItems(variables.qaSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionItem(result.id) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
        },
    });
}

export function useCreateSmartQuestionItemsBatchMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: CreateSmartQuestionItemBatchInput) => (await apiRequest<unknown[]>("/qa/item/create/batch", {
            method: "POST",
            body: input,
        })).map(normalizeQuestionItem),
        onSuccess: async (result, variables) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSet(variables.qaSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSetItems(variables.qaSetId) });
            await Promise.all(result.map((item) => queryClient.invalidateQueries({ queryKey: apiKeys.questionItem(item.id) })));
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
        },
    });
}

export function useRetryCompleteQuestionItemMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: RetryCompleteQuestionItemInput) => normalizeQuestionItem(await apiRequest<unknown>("/qa/item/complete", {
            method: "POST",
            body: input,
        })),
        onSuccess: async (result) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSetItems(result.qaSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionItem(result.id) });
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
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionItem(variables.questionItemId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
        },
    });
}

export function useExistingPracticeSessionQuery(qaSetId?: string, options: QueryControlOptions = {}) {
    return useQuery({
        queryKey: apiKeys.existingPractice(qaSetId ?? ""),
        enabled: Boolean(qaSetId) && (options.enabled ?? true),
        queryFn: async () => {
            const raw = await apiRequest<unknown | null>("/practice/session/exist", {
                query: { qaSetId: qaSetId ?? "" },
            });
            return raw ? normalizePracticeFlowSession(raw) : null;
        },
    });
}

export function usePracticeDetailQuery(sessionId?: string, options: QueryControlOptions = {}) {
    return useQuery({
        queryKey: apiKeys.practiceDetail(sessionId ?? ""),
        enabled: Boolean(sessionId) && (options.enabled ?? true),
        queryFn: async () => normalizePracticeSessionDetail(await apiRequest<unknown>("/practice/session/detail", {
            query: { sessionId: sessionId ?? "" },
        })),
    });
}

export function usePracticeHistoryQuery(qaSetId?: string, options: QueryControlOptions = {}) {
    return useQuery({
        queryKey: apiKeys.practiceHistory(qaSetId ?? ""),
        enabled: Boolean(qaSetId) && (options.enabled ?? true),
        queryFn: async () => {
            const raw = await apiRequest<unknown[]>("/practice/session/history", {
                query: { qaSetId: qaSetId ?? "" },
            });
            return Array.isArray(raw) ? raw.map(normalizePracticeFlowSession) : [];
        },
    });
}

export function useStartPracticeMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: StartPracticeInput) => normalizePracticeSessionDetail(await apiRequest<unknown>("/practice/session/init", {
            method: "POST",
            body: input,
        })),
        onSuccess: async (detail) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.existingPractice(detail.session.qaSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.practiceDetail(detail.session.id) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
        },
    });
}

export function useSavePracticeAnswerMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: SaveAnswerInput) => normalizePracticeFlowItem(await apiRequest<unknown>("/practice/item/save", {
            method: "POST",
            body: input,
        })),
        onSuccess: async (_item, variables) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.practiceDetail(variables.sessionId) });
        },
    });
}

export function useMarkPracticeUnknownMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: SaveAnswerInput) => normalizePracticeFlowItem(await apiRequest<unknown>("/practice/item/unknown", {
            method: "POST",
            body: input,
        })),
        onSuccess: async (_item, variables) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.practiceDetail(variables.sessionId) });
        },
    });
}

export function useSubmitPracticeItemMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: SubmitItemInput) => normalizePracticeFlowItem(await apiRequest<unknown>("/practice/item/answer", {
            method: "POST",
            body: input,
        })),
        onSuccess: async (_item, variables) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.practiceDetail(variables.sessionId) });
        },
    });
}

export function useSubmitPracticeSessionMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: SubmitSessionInput) => normalizePracticeSessionDetail(await apiRequest<unknown>("/practice/session/submit", {
            method: "POST",
            body: input,
        })),
        onSuccess: async (detail, variables) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.practiceDetail(variables.sessionId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
            await queryClient.invalidateQueries({ queryKey: apiKeys.existingPractice(detail.session.qaSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.practiceHistory(detail.session.qaSetId) });
        },
    });
}

export function useRestartPracticeMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: StartPracticeInput & { sessionId?: string }) => normalizePracticeSessionDetail(await apiRequest<unknown>("/practice/session/restart", {
            method: "POST",
            body: input,
        })),
        onSuccess: async (detail) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.existingPractice(detail.session.qaSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.practiceDetail(detail.session.id) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
        },
    });
}

export function useAbandonPracticeMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: AbandonPracticeInput) => apiRequest<void>("/practice/session/abandon", {
            method: "POST",
            body: input,
        }),
        onSuccess: async (_detail, variables) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.practiceDetail(variables.sessionId) });
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

export function useCreateTaskMutation() {
    return useMutation({
        mutationFn: async (input: CreateQuestionSetInput) =>
            apiRequest<{ taskId: string }>("/qa/set/task", {
                method: "POST",
                body: {
                    title: input.title,
                    userPrompt: input.userPrompt,
                    documentIds: input.documentIds,
                    requestedQuestionCount: input.requestedQuestionCount,
                    jobDescription: input.jobDescription || "暂无",
                },
            }),
    });
}

export function useAbortTaskMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        meta: {
            errorMode: "silent",
        } satisfies ErrorHandlingMeta,
        mutationFn: async (taskId: string) => {
            return apiRequest<unknown>("/qa/set/abort", {
                method: "POST",
                body: { taskId },
            });
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: apiKeys.taskList });
        },
    });
}

export function useCreateQuestionSetStream() {
    const queryClient = useQueryClient();
    return useMutation({
        meta: {
            errorMode: "silent",
        } satisfies ErrorHandlingMeta,
        mutationFn: async (input: CreateQuestionSetInput & { onEvent: (event: SseEvent) => void }) => {
            const token = getAccessToken();

            const response = await fetch(`${getApiBaseUrl()}/qa/set/create`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    ...(token ? { Authorization: `Bearer ${token}` } : {}),
                },
                body: JSON.stringify({
                    taskId: input.taskId,
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
