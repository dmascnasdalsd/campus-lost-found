package com.campus.lostfound.service;

import com.campus.lostfound.entity.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * JWT 颁发与解析：subject 为用户 id，附带 role 声明。
 */
@Service
public class JwtService {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final long expirationMillis;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = Duration.ofMinutes(expirationMinutes).toMillis();
    }

    public String generate(Long userId, UserRole role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验签名与过期时间；token 非法/过期时抛出 JwtException。
     */
    public ParsedToken parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        UserRole role = UserRole.valueOf(claims.get(ROLE_CLAIM, String.class));
        Instant expiresAt = claims.getExpiration().toInstant();
        return new ParsedToken(userId, role, expiresAt);
    }

    public record ParsedToken(Long userId, UserRole role, Instant expiresAt) {
    }
}
