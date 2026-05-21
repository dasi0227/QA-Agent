import type { ReactNode } from "react";

type PracticeLayoutProps = {
    topStatus: ReactNode;
    workspace: ReactNode;
    answerCard: ReactNode;
    answerCardCollapsed?: boolean;
};

export function PracticeLayout({ topStatus, workspace, answerCard, answerCardCollapsed = false }: PracticeLayoutProps) {
    return (
        <div className="practice-shell">
            <header className="practice-top-status">
                {topStatus}
            </header>
            <div className={`practice-main${answerCardCollapsed ? " practice-main--card-collapsed" : ""}`}>
                {workspace}
                {answerCard}
            </div>
        </div>
    );
}
