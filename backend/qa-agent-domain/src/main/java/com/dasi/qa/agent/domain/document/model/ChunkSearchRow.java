package com.dasi.qa.agent.domain.document.model;

import java.util.List;

public class ChunkSearchRow {

    private String chunkId;
    private String documentId;
    private String userId;
    private int chunkIndex;
    private String titlePath;
    private String content;
    private String summary;
    private List<String> moduleTags;
    private float[] embedding;
    private float vectorScore;
    private float keywordScore;

    public ChunkSearchRow() {
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getModuleTags() {
        return moduleTags;
    }

    public void setModuleTags(List<String> moduleTags) {
        this.moduleTags = moduleTags;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public float getVectorScore() {
        return vectorScore;
    }

    public void setVectorScore(float vectorScore) {
        this.vectorScore = vectorScore;
    }

    public float getKeywordScore() {
        return keywordScore;
    }

    public void setKeywordScore(float keywordScore) {
        this.keywordScore = keywordScore;
    }
}
