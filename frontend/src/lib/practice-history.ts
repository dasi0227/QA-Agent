const LAST_PRACTICE_SESSION_KEY = "qa:last-practice-session";

export type LastPracticeSessionSnapshot = {
    sessionId: string;
    questionSetId: string;
    questionSetTitle: string;
    currentQuestionIndex: number;
    totalQuestions: number;
    currentQuestion: string;
    mode: string;
    feedbackMode: string;
    status: string;
    updatedAt: string;
};

function canUseStorage() {
    return typeof window !== "undefined";
}

export function readLastPracticeSession() {
    if (!canUseStorage()) {
        return null;
    }

    const raw = window.localStorage.getItem(LAST_PRACTICE_SESSION_KEY);
    if (!raw) {
        return null;
    }

    try {
        return JSON.parse(raw) as LastPracticeSessionSnapshot;
    } catch {
        return null;
    }
}

export function saveLastPracticeSession(snapshot: LastPracticeSessionSnapshot) {
    if (!canUseStorage()) {
        return;
    }

    window.localStorage.setItem(LAST_PRACTICE_SESSION_KEY, JSON.stringify(snapshot));
}
