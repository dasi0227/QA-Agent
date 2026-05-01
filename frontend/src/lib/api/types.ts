export type ApiErrorBody = {
    code?: string;
    message?: string;
};

export type ApiResponseEnvelope<T> = {
    success?: boolean;
    data?: T;
    error?: ApiErrorBody;
};

export type AuthUser = {
    id: string;
    username: string;
    email: string;
    displayName?: string;
    status?: "ACTIVE" | "LOCKED" | "DELETED";
    profileCompleted?: boolean;
};

export type AuthSession = {
    accessToken?: string;
    token?: string;
    refreshToken?: string;
    user?: AuthUser;
};

export type LoginInput = {
    account: string;
    password: string;
    remember?: boolean;
};

export type RegisterInput = {
    name: string;
    email: string;
    password: string;
    remember?: boolean;
};

export type Profile = {
    targetRole: string;
    targetDirection: string;
    allowGeneralKnowledge: boolean;
    answerStyle: string;
    feedbackStyle: string;
    grade: string;
    education: string;
    stage: string;
    companyType: string;
    note: string;
};

export type DocumentRecord = {
    id: string;
    fileName: string;
    fileType: "markdown" | "text";
    size?: number;
    createdAt?: string;
    updatedAt?: string;
    rawContent?: string;
    normalizedText?: string;
    summary?: string;
    contentPreview?: string;
    chunkCount?: number;
    usedInGeneration?: boolean;
};

export type QuestionSet = {
    id: string;
    title: string;
    note?: string;
    moduleTags: string[];
    questionCount: number;
    practiceCount: number;
    averageScore: number;
    lastPracticedAt: string;
    status?: string;
    documentCount?: number;
    createdAt?: string;
    updatedAt?: string;
};

export type QuestionItem = {
    id: string;
    questionSetId: string;
    question: string;
    knowledgeNote: string;
    interviewAnswer: string;
    moduleTag: string;
    tags: string[];
    sortOrder: number;
    status: string;
    difficulty?: "EASY" | "MEDIUM" | "HARD" | string;
    conflictTip?: string;
    scoringRubric?: {
        keyPoints: string[];
        answerStructure: string;
        evidenceRefs: string[];
        promptKey?: string;
        promptVersion?: string;
        usedFallback?: boolean;
    };
    sourceChunkIds?: string[];
};

export type QuestionItemDraft = {
    question: string;
    knowledgeNote: string;
    interviewAnswer: string;
    moduleTag: string;
    tags: string;
    difficulty: string;
    conflictTip: string;
};

export type UpdateQuestionSetInput = {
    questionSetId: string;
    title: string;
};

export type CreateQuestionItemInput = QuestionItemDraft & {
    questionSetId: string;
};

export type UpdateQuestionItemInput = QuestionItemDraft & {
    questionSetId: string;
    questionItemId: string;
};

export type DeleteQuestionItemInput = {
    questionSetId: string;
    questionItemId: string;
};

export type GenerationStage =
    | "QUEUED"
    | "PARSING"
    | "PLANNING"
    | "GENERATING"
    | "VALIDATING"
    | "OPTIMIZING"
    | "COMPLETED"
    | "FAILED"
    | string;

export type GenerationTask = {
    id: string;
    title?: string;
    note?: string;
    allowGeneralKnowledge?: boolean;
    requestedQuestionCount?: number;
    type: string;
    targetId?: string;
    status: string;
    stage: GenerationStage;
    progress: number;
    message: string;
    errorMessage?: string;
    documentIds?: string[];
    documentNames?: string[];
    createdAt?: string;
    updatedAt?: string;
    startedAt?: string;
    completedAt?: string;
    questionSetId?: string;
};

export type PracticeMode = "SEQUENTIAL" | "BY_MODULE" | "RANDOM";

export type FeedbackMode = "ITEM_BY_ITEM" | "AFTER_ALL";

export type PracticeQuestion = {
    id: string;
    questionSetId: string;
    question: string;
    moduleTag: string;
    tags: string[];
    hint: string;
    knowledgeNote?: string;
    interviewAnswer?: string;
    answerGuide?: string;
    difficulty?: "EASY" | "MEDIUM" | "HARD" | string;
    conflictTip?: string;
    scoringRubric?: QuestionItem["scoringRubric"];
    sourceChunkIds?: string[];
};

export type PracticeFeedbackDetail = {
    promptKey?: string;
    promptVersion?: string;
    usedFallback?: boolean;
    judgement: string;
    scoreHint: number;
    reason: string;
    missingPoints: string[];
    suggestions: string[];
    evidenceRefs: string[];
};

export type PracticeSession = {
    id: string;
    questionSetId: string;
    questionSetTitle?: string;
    mode: PracticeMode;
    feedbackMode: FeedbackMode;
    status: string;
    currentQuestionIndex: number;
    totalQuestions: number;
    currentQuestion: PracticeQuestion | null;
    answeredCount?: number;
    canRevealAnswer?: boolean;
    currentAnswer?: string;
    feedback?: string;
    answerGuide?: string;
    score?: number;
    summary?: string;
    strengths?: string[];
    gaps?: string[];
    moduleResults?: Array<{ label: string; score: number; detail: string }>;
    latestAnswer?: PracticeAnswer | null;
};

export type PracticeAnswer = {
    sessionId: string;
    score: number;
    result: string;
    currentAnswer?: string;
    feedback: string;
    answerGuide: string;
    standardAnswer?: string;
    nextQuestion?: PracticeQuestion | null;
    feedbackDetail?: PracticeFeedbackDetail | null;
    missingPoints?: string[];
    suggestions?: string[];
    evidenceRefs?: string[];
};

export type PracticeResultDetail = {
    promptKey?: string;
    promptVersion?: string;
    usedFallback?: boolean;
    finalScore: number;
    summary: string;
    moduleScores: Array<{
        moduleTag: string;
        score: number;
        judgement: string;
        evidenceNote: string;
    }>;
    strengths: string[];
    gaps: string[];
    reviewOrder: string[];
    evidenceRefs: string[];
};

export type PracticeResult = {
    sessionId: string;
    questionSetId: string;
    score: number;
    summary: string;
    strengths: string[];
    gaps: string[];
    moduleResults: Array<{ label: string; score: number; detail: string }>;
    reviewOrder?: string[];
    evidenceRefs?: string[];
    detail?: PracticeResultDetail | null;
    completedCount?: number;
    totalCount?: number;
};

export type PracticeStartInput = {
    questionSetId: string;
    mode: PracticeMode;
    feedbackMode: FeedbackMode;
    moduleTag?: string;
};

export type PracticeAnswerInput = {
    sessionId: string;
    answer: string;
};

export type GenerateQuestionSetInput = {
    sourceDocumentIds: string[];
    note: string;
    title: string;
    allowGeneralKnowledge: boolean;
    questionCount?: number;
};

export type QuestionSetDetail = {
    questionSet: QuestionSet;
    items: QuestionItem[];
};
