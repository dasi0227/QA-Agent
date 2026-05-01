import type { DocumentRecord } from "@/lib/api/types";

const DOCUMENT_DRAFT_PREFIX = "qa:document-draft:";

export type DocumentDraftSnapshot = {
    rawContent: string;
    updatedAt: string;
};

function canUseStorage() {
    return typeof window !== "undefined";
}

function buildStorageKey(documentId: string) {
    return `${DOCUMENT_DRAFT_PREFIX}${documentId}`;
}

export function readDocumentDraft(documentId: string): DocumentDraftSnapshot | null {
    if (!canUseStorage() || !documentId) {
        return null;
    }

    const raw = window.localStorage.getItem(buildStorageKey(documentId));
    if (!raw) {
        return null;
    }

    try {
        const parsed = JSON.parse(raw) as Partial<DocumentDraftSnapshot>;
        return {
            rawContent: typeof parsed.rawContent === "string" ? parsed.rawContent : "",
            updatedAt: typeof parsed.updatedAt === "string" ? parsed.updatedAt : "",
        };
    } catch {
        return null;
    }
}

export function writeDocumentDraft(documentId: string, rawContent: string) {
    if (!canUseStorage() || !documentId) {
        return null;
    }

    const snapshot: DocumentDraftSnapshot = {
        rawContent,
        updatedAt: new Date().toISOString(),
    };

    window.localStorage.setItem(buildStorageKey(documentId), JSON.stringify(snapshot));
    return snapshot;
}

export function clearDocumentDraft(documentId: string) {
    if (!canUseStorage() || !documentId) {
        return;
    }

    window.localStorage.removeItem(buildStorageKey(documentId));
}

export function normalizeDocumentText(value: string) {
    return value.replace(/\r\n/g, "\n").replace(/\\n/g, "\n");
}

export function getDocumentSourceText(document?: DocumentRecord | null) {
    if (!document) {
        return "";
    }

    const savedDraft = readDocumentDraft(document.id);
    if (savedDraft) {
        return savedDraft.rawContent;
    }

    return (
        normalizeDocumentText(
            document.rawContent
            || document.normalizedText
            || document.contentPreview
            || document.summary
            || "",
        )
    );
}

export function getDocumentEffectiveUpdatedAt(document?: DocumentRecord | null) {
    if (!document) {
        return "";
    }

    const savedDraft = readDocumentDraft(document.id);
    return savedDraft?.updatedAt || document.updatedAt || document.createdAt || "";
}
