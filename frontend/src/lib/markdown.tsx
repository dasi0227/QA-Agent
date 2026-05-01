import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

type MarkdownRendererProps = {
    content: string;
    className?: string;
    emptyLabel?: string;
};

type MarkdownBlock =
    | { type: "heading"; level: number; text: string }
    | { type: "paragraph"; text: string }
    | { type: "unordered-list"; items: string[] }
    | { type: "ordered-list"; items: string[] }
    | { type: "blockquote"; text: string }
    | { type: "code"; text: string }
    | { type: "hr" };

function renderInline(text: string, keyPrefix: string): ReactNode[] {
    const nodes: ReactNode[] = [];
    const pattern = /(`[^`]+`|\[[^\]]+\]\([^)]+\)|\*\*[^*]+\*\*|\*[^*]+\*)/g;
    let lastIndex = 0;
    let match: RegExpExecArray | null;
    let index = 0;

    while ((match = pattern.exec(text))) {
        if (match.index > lastIndex) {
            nodes.push(text.slice(lastIndex, match.index));
        }

        const token = match[0];
        if (token.startsWith("`")) {
            nodes.push(
                <code key={`${keyPrefix}-code-${index}`} className="document-markdown__inline-code">
                    {token.slice(1, -1)}
                </code>,
            );
        } else if (token.startsWith("**")) {
            nodes.push(
                <strong key={`${keyPrefix}-strong-${index}`}>
                    {token.slice(2, -2)}
                </strong>,
            );
        } else if (token.startsWith("*")) {
            nodes.push(
                <em key={`${keyPrefix}-em-${index}`}>
                    {token.slice(1, -1)}
                </em>,
            );
        } else if (token.startsWith("[")) {
            const splitIndex = token.lastIndexOf("](");
            const label = token.slice(1, splitIndex);
            const href = token.slice(splitIndex + 2, -1);
            nodes.push(
                <a
                    key={`${keyPrefix}-link-${index}`}
                    className="document-markdown__link"
                    href={href}
                    target="_blank"
                    rel="noreferrer"
                >
                    {label}
                </a>,
            );
        }

        lastIndex = match.index + token.length;
        index += 1;
    }

    if (lastIndex < text.length) {
        nodes.push(text.slice(lastIndex));
    }

    return nodes;
}

function parseMarkdown(content: string): MarkdownBlock[] {
    const lines = content.replace(/\r\n/g, "\n").split("\n");
    const blocks: MarkdownBlock[] = [];
    let index = 0;

    const flushParagraph = (buffer: string[]) => {
        if (buffer.length > 0) {
            blocks.push({ type: "paragraph", text: buffer.join(" ").trim() });
            buffer.length = 0;
        }
    };

    while (index < lines.length) {
        const line = lines[index];
        const trimmed = line.trim();

        if (!trimmed) {
            index += 1;
            continue;
        }

        const headingMatch = trimmed.match(/^(#{1,6})\s+(.*)$/);
        if (headingMatch) {
            blocks.push({ type: "heading", level: headingMatch[1].length, text: headingMatch[2].trim() });
            index += 1;
            continue;
        }

        if (/^(-{3,}|\*{3,}|_{3,})$/.test(trimmed)) {
            blocks.push({ type: "hr" });
            index += 1;
            continue;
        }

        if (trimmed.startsWith("```")) {
            const codeLines: string[] = [];
            index += 1;
            while (index < lines.length && !lines[index].trim().startsWith("```")) {
                codeLines.push(lines[index]);
                index += 1;
            }
            if (index < lines.length) {
                index += 1;
            }
            blocks.push({ type: "code", text: codeLines.join("\n") });
            continue;
        }

        if (trimmed.startsWith(">")) {
            const quoteLines: string[] = [];
            while (index < lines.length && lines[index].trim().startsWith(">")) {
                quoteLines.push(lines[index].replace(/^\s*>\s?/, ""));
                index += 1;
            }
            blocks.push({ type: "blockquote", text: quoteLines.join(" ") });
            continue;
        }

        const unorderedMatch = trimmed.match(/^[-*+]\s+(.*)$/);
        const orderedMatch = trimmed.match(/^\d+\.\s+(.*)$/);
        if (unorderedMatch || orderedMatch) {
            const ordered = Boolean(orderedMatch);
            const items: string[] = [];
            while (index < lines.length) {
                const current = lines[index].trim();
                const listMatch = ordered ? current.match(/^\d+\.\s+(.*)$/) : current.match(/^[-*+]\s+(.*)$/);
                if (!listMatch) {
                    break;
                }
                items.push(listMatch[1].trim());
                index += 1;
            }
            blocks.push({ type: ordered ? "ordered-list" : "unordered-list", items });
            continue;
        }

        const paragraphLines: string[] = [trimmed];
        index += 1;
        while (index < lines.length) {
            const nextLine = lines[index];
            const nextTrimmed = nextLine.trim();
            if (
                !nextTrimmed
                || nextTrimmed.startsWith("#")
                || nextTrimmed.startsWith(">")
                || nextTrimmed.startsWith("```")
                || /^(-{3,}|\*{3,}|_{3,})$/.test(nextTrimmed)
                || /^[-*+]\s+/.test(nextTrimmed)
                || /^\d+\.\s+/.test(nextTrimmed)
            ) {
                break;
            }
            paragraphLines.push(nextTrimmed);
            index += 1;
        }
        flushParagraph(paragraphLines);
    }

    return blocks;
}

export function MarkdownRenderer({ content, className, emptyLabel = "暂无正文" }: MarkdownRendererProps) {
    const blocks = parseMarkdown(content);

    if (!content.trim()) {
        return <div className={cn("document-markdown", className)}><p className="document-markdown__empty">{emptyLabel}</p></div>;
    }

    return (
        <div className={cn("document-markdown", className)}>
            {blocks.map((block, blockIndex) => {
                if (block.type === "heading") {
                    const HeadingTag = `h${block.level}` as keyof JSX.IntrinsicElements;
                    return (
                        <HeadingTag
                            key={`heading-${blockIndex}`}
                            className={cn("document-markdown__heading", `document-markdown__heading--${block.level}`)}
                        >
                            {renderInline(block.text, `heading-${blockIndex}`)}
                        </HeadingTag>
                    );
                }

                if (block.type === "paragraph") {
                    return (
                        <p key={`paragraph-${blockIndex}`} className="document-markdown__paragraph">
                            {renderInline(block.text, `paragraph-${blockIndex}`)}
                        </p>
                    );
                }

                if (block.type === "unordered-list" || block.type === "ordered-list") {
                    const ListTag = block.type === "ordered-list" ? "ol" : "ul";
                    return (
                        <ListTag key={`${block.type}-${blockIndex}`} className="document-markdown__list">
                            {block.items.map((item, itemIndex) => (
                                <li key={`${block.type}-${blockIndex}-item-${itemIndex}`} className="document-markdown__list-item">
                                    {renderInline(item, `${block.type}-${blockIndex}-${itemIndex}`)}
                                </li>
                            ))}
                        </ListTag>
                    );
                }

                if (block.type === "blockquote") {
                    return (
                        <blockquote key={`blockquote-${blockIndex}`} className="document-markdown__blockquote">
                            {renderInline(block.text, `blockquote-${blockIndex}`)}
                        </blockquote>
                    );
                }

                if (block.type === "code") {
                    return (
                        <pre key={`code-${blockIndex}`} className="document-markdown__code-block">
                            <code>{block.text}</code>
                        </pre>
                    );
                }

                return <hr key={`hr-${blockIndex}`} className="document-markdown__hr" />;
            })}
        </div>
    );
}
