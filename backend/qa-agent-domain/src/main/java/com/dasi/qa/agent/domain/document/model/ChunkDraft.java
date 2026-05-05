package com.dasi.qa.agent.domain.document.model;

import java.util.List;

public class ChunkDraft {

    private String chunkId;
    private int chunkIndex;
    private String titlePath;
    private String content;
    private List<String> moduleTags;
    private String summary;

    public ChunkDraft() {
    }

    public ChunkDraft(int chunkIndex, String titlePath, String content,
                      List<String> moduleTags, String summary) {
        this.chunkIndex = chunkIndex;
        this.titlePath = titlePath;
        this.content = content;
        this.moduleTags = moduleTags;
        this.summary = summary;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getTitlePath() {
        return titlePath;
    }

    public void setTitlePath(String titlePath) {
        this.titlePath = titlePath;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getModuleTags() {
        return moduleTags;
    }

    public void setModuleTags(List<String> moduleTags) {
        this.moduleTags = moduleTags;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
