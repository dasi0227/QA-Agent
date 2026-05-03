import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { cn } from "@/lib/cn";

type MarkdownRendererProps = {
    content: string;
    className?: string;
    emptyLabel?: string;
};

export function MarkdownRenderer({ content, className, emptyLabel = "暂无正文" }: MarkdownRendererProps) {
    const normalized = content.replace(/\\n/g, "\n");

    if (!normalized.trim()) {
        return (
            <div className={cn("document-markdown", className)}>
                <p className="document-markdown__empty">{emptyLabel}</p>
            </div>
        );
    }

    return (
        <div className={cn("document-markdown", className)}>
            <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {normalized}
            </ReactMarkdown>
        </div>
    );
}
