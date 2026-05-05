package com.dasi.qa.agent.domain.document.service.rag.retrieval;

import java.util.List;

public class RetrieveContext {

    private String queryText;
    private float[] queryVector;
    private String userId;
    private List<String> filterDocumentIds;
    private List<String> filterModuleTags;
    private String filterTitlePathPrefix;
    private int topK;

    public RetrieveContext() {
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public float[] getQueryVector() {
        return queryVector;
    }

    public void setQueryVector(float[] queryVector) {
        this.queryVector = queryVector;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getFilterDocumentIds() {
        return filterDocumentIds;
    }

    public void setFilterDocumentIds(List<String> filterDocumentIds) {
        this.filterDocumentIds = filterDocumentIds;
    }

    public List<String> getFilterModuleTags() {
        return filterModuleTags;
    }

    public void setFilterModuleTags(List<String> filterModuleTags) {
        this.filterModuleTags = filterModuleTags;
    }

    public String getFilterTitlePathPrefix() {
        return filterTitlePathPrefix;
    }

    public void setFilterTitlePathPrefix(String filterTitlePathPrefix) {
        this.filterTitlePathPrefix = filterTitlePathPrefix;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }
}
