package com.dasi.qa.agent.infrastructure.util;

import com.dasi.qa.agent.domain.util.UserContextUtil;
import org.springframework.stereotype.Component;

@Component
public class UserContext implements UserContextUtil {

    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();

    @Override
    public void setUserId(String userId) {
        USER_ID_HOLDER.set(userId);
    }

    @Override
    public String getUserId() {
        return USER_ID_HOLDER.get();
    }

    @Override
    public void clear() {
        USER_ID_HOLDER.remove();
    }
}
