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
    allowGeneralKnowledge: boolean;
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

export type DocumentRecord = {
    id: string;
    fileName: string;
    fileType: string;
    filePath: string;
    rawContent: string;
    normalizedContent: string;
    summary: string;
    moduleTagsJson: string;
    referenceCount: number;
    deleted: boolean;
    createdAt?: string;
    updatedAt?: string;
};

export type QuestionSet = {
    id: string;
    taskId: string;
    title: string;
    description: string;
    moduleTagsJson: string;
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
    conflictTip: string;
    sourceChunkIdsJson: string;
    sortOrder: number;
};

export type QuestionItemDraft = {
    question: string;
    knowledgeNote: string;
    answer: string;
    moduleTag: string;
    difficulty: string;
    conflictTip: string;
    sourceChunkIdsJson: string;
};

export type UpdateQuestionSetInput = {
    questionSetId: string;
    title: string;
    description: string;
};

export type CreateQuestionItemInput = QuestionItemDraft & {
    qaSetId: string;
};

export type UpdateQuestionItemInput = QuestionItemDraft & {
    qaSetId: string;
    questionItemId: string;
};

export type DeleteQuestionItemInput = {
    qaSetId: string;
    questionItemId: string;
};

export type CreateQuestionSetInput = {
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
    phase: string;
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
