package com.dasi.qa.agent.domain.util;

public interface UserContext {

    void setUserId(String userId);

    String getUserId();

    void clear();
}
