export type ApiResult<T> = {
    code: number;
    msg: string;
    data: T;
};

export type AuthUser = {
    id: string;
    username: string;
    email: string;
    avatar?: string;
    status?: "ACTIVE" | "LOCKED" | "DISABLED" | string;
    profileCompleted?: boolean;
};

export type AuthSession = {
    userId?: string;
    username?: string;
    email?: string;
    status?: string;
    profileCompleted?: boolean;
    accessToken?: string;
    refreshToken?: string;
};

export type LoginInput = {
    account: string;
    password: string;
    remember?: boolean;
};

export type SendVerifyCodeInput = {
    email: string;
};

export type RegisterInput = {
    name: string;
    email: string;
    password: string;
    verifyCode: string;
    remember?: boolean;
};

export type Profile = {
    targetRole: string;
    targetDomain: string;
    targetCompany: string;
    allowReferMemory: boolean;
    allowWebSearch: boolean;
    allowFallback: boolean;
    answerStyle: string;
    feedbackStyle: string;
    grade: string;
    major: string;
    stage: string;
    llmBaseUrl: string;
    llmApiKey: string;
    llmModelName: string;
};

export type ChangePasswordInput = {
    currentPassword: string;
    newPassword: string;
};

export type TempChatInput = {
    tempChatId: string;
    message: string;
};

export type TempChatResponse = {
    role: "assistant" | string;
    content: string;
};

export type UserMemory = {
    id: string;
    memoryType: "AWFUL" | "UNCLEAR" | "MASTER" | string;
    targetType: "MODULE_TAG" | "ANSWER_SKILL" | string;
    targetKey: string;
    summary: string;
    content: string;
    supportCount: number;
    status: "ACTIVE" | "HIDDEN" | string;
    firstSeenAt: string;
    lastSeenAt: string;
    hiddenAt: string;
    latestSessionId: string;
    latestQaSetId: string;
    createdAt?: string;
    updatedAt?: string;
};

export type UserMemoryEvidence = {
    id: string;
    memoryId: string;
    sessionId: string;
    sessionItemId: string;
    qaSetId: string;
    qaItemId: string;
    moduleTag: string;
    questionSnapshot: string;
    result: string;
    score: number;
    sourceChunkIdsJson: string;
    evidenceSummary: string;
    createdAt?: string;
};

export type UserMemoryDetail = {
    memory: UserMemory;
    evidenceList: UserMemoryEvidence[];
};

export type DocumentRecord = {
    id: string;
    fileName: string;
    fileType: string;
    filePath: string;
    rawContent: string;
    indexStatus: string;
    referenceCount: number;
    createdAt?: string;
    updatedAt?: string;
};

export type UpdateDocumentInput = {
    id: string;
    fileName: string;
};

export type DocumentChunkRecord = {
    id: string;
    documentId: string;
    fileName: string;
    chunkIndex: number;
    headingPath: string;
    content: string;
    summary: string;
    moduleTagsJson: string;
};

export type QuestionSet = {
    id: string;
    taskId: string;
    title: string;
    description: string;
    moduleTagsJson: string;
    documentCount: number;
    questionCount: number;
    practiceCount: number;
    averageScore: number;
    bestScore: number;
    averageAccuracy: number;
    bestAccuracy: number;
    lastPracticedAt: string;
    createdAt?: string;
    updatedAt?: string;
};

export type QuestionItem = {
    id: string;
    qaSetId: string;
    question: string;
    knowledgeNote: string;
    answer: string;
    moduleTag: string;
    difficulty: string;
    keywords: string;
    hint: string;
    sourceReliable: boolean;
    isImported: boolean;
    sourceChunkIdsJson: string;
    completeStatus: "PROCESSING" | "SOLVED" | "UNSOLVED" | string;
    sortOrder: number;
    practiceTotalCount: number | null;
    practiceAverageScore: number | null;
    practiceCorrectRate: number | null;
};

export type QuestionItemDraft = {
    question: string;
    knowledgeNote: string;
    answer: string;
    moduleTag: string;
    difficulty: string;
    keywords: string;
    hint: string;
    sourceReliable: boolean;
    sourceChunkIdsJson: string;
};

export type UpdateQuestionSetInput = {
    questionSetId: string;
    title?: string;
    description?: string;
    moduleTagsJson?: string;
};

export type ImportQuestionSetInput = {
    file: File;
};

export type ExportQuestionSetFile = {
    fileName: string;
    blob: Blob;
};

export type UpdateQuestionItemInput = QuestionItemDraft & {
    qaSetId: string;
    questionItemId: string;
};

export type CreateSmartQuestionItemInput = {
    qaSetId: string;
    question: string;
    answer?: string;
};

export type CreateSmartQuestionItemBatchInput = {
    qaSetId: string;
    items: { question: string; answer?: string }[];
};

export type RetryCompleteQuestionItemInput = {
    id: string;
    question: string;
    answer?: string;
};

export type DeleteQuestionItemInput = {
    qaSetId: string;
    questionItemId: string;
};

export type CreateEmptyQuestionSetInput = {
    title: string;
    description?: string;
};

export type CreateQuestionSetInput = {
    taskId?: string;
    title: string;
    userPrompt: string;
    documentIds: string[];
    requestedQuestionCount: number;
    jobDescription?: string;
};

export type TaskStatus = {
    taskId: string;
    userId: string;
    title: string;
    userPrompt: string;
    documentIdsJson: string;
    documentNamesJson: string;
    qaSetId: string;
    status: string;
    stage: string;
    errorCode: string;
    errorMessage: string;
    requestedQuestionCount: number;
    createdAt: string;
    startedAt: string;
    completedAt: string;
};

export type TaskMessage = {
    id: string;
    taskId: string;
    stage: string;
    message: string;
    content: string;
    createdAt: string;
};

export type TaskListItem = {
    taskId: string;
    title: string;
    status: string;
    stage: string;
    qaSetId: string;
    createdAt: string;
};

export type SseEvent = {
    taskId: string;
    stage: string;
    status: string;
    message: string;
    timestamp: number;
    currentTokens: number;
    totalTokens: number;
    isCompleted: boolean;
};

export type PracticeSession = {
    id: string;
    qaSetId: string;
    mode: string;
    feedbackMode: string;
    status: string;
    selectedModule: string;
    totalQuestions: number;
    answeredCount: number;
    score: number;
    accuracy: number;
    summary: string;
    startedAt: string;
    finishedAt: string;
    createdAt?: string;
    updatedAt?: string;
};

export type PracticeMode = "SEQUENTIAL" | "RANDOM" | string;

export type PracticeFeedbackMode = "ITEM_BY_ITEM" | "AFTER_ALL" | string;

export type PracticeSessionStatus = "IN_PROGRESS" | "FINISHED" | "ABANDONED" | string;

export type PracticeItemStatus = "UNANSWERED" | "DRAFT" | "UNKNOWN" | "SUBMITTED" | string;

export type JudgeDetail = {
    missingPoints?: string[];
    wrongPoints?: string[];
    improvementAdvice?: string;
    betterAnswer?: string;
};

export type HintDetail = {
    memoryTip?: string;
    encouragement?: string;
};

export type AssessPoint = {
    title?: string;
    analysis?: string;
    moduleTag?: string;
};

export type AssessDetail = {
    overallComment?: string;
    reviewGuidance?: string;
    strengths?: AssessPoint[];
    weaknesses?: AssessPoint[];
    reviewSuggestions?: string[];
};

export type PracticeFlowSession = {
    id: string;
    qaSetId: string;
    qaSetTitle: string;
    mode: PracticeMode;
    feedbackMode: PracticeFeedbackMode;
    status: PracticeSessionStatus;
    selectedModule: string;
    currentIndex: number;
    durationSeconds: number;
    totalQuestions: number;
    answeredCount: number;
    score: number | null;
    accuracy: number | null;
    perfectCount: number;
    correctCount: number;
    deficientCount: number;
    wrongCount: number;
    unknownCount: number;
    summary: string;
    assessDetail?: AssessDetail | null;
    startedAt: string;
    lastActiveAt: string;
    finishedAt: string;
};

export type PracticeFlowItem = {
    sessionItemId: string;
    qaItemId: string;
    sortOrder: number;
    question: string;
    knowledgeNote: string;
    standardAnswer: string;
    moduleTag: string;
    difficulty: string;
    keywords: string;
    hint: string;
    sourceChunkIdsJson: string;
    userAnswer: string;
    status: PracticeItemStatus;
    unknown: boolean;
    result: string;
    score: number | null;
    feedbackSummary: string;
    judgeDetail?: JudgeDetail | null;
    hintDetail?: HintDetail | null;
    answeredAt: string;
    submittedAt: string;
};

export type PracticeSessionDetail = {
    session: PracticeFlowSession;
    items: PracticeFlowItem[];
};

export type StartPracticeInput = {
    qaSetId: string;
    mode: PracticeMode;
    feedbackMode: PracticeFeedbackMode;
    selectedModule?: string;
    itemIds?: string[];
};

export type SaveAnswerInput = {
    sessionId: string;
    sessionItemId: string;
    userAnswer?: string;
    currentIndex: number;
    durationSeconds?: number;
};

export type SubmitItemInput = SaveAnswerInput;

export type SubmitSessionInput = {
    sessionId: string;
    durationSeconds?: number;
};

export type AbandonPracticeInput = SubmitSessionInput;

export type PracticeSessionItem = {
    id: string;
    sessionId: string;
    qaItemId: string;
    sortOrder: number;
    userAnswer: string;
    result: string;
    score: number;
    feedbackSummary: string;
    answeredAt: string;
    createdAt?: string;
    updatedAt?: string;
};
