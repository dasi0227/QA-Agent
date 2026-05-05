package com.dasi.qa.agent.domain.util;

public interface IJwtUtil {

    String generateAccessToken(String userId);

    String generateRefreshToken(String userId);

    String parseUserId(String token);

    boolean isAccessTokenValid(String token);

    boolean isRefreshTokenValid(String token);
}
