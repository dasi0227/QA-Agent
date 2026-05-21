import type { ReactNode } from "react";
import { SiteFooter } from "@/components/layout/SiteFooter";

type PracticeLayoutProps = {
    topStatus: ReactNode;
    workspace: ReactNode;
    answerCard: ReactNode;
};

export function PracticeLayout({ topStatus, workspace, answerCard }: PracticeLayoutProps) {
    return (
        <div className="practice-shell">
            <header className="practice-top-status">
                {topStatus}
            </header>
            <div className="practice-main">
                {workspace}
                {answerCard}
            </div>
            <SiteFooter />
        </div>
    );
}
