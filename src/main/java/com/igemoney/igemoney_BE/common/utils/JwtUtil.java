package com.igemoney.igemoney_BE.common.utils;

import com.igemoney.igemoney_BE.common.exception.user.UnvalidJwtTokenException;
import com.igemoney.igemoney_BE.user.entity.Role;
import com.igemoney.igemoney_BE.user.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expirationMs;
    private static final String ROLE_KEY = "role";

    public JwtUtil(@Value("${jwt.secret}") String secretKey, @Value("${jwt.expiration}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secretKey.getBytes());
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
            .subject(user.getUserId().toString())
            .claim("nickname", user.getNickname())
            .claim(ROLE_KEY, user.getRole().name())
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact();
    }

    public String getRole(String token) {
        try {
            Object role = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get(ROLE_KEY);
            return role != null ? role.toString() : Role.ROLE_USER.name();
        } catch (Exception e) {
            throw new UnvalidJwtTokenException("유효하지 않은 토큰입니다.");
        }
    }

    public String extractJwtTokenFromHeader(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }

        return null;
    }

    public Boolean validateJwtToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parse(token);
            return true;

        } catch (SecurityException e) {
            throw new UnvalidJwtTokenException("decryption을 실패했습니다.");
        } catch (MalformedJwtException e) {
            throw new UnvalidJwtTokenException("올바르지 않은 토큰입니다.");
        } catch (ExpiredJwtException e) {
            throw new UnvalidJwtTokenException("토큰이 만료되었습니다.");
        }

    }

    public String getSubject(String token) {
        try {
            return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        } catch (Exception e) {
            throw new IllegalArgumentException("유효하지 않은 토큰입니다.", e);
        }
    }
}
