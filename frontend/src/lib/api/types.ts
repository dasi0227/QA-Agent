export type ApiResult<T> = {
    code: number;
    msg: string;
    data: T;
};

export type AuthUser = {
    id: string;
    username: string;
    email: string;
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

export type RegisterInput = {
    name: string;
    email: string;
    password: string;
    remember?: boolean;
};

export type Profile = {
    targetRole: string;
    targetDomain: string;
    targetCompany: string;
    allowGeneralKnowledge: boolean;
    allowWebSearch: boolean;
    answerStyle: string;
    feedbackStyle: string;
    age: string;
    grade: string;
    major: string;
    stage: string;
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
