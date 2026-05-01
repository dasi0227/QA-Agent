import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "./client";
import type {
    AuthSession,
    AuthUser,
    DocumentRecord,
    GenerateQuestionSetInput,
    CreateQuestionItemInput,
    DeleteQuestionItemInput,
    GenerationTask,
    LoginInput,
    PracticeAnswer,
    PracticeAnswerInput,
    PracticeFeedbackDetail,
    PracticeResult,
    PracticeResultDetail,
    PracticeSession,
    PracticeStartInput,
    Profile,
    QuestionItemDraft,
    QuestionItem,
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

const toStringArray = (value: unknown) => {
    if (!Array.isArray(value)) {
        return [];
    }
    return value.map((item) => toStringValue(item)).filter(Boolean);
};

const toCommaSeparatedArray = (value: unknown) => {
    if (Array.isArray(value)) {
        return toStringArray(value);
    }
    if (typeof value !== "string" || !value.trim()) {
        return [];
    }
    return value.split(",").map((item) => item.trim()).filter(Boolean);
};

const parseJsonStringArray = (value: unknown) => {
    if (Array.isArray(value)) {
        return toStringArray(value);
    }
    if (typeof value !== "string" || !value.trim()) {
        return [];
    }
    try {
        return toStringArray(JSON.parse(value));
    } catch {
        return toCommaSeparatedArray(value);
    }
};

const parseJsonValue = (value: unknown) => {
    if (!value) {
        return null;
    }
    if (typeof value === "string") {
        try {
            return JSON.parse(value);
        } catch {
            return null;
        }
    }
    return value;
};

const parseModuleResults = (value: unknown) => {
    if (!value) {
        return [];
    }
    try {
        const parsed = typeof value === "string" ? JSON.parse(value) : value;
        if (!Array.isArray(parsed)) {
            return [];
        }
        return parsed.map((item) => ({
            label: toStringValue(pick(item, "label")),
            score: toNumberValue(pick(item, "score"), 0),
            detail: toStringValue(pick(item, "detail")),
        }));
    } catch {
        return [];
    }
};

const parseScoringRubric = (value: unknown) => {
    const parsed = parseJsonValue(value);
    if (!isObject(parsed)) {
        return undefined;
    }
    return {
        keyPoints: toStringArray(pick(parsed, "keyPoints", "key_points")),
        answerStructure: toStringValue(pick(parsed, "answerStructure", "answer_structure")),
        evidenceRefs: parseJsonStringArray(pick(parsed, "evidenceRefs", "evidence_refs")),
        promptKey: toStringValue(pick(parsed, "promptKey", "prompt_key"), ""),
        promptVersion: toStringValue(pick(parsed, "promptVersion", "prompt_version"), ""),
        usedFallback: toBooleanValue(pick(parsed, "usedFallback", "used_fallback"), false),
    };
};

const parseFeedbackDetail = (value: unknown): PracticeFeedbackDetail | null => {
    const parsed = parseJsonValue(value);
    if (!isObject(parsed)) {
        return null;
    }
    const schema = isObject(pick(parsed, "schema")) ? pick(parsed, "schema") : parsed;
    if (!isObject(schema)) {
        return null;
    }
    return {
        promptKey: toStringValue(pick(parsed, "promptKey", "prompt_key"), ""),
        promptVersion: toStringValue(pick(parsed, "promptVersion", "prompt_version"), ""),
        usedFallback: toBooleanValue(pick(parsed, "usedFallback", "used_fallback"), false),
        judgement: toStringValue(pick(schema, "judgement"), ""),
        scoreHint: toNumberValue(pick(schema, "scoreHint", "score_hint"), 0),
        reason: toStringValue(pick(schema, "reason"), ""),
        missingPoints: toStringArray(pick(schema, "missingPoints", "missing_points")),
        suggestions: toStringArray(pick(schema, "suggestions")),
        evidenceRefs: toStringArray(pick(schema, "evidenceRefs", "evidence_refs")),
    };
};

const parseScoringDetail = (value: unknown): PracticeResultDetail | null => {
    const parsed = parseJsonValue(value);
    if (!isObject(parsed)) {
        return null;
    }
    const schema = isObject(pick(parsed, "schema")) ? pick(parsed, "schema") : parsed;
    if (!isObject(schema)) {
        return null;
    }
    const moduleScoresValue = pick(schema, "moduleScores", "module_scores");
    const moduleScores = Array.isArray(moduleScoresValue)
        ? moduleScoresValue.map((item) => ({
            moduleTag: toStringValue(pick(item, "moduleTag", "module_tag", "label")),
            score: toNumberValue(pick(item, "score"), 0),
            judgement: toStringValue(pick(item, "judgement"), ""),
            evidenceNote: toStringValue(pick(item, "evidenceNote", "evidence_note", "detail"), ""),
        }))
        : [];
    return {
        promptKey: toStringValue(pick(parsed, "promptKey", "prompt_key"), ""),
        promptVersion: toStringValue(pick(parsed, "promptVersion", "prompt_version"), ""),
        usedFallback: toBooleanValue(pick(parsed, "usedFallback", "used_fallback"), false),
        finalScore: toNumberValue(pick(schema, "finalScore", "final_score"), 0),
        summary: toStringValue(pick(schema, "summary"), ""),
        moduleScores,
        strengths: toStringArray(pick(schema, "strengths")),
        gaps: toStringArray(pick(schema, "gaps")),
        reviewOrder: toStringArray(pick(schema, "reviewOrder", "review_order")),
        evidenceRefs: toStringArray(pick(schema, "evidenceRefs", "evidence_refs")),
    };
};

export const apiKeys = {
    auth: ["auth"] as const,
    currentUser: ["auth", "me"] as const,
    profile: ["profile"] as const,
    documents: ["documents"] as const,
    questionSets: ["question-sets"] as const,
    questionSet: (id: string) => ["question-sets", id] as const,
    questionSetItems: (id: string) => ["question-sets", id, "items"] as const,
    generationTask: (id: string) => ["tasks", id] as const,
    practiceSession: (id: string) => ["practice-sessions", id] as const,
    practiceResult: (id: string) => ["practice-sessions", id, "result"] as const,
} as const;

type QueryControlOptions = {
    enabled?: boolean;
};

export function normalizeAuthUser(raw: unknown): AuthUser {
    return {
        id: toStringValue(pick(raw, "id", "userId", "user_id")),
        username: toStringValue(pick(raw, "username", "userName", "user_name")),
        email: toStringValue(pick(raw, "email", "emailAddress", "email_address")),
        displayName: toStringValue(pick(raw, "displayName", "display_name", "name"), ""),
        status: toStringValue(pick(raw, "status"), "ACTIVE") as AuthUser["status"],
        profileCompleted: toBooleanValue(pick(raw, "profileCompleted", "profile_completed"), false),
    };
}

export function normalizeProfile(raw: unknown): Profile {
    return {
        targetRole: toStringValue(pick(raw, "targetRole", "target_role")),
        targetDirection: toStringValue(pick(raw, "targetDirection", "target_direction")),
        allowGeneralKnowledge: toBooleanValue(pick(raw, "allowGeneralKnowledge", "allow_general_knowledge")),
        answerStyle: toStringValue(pick(raw, "answerStyle", "answer_style")),
        feedbackStyle: toStringValue(pick(raw, "feedbackStyle", "feedback_style")),
        grade: toStringValue(pick(raw, "grade")),
        education: toStringValue(pick(raw, "education")),
        stage: toStringValue(pick(raw, "stage")),
        companyType: toStringValue(pick(raw, "companyType", "company_type")),
        note: toStringValue(pick(raw, "note")),
    };
}

export function normalizeDocument(raw: unknown): DocumentRecord {
    return {
        id: toStringValue(pick(raw, "id", "documentId", "document_id")),
        fileName: toStringValue(pick(raw, "fileName", "file_name", "name")),
        fileType: toStringValue(pick(raw, "fileType", "file_type", "kind"), "text") as DocumentRecord["fileType"],
        size: toNumberValue(pick(raw, "size", "sizeBytes", "fileSize", "file_size"), 0),
        createdAt: toStringValue(pick(raw, "createdAt", "created_at"), ""),
        updatedAt: toStringValue(pick(raw, "updatedAt", "updated_at"), ""),
        rawContent: toStringValue(pick(raw, "rawContent", "raw_content", "content", "body"), ""),
        normalizedText: toStringValue(pick(raw, "normalizedText", "normalized_text", "normalizedContent", "normalized_content"), ""),
        summary: toStringValue(pick(raw, "summary"), ""),
        contentPreview: toStringValue(pick(raw, "contentPreview", "content_preview", "preview", "summary"), ""),
        chunkCount: toNumberValue(pick(raw, "chunkCount", "chunk_count"), 0),
        usedInGeneration: toBooleanValue(pick(raw, "usedInGeneration", "used_in_generation"), false),
    };
}

export function normalizeQuestionSet(raw: unknown): QuestionSet {
    return {
        id: toStringValue(pick(raw, "id", "qaSetId", "qa_set_id")),
        title: toStringValue(pick(raw, "title", "name")),
        note: toStringValue(pick(raw, "note"), ""),
        moduleTags: toCommaSeparatedArray(pick(raw, "moduleTags", "module_tags", "modules")),
        questionCount: toNumberValue(pick(raw, "questionCount", "question_count"), 0),
        practiceCount: toNumberValue(pick(raw, "practiceCount", "practice_count"), 0),
        averageScore: toNumberValue(pick(raw, "averageScore", "average_score"), 0),
        lastPracticedAt: toStringValue(pick(raw, "lastPracticedAt", "last_practiced_at"), ""),
        status: toStringValue(pick(raw, "status"), "READY"),
        documentCount: toNumberValue(pick(raw, "documentCount", "document_count"), 0),
        createdAt: toStringValue(pick(raw, "createdAt", "created_at"), ""),
        updatedAt: toStringValue(pick(raw, "updatedAt", "updated_at"), ""),
    };
}

export function normalizeQuestionItem(raw: unknown): QuestionItem {
    return {
        id: toStringValue(pick(raw, "id", "qaItemId", "qa_item_id")),
        questionSetId: toStringValue(pick(raw, "questionSetId", "qaSetId", "question_set_id", "qa_set_id")),
        question: toStringValue(pick(raw, "question")),
        knowledgeNote: toStringValue(pick(raw, "knowledgeNote", "knowledge_note")),
        interviewAnswer: toStringValue(pick(raw, "interviewAnswer", "interview_answer")),
        moduleTag: toStringValue(pick(raw, "moduleTag", "module_tag")),
        tags: parseJsonStringArray(pick(raw, "tags")),
        sortOrder: toNumberValue(pick(raw, "sortOrder", "sort_order"), 0),
        status: toStringValue(pick(raw, "status"), "READY"),
        difficulty: toStringValue(pick(raw, "difficulty"), ""),
        conflictTip: toStringValue(pick(raw, "conflictTip", "conflict_tip"), ""),
        scoringRubric: parseScoringRubric(pick(raw, "scoringRubric", "scoringRubricJson", "scoring_rubric_json")),
        sourceChunkIds: parseJsonStringArray(pick(raw, "sourceChunkIds", "sourceChunkIdsJson", "source_chunk_ids")),
    };
}

export function normalizeGenerationTask(raw: unknown): GenerationTask {
    const stage = toStringValue(pick(raw, "stage"), "QUEUED");
    return {
        id: toStringValue(pick(raw, "id", "taskId", "task_id")),
        title: toStringValue(pick(raw, "title"), ""),
        note: toStringValue(pick(raw, "note"), ""),
        allowGeneralKnowledge: toBooleanValue(pick(raw, "allowGeneralKnowledge", "allow_general_knowledge"), false),
        requestedQuestionCount: toNumberValue(pick(raw, "requestedQuestionCount", "requested_question_count"), 0),
        type: toStringValue(pick(raw, "type", "taskType", "task_type"), "QA_GENERATION"),
        targetId: toStringValue(pick(raw, "targetId", "target_id"), ""),
        status: toStringValue(pick(raw, "status"), stage),
        stage,
        progress: toNumberValue(pick(raw, "progress"), 0),
        message: toStringValue(pick(raw, "message", "stageMessage"), ""),
        errorMessage: toStringValue(pick(raw, "errorMessage", "error_message"), ""),
        documentIds: parseJsonStringArray(pick(raw, "documentIds", "document_ids")),
        documentNames: toStringArray(pick(raw, "documentNames", "document_names")),
        createdAt: toStringValue(pick(raw, "createdAt", "created_at"), ""),
        updatedAt: toStringValue(pick(raw, "updatedAt", "updated_at"), ""),
        startedAt: toStringValue(pick(raw, "startedAt", "started_at"), ""),
        completedAt: toStringValue(pick(raw, "completedAt", "completed_at"), ""),
        questionSetId: toStringValue(pick(raw, "questionSetId", "qaSetId", "question_set_id"), ""),
    };
}

function normalizePracticeQuestion(raw: unknown) {
    const qaItem = isObject(pick(raw, "qaItem")) ? pick(raw, "qaItem") : raw;
    if (!isObject(qaItem)) {
        return null;
    }
    const scoringRubric = parseScoringRubric(pick(qaItem, "scoringRubric", "scoringRubricJson", "scoring_rubric_json"));
    return {
        id: toStringValue(pick(qaItem, "id")),
        questionSetId: toStringValue(pick(qaItem, "qaSetId", "questionSetId", "qa_set_id")),
        question: toStringValue(pick(qaItem, "question")),
        moduleTag: toStringValue(pick(qaItem, "moduleTag", "module_tag")),
        tags: parseJsonStringArray(pick(qaItem, "tags")),
        hint: toStringValue(pick(qaItem, "knowledgeNote", "knowledge_note")),
        knowledgeNote: toStringValue(pick(qaItem, "knowledgeNote", "knowledge_note"), ""),
        interviewAnswer: toStringValue(pick(qaItem, "interviewAnswer", "interview_answer"), ""),
        answerGuide: toStringValue(pick(qaItem, "interviewAnswer", "interview_answer"), ""),
        difficulty: toStringValue(pick(qaItem, "difficulty"), ""),
        conflictTip: toStringValue(pick(qaItem, "conflictTip", "conflict_tip"), ""),
        scoringRubric,
        sourceChunkIds: parseJsonStringArray(pick(qaItem, "sourceChunkIds", "sourceChunkIdsJson", "source_chunk_ids")),
    };
}

export function normalizePracticeSession(raw: unknown): PracticeSession {
    const sessionRaw = isObject(pick(raw, "session")) ? pick(raw, "session") : raw;
    const answerAttempt = isObject(pick(raw, "attempt")) ? pick(raw, "attempt") : null;
    const latestAnswer = isObject(pick(raw, "latestAnswer", "latest_answer"))
        ? normalizePracticeAnswer(pick(raw, "latestAnswer", "latest_answer"))
        : null;
    const currentQuestionRaw = pick(raw, "currentQuestion", "current_question", "nextQuestion", "next_question");
    const question = normalizePracticeQuestion(currentQuestionRaw);
    return {
        id: toStringValue(pick(sessionRaw, "id", "sessionId", "session_id"), toStringValue(pick(answerAttempt, "sessionId", "session_id"))),
        questionSetId: toStringValue(pick(sessionRaw, "qaSetId", "questionSetId", "question_set_id")),
        questionSetTitle: toStringValue(pick(raw, "questionSetTitle", "question_set_title"), ""),
        mode: toStringValue(pick(sessionRaw, "mode"), "SEQUENTIAL") as PracticeSession["mode"],
        feedbackMode: toStringValue(pick(sessionRaw, "feedbackMode", "feedback_mode"), "ITEM_BY_ITEM") as PracticeSession["feedbackMode"],
        status: toStringValue(pick(sessionRaw, "status"), "CREATED"),
        currentQuestionIndex: toNumberValue(pick(currentQuestionRaw, "questionIndex", "currentQuestionIndex", "current_question_index"), 0),
        totalQuestions: toNumberValue(pick(currentQuestionRaw, "questionTotal", "totalQuestions", "total_questions"), toNumberValue(pick(sessionRaw, "totalQuestions", "total_questions"), 0)),
        currentQuestion: question,
        answeredCount: toNumberValue(pick(raw, "answeredCount", "answered_count"), 0),
        canRevealAnswer: latestAnswer != null || Boolean(answerAttempt) || toBooleanValue(pick(raw, "canRevealAnswer", "can_reveal_answer"), false),
        currentAnswer: latestAnswer?.currentAnswer || toStringValue(pick(raw, "currentAnswer", "current_answer"), toStringValue(pick(answerAttempt, "userAnswer", "user_answer"), "")),
        feedback: latestAnswer?.feedback || toStringValue(pick(raw, "feedback"), toStringValue(pick(answerAttempt, "reason", "modelFeedback"), "")),
        answerGuide: latestAnswer?.standardAnswer || latestAnswer?.answerGuide || toStringValue(pick(raw, "answerGuide", "standardAnswer"), toStringValue(pick(answerAttempt, "improvementSuggestion", "missingPoints"), "")),
        score: toNumberValue(pick(sessionRaw, "score"), toNumberValue(pick(answerAttempt, "score"), 0)),
        summary: toStringValue(pick(sessionRaw, "summary"), ""),
        strengths: parseJsonStringArray(pick(sessionRaw, "strengthsJson", "strengths_json")),
        gaps: parseJsonStringArray(pick(sessionRaw, "gapsJson", "gaps_json")),
        moduleResults: parseModuleResults(pick(sessionRaw, "moduleScoresJson", "module_scores_json")),
        latestAnswer,
    };
}

export function normalizePracticeResult(raw: unknown): PracticeResult {
    const sessionRaw = isObject(pick(raw, "session")) ? pick(raw, "session") : raw;
    const detail = parseScoringDetail(pick(sessionRaw, "scoringDetailJson", "scoring_detail_json"));
    const moduleResults = detail?.moduleScores?.length
        ? detail.moduleScores.map((item) => ({
            label: item.moduleTag,
            score: item.score,
            detail: [item.judgement, item.evidenceNote].filter(Boolean).join(" | "),
        }))
        : parseModuleResults(pick(sessionRaw, "moduleScoresJson", "module_scores_json"));
    return {
        sessionId: toStringValue(pick(sessionRaw, "id", "sessionId", "session_id")),
        questionSetId: toStringValue(pick(sessionRaw, "qaSetId", "questionSetId", "question_set_id")),
        score: toNumberValue(pick(sessionRaw, "score"), 0),
        summary: detail?.summary || toStringValue(pick(sessionRaw, "summary"), ""),
        strengths: detail?.strengths ?? parseJsonStringArray(pick(sessionRaw, "strengthsJson", "strengths_json")),
        gaps: detail?.gaps ?? parseJsonStringArray(pick(sessionRaw, "gapsJson", "gaps_json")),
        moduleResults,
        reviewOrder: detail?.reviewOrder ?? [],
        evidenceRefs: detail?.evidenceRefs ?? [],
        detail,
        completedCount: toNumberValue(pick(sessionRaw, "completedCount", "completed_count", "currentIndex", "current_index"), 0),
        totalCount: toNumberValue(pick(sessionRaw, "totalCount", "total_count", "totalQuestions", "total_questions"), 0),
    };
}

export function normalizePracticeAnswer(raw: unknown): PracticeAnswer {
    const attempt = isObject(pick(raw, "attempt")) ? pick(raw, "attempt") : null;
    const feedbackDetail = parseFeedbackDetail(pick(attempt, "modelFeedback", "model_feedback"));
    const suggestions = feedbackDetail?.suggestions ?? toStringValue(pick(attempt, "improvementSuggestion", "improvement_suggestion"), "")
        .split("\n")
        .map((item) => item.trim())
        .filter(Boolean);
    const missingPoints = feedbackDetail?.missingPoints ?? toStringValue(pick(attempt, "missingPoints", "missing_points"), "")
        .split("\n")
        .map((item) => item.trim())
        .filter(Boolean);
    return {
        sessionId: toStringValue(pick(attempt, "sessionId", "session_id")),
        score: toNumberValue(pick(attempt, "score"), 0),
        result: toStringValue(pick(attempt, "result"), ""),
        currentAnswer: toStringValue(pick(raw, "currentAnswer", "current_answer"), toStringValue(pick(attempt, "userAnswer", "user_answer"), "")),
        feedback: feedbackDetail?.reason || toStringValue(pick(attempt, "reason"), ""),
        answerGuide: toStringValue(pick(raw, "standardAnswer"), toStringValue(pick(attempt, "improvementSuggestion", "improvement_suggestion"), "")),
        standardAnswer: toStringValue(pick(raw, "standardAnswer"), ""),
        nextQuestion: normalizePracticeQuestion(pick(raw, "nextQuestion", "next_question")),
        feedbackDetail,
        missingPoints,
        suggestions,
        evidenceRefs: feedbackDetail?.evidenceRefs ?? [],
    };
}

export function normalizeQuestionSetDetail(raw: unknown) {
    const qaSetRaw = isObject(pick(raw, "qaSet")) ? pick(raw, "qaSet") : raw;
    const itemsRaw = Array.isArray(pick(raw, "items")) ? (pick(raw, "items") as unknown[]) : [];
    return {
        questionSet: normalizeQuestionSet(qaSetRaw),
        items: itemsRaw.map(normalizeQuestionItem),
    };
}

export function toProfilePayload(profile: Profile) {
    return {
        targetRole: profile.targetRole,
        targetDirection: profile.targetDirection,
        allowGeneralKnowledge: profile.allowGeneralKnowledge,
        answerStyle: profile.answerStyle,
        feedbackStyle: profile.feedbackStyle,
        grade: profile.grade,
        education: profile.education,
        stage: profile.stage,
        companyType: profile.companyType,
        note: profile.note,
    };
}

export function toLoginPayload(input: LoginInput) {
    return {
        account: input.account,
        password: input.password,
        remember: input.remember ?? true,
    };
}

export function toRegisterPayload(input: RegisterInput) {
    return {
        username: input.name,
        name: input.name,
        email: input.email,
        password: input.password,
    };
}

export function toGenerateQuestionSetPayload(input: GenerateQuestionSetInput) {
    return {
        title: input.title,
        note: input.note,
        allowGeneralKnowledge: input.allowGeneralKnowledge,
        requestedQuestionCount: input.questionCount ?? 6,
        documentIds: input.sourceDocumentIds,
    };
}

export function toQuestionSetPayload(input: UpdateQuestionSetInput) {
    return {
        title: input.title,
    };
}

export function toQuestionItemPayload(input: QuestionItemDraft) {
    return {
        question: input.question,
        knowledgeNote: input.knowledgeNote,
        interviewAnswer: input.interviewAnswer,
        moduleTag: input.moduleTag,
        tags: input.tags,
        difficulty: input.difficulty,
        conflictTip: input.conflictTip,
    };
}

export function toPracticeStartPayload(input: PracticeStartInput) {
    return {
        qaSetId: input.questionSetId,
        mode: input.mode,
        feedbackMode: input.feedbackMode,
        moduleTag: input.moduleTag,
    };
}

export function toPracticeAnswerPayload(input: PracticeAnswerInput) {
    return {
        answer: input.answer,
    };
}

export function useCurrentUserQuery(options: QueryControlOptions = {}) {
    return useQuery({
        queryKey: apiKeys.currentUser,
        enabled: options.enabled ?? true,
        retry: false,
        queryFn: async () => normalizeAuthUser(await apiRequest<AuthUser>("/api/auth/me")),
    });
}

export function useProfileQuery() {
    return useQuery({
        queryKey: apiKeys.profile,
        queryFn: async () => normalizeProfile(await apiRequest<Profile>("/api/profile")),
    });
}

export function useSaveProfileMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (profile: Profile) => normalizeProfile(await apiRequest<Profile>("/api/profile", {
            method: "PUT",
            body: toProfilePayload(profile),
        })),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.profile });
        },
    });
}

export function useLoginMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: LoginInput) => {
            const session = await apiRequest<AuthSession>("/api/auth/login", {
                method: "POST",
                body: toLoginPayload(input),
                auth: false,
            });
            const token = session.accessToken ?? session.token ?? "";
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
            const session = await apiRequest<AuthSession>("/api/auth/register", {
                method: "POST",
                body: toRegisterPayload(input),
                auth: false,
            });
            const token = session.accessToken ?? session.token ?? "";
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
        queryFn: async () => (await apiRequest<unknown[]>("/api/documents")).map(normalizeDocument),
    });
}

export function useDocumentQuery(documentId?: string) {
    return useQuery({
        queryKey: ["documents", documentId ?? ""] as const,
        enabled: Boolean(documentId),
        queryFn: async () => normalizeDocument(await apiRequest<unknown>(`/api/documents/${documentId}`)),
    });
}

export function useDeleteDocumentMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (documentId: string) => {
            await apiRequest<void>(`/api/documents/${documentId}`, { method: "DELETE" });
            return documentId;
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.documents });
        },
    });
}

export function useUploadDocumentsMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (files: File[]) => {
            const formData = new FormData();
            files.forEach((file) => {
                formData.append("files", file);
            });
            const uploaded = await apiRequest<unknown[]>("/api/documents/upload", {
                method: "POST",
                body: formData,
            });
            return uploaded.map(normalizeDocument);
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
        queryFn: async () => (await apiRequest<unknown[]>("/api/qa-sets")).map(normalizeQuestionSet),
    });
}

export function useQuestionSetQuery(questionSetId?: string) {
    return useQuery({
        queryKey: apiKeys.questionSet(questionSetId ?? ""),
        enabled: Boolean(questionSetId),
        queryFn: async () => normalizeQuestionSetDetail(await apiRequest<unknown>(`/api/qa-sets/${questionSetId}`)).questionSet,
    });
}

export function useQuestionSetItemsQuery(questionSetId?: string) {
    return useQuery({
        queryKey: apiKeys.questionSetItems(questionSetId ?? ""),
        enabled: Boolean(questionSetId),
        queryFn: async () => normalizeQuestionSetDetail(await apiRequest<unknown>(`/api/qa-sets/${questionSetId}`)).items,
    });
}

export function useGenerateQuestionSetMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: GenerateQuestionSetInput) => {
            const result = await apiRequest<unknown>("/api/qa-sets/generate", {
                method: "POST",
                body: toGenerateQuestionSetPayload(input),
            });
            return normalizeGenerationTask(result);
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
            await queryClient.invalidateQueries({ queryKey: apiKeys.documents });
        },
    });
}

export function useDeleteQuestionSetMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (questionSetId: string) => {
            await apiRequest<void>(`/api/qa-sets/${questionSetId}`, {
                method: "DELETE",
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
        mutationFn: async (input: UpdateQuestionSetInput) => normalizeQuestionSet(await apiRequest<unknown>(`/api/qa-sets/${input.questionSetId}`, {
            method: "PUT",
            body: toQuestionSetPayload(input),
        })),
        onSuccess: async (_result, variables) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSet(variables.questionSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSetItems(variables.questionSetId) });
        },
    });
}

export function useCreateQuestionItemMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: CreateQuestionItemInput) => normalizeQuestionItem(await apiRequest<unknown>(`/api/qa-sets/${input.questionSetId}/items`, {
            method: "POST",
            body: toQuestionItemPayload(input),
        })),
        onSuccess: async (_result, variables) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSet(variables.questionSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSetItems(variables.questionSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
        },
    });
}

export function useUpdateQuestionItemMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: UpdateQuestionItemInput) => normalizeQuestionItem(await apiRequest<unknown>(`/api/qa-items/${input.questionItemId}`, {
            method: "PUT",
            body: toQuestionItemPayload(input),
        })),
        onSuccess: async (_result, variables) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSet(variables.questionSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSetItems(variables.questionSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
        },
    });
}

export function useDeleteQuestionItemMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: DeleteQuestionItemInput) => {
            await apiRequest<void>(`/api/qa-items/${input.questionItemId}`, {
                method: "DELETE",
            });
            return input;
        },
        onSuccess: async (_result, variables) => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSet(variables.questionSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSetItems(variables.questionSetId) });
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
        },
    });
}

export function useGenerationTaskQuery(taskId?: string) {
    return useQuery({
        queryKey: apiKeys.generationTask(taskId ?? ""),
        enabled: Boolean(taskId),
        refetchInterval: (query) => {
            const data = query.state.data as GenerationTask | undefined;
            if (!data) {
                return 2000;
            }
            return data.stage === "COMPLETED" || data.stage === "FAILED" ? false : 2000;
        },
        queryFn: async () => normalizeGenerationTask(await apiRequest<unknown>(`/api/jobs/${taskId}`)),
    });
}

export function usePracticeSessionQuery(sessionId?: string) {
    return useQuery({
        queryKey: apiKeys.practiceSession(sessionId ?? ""),
        enabled: Boolean(sessionId),
        queryFn: async () => normalizePracticeSession(await apiRequest<unknown>(`/api/practice-sessions/${sessionId}`)),
    });
}

export function usePracticeResultQuery(sessionId?: string) {
    return useQuery({
        queryKey: apiKeys.practiceResult(sessionId ?? ""),
        enabled: Boolean(sessionId),
        queryFn: async () => normalizePracticeResult(await apiRequest<unknown>(`/api/practice-sessions/${sessionId}/result`)),
    });
}

export function useStartPracticeSessionMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: PracticeStartInput) => normalizePracticeSession(await apiRequest<unknown>("/api/practice-sessions", {
            method: "POST",
            body: toPracticeStartPayload(input),
        })),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: apiKeys.questionSets });
        },
    });
}

export function useSubmitPracticeAnswerMutation() {
    return useMutation({
        mutationFn: async (input: PracticeAnswerInput) => normalizePracticeAnswer(await apiRequest<unknown>(`/api/practice-sessions/${input.sessionId}/answer`, {
            method: "POST",
            body: toPracticeAnswerPayload(input),
        })),
    });
}

export function useMarkUnknownMutation() {
    return useMutation({
        mutationFn: async (sessionId: string) => normalizePracticeAnswer(await apiRequest<unknown>(`/api/practice-sessions/${sessionId}/mark-unknown`, {
            method: "POST",
        })),
    });
}

export function useContinuePracticeSessionMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (sessionId: string) => normalizePracticeSession(await apiRequest<unknown>(`/api/practice-sessions/${sessionId}/continue`, {
            method: "POST",
        })),
        onSuccess: async (session, sessionId) => {
            queryClient.setQueryData(apiKeys.practiceSession(sessionId), session);
            await queryClient.invalidateQueries({ queryKey: apiKeys.practiceSession(sessionId) });
        },
    });
}

export function useFinishPracticeSessionMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (sessionId: string) => normalizePracticeResult(await apiRequest<unknown>(`/api/practice-sessions/${sessionId}/finish`, {
            method: "POST",
        })),
        onSuccess: async (result, sessionId) => {
            queryClient.setQueryData(apiKeys.practiceResult(sessionId), result);
            await queryClient.invalidateQueries({ queryKey: apiKeys.practiceResult(sessionId) });
        },
    });
}
