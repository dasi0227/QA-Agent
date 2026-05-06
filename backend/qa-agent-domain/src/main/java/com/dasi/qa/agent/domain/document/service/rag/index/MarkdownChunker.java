package com.dasi.qa.agent.domain.document.service.rag.index;

import com.dasi.qa.agent.domain.document.model.ChunkDraft;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Block;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MarkdownChunker {

    private static final int MAX_CHUNK_LENGTH = 2000;
    private final Parser parser;

    public MarkdownChunker() {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of());
        this.parser = Parser.builder(options).build();
    }

    public List<ChunkDraft> chunk(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return List.of();
        }
        Node document = parser.parse(rawContent);
        List<ChunkDraft> chunks = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        StringBuilder currentContent = new StringBuilder();
        int chunkIndex = 0;

        for (Node node : document.getChildren()) {
            if (node instanceof Heading heading) {
                if (!currentContent.isEmpty()) {
                    chunks.addAll(splitByLength(chunkIndex, buildTitlePath(headingStack),
                            currentContent.toString().trim(), headingStack));
                    chunkIndex = chunks.size();
                    currentContent.setLength(0);
                }

                int level = heading.getLevel();
                while (headingStack.size() >= level) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(heading.getText().toString().trim());

            } else if (node instanceof Paragraph || node instanceof FencedCodeBlock
                    || node instanceof IndentedCodeBlock) {
                String text;
                if (node instanceof FencedCodeBlock codeBlock) {
                    text = "```\n" + codeBlock.getContentChars() + "\n```";
                } else if (node instanceof IndentedCodeBlock codeBlock) {
                    text = codeBlock.getContentChars().toString();
                } else {
                    text = node.getChars().toString();
                }
                if (!currentContent.isEmpty()) {
                    currentContent.append("\n\n");
                }
                currentContent.append(text);

            } else if (node instanceof Block block) {
                String text = block.getChars().toString().trim();
                if (!text.isEmpty()) {
                    if (!currentContent.isEmpty()) {
                        currentContent.append("\n\n");
                    }
                    currentContent.append(text);
                }
            }
        }

        if (!currentContent.isEmpty()) {
            chunks.addAll(splitByLength(chunkIndex, buildTitlePath(headingStack),
                    currentContent.toString().trim(), headingStack));
        }

        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setChunkIndex(i);
        }

        return chunks;
    }

    private String buildTitlePath(List<String> headingStack) {
        return String.join(" > ", headingStack);
    }

    private List<ChunkDraft> splitByLength(int startIndex, String titlePath,
                                           String content, List<String> moduleTags) {
        List<ChunkDraft> result = new ArrayList<>();
        if (content.length() <= MAX_CHUNK_LENGTH) {
            result.add(new ChunkDraft("", startIndex, titlePath, content,
                    new ArrayList<>(moduleTags), ""));
            return result;
        }

        String[] sections = content.split("\\n\\s*\\n");
        StringBuilder buf = new StringBuilder();
        int idx = startIndex;

        for (String section : sections) {
            section = section.trim();
            if (section.isEmpty()) {
                continue;
            }
            if (buf.length() + section.length() > MAX_CHUNK_LENGTH && !buf.isEmpty()) {
                result.add(new ChunkDraft("", idx++, titlePath, buf.toString().trim(),
                        new ArrayList<>(moduleTags), ""));
                buf.setLength(0);
            }
            if (!buf.isEmpty()) {
                buf.append("\n\n");
            }
            buf.append(section);
        }

        if (!buf.isEmpty()) {
            result.add(new ChunkDraft("", idx, titlePath, buf.toString().trim(),
                    new ArrayList<>(moduleTags), ""));
        }

        return result;
    }
}
