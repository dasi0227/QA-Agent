package com.dasi.qa.agent.infrastructure.util;

import com.dasi.qa.agent.domain.util.IJwtUtil;
import com.dasi.qa.agent.infrastructure.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtUtil implements IJwtUtil {

    private static final String TOKEN_TYPE = "tokenType";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";
    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        String secret = jwtProperties.getSecret();
        if (secret == null) {
            throw new IllegalStateException("qa-agent.jwt.secret is required");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public String generateAccessToken(String userId) {
        return buildToken(userId, ACCESS, Duration.ofMinutes(jwtProperties.getAccessTokenTtlMinutes()));
    }

    @Override
    public String generateRefreshToken(String userId) {
        return buildToken(userId, REFRESH, Duration.ofDays(jwtProperties.getRefreshTokenTtlDays()));
    }

    @Override
    public String parseUserId(String token) {
        return parseClaims(token).getSubject();
    }

    @Override
    public boolean isAccessTokenValid(String token) {
        return isTokenValid(token, ACCESS);
    }

    @Override
    public boolean isRefreshTokenValid(String token) {
        return isTokenValid(token, REFRESH);
    }

    private String buildToken(String userId, String tokenType, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(jwtProperties.getIssuer())
            .subject(userId)
            .claim(TOKEN_TYPE, tokenType)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(secretKey)
            .compact();
    }

    private boolean isTokenValid(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            Object tokenType = claims.get(TOKEN_TYPE);
            return expectedType.equals(tokenType) && claims.getExpiration().after(new Date());
        } catch (Exception ex) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
