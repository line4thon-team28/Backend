package com.pawpose.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/** 액세스 토큰 발급·검증. 토큰 subject 에 userId 를 넣습니다. 유효 기간 7일(명세 F-01). */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long validityMillis;

    public JwtProvider(JwtProperties properties) {
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET 은 32자 이상이어야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.validityMillis = Duration.ofDays(properties.accessTokenValidityDays()).toMillis();
    }

    public String createAccessToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validityMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 토큰을 검증하고 userId 를 꺼냅니다.
     * @throws ExpiredJwtException 만료된 토큰
     * @throws JwtException        위조·형식 오류
     */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }
}
