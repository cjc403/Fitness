package com.gym.fitness.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Token提供者
 */
@Slf4j
@Component
public class JwtTokenProvider {

    public enum TokenValidationStatus {
        VALID,
        EXPIRED,
        INVALID
    }
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.expiration}")
    private long jwtExpiration;
    
    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;
    
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * 生成Token
     */
    public String generateToken(Long userId, String username, String role, Long organizationId) {
        return generateToken(userId, username, role, organizationId, 0L);
    }

    public String generateToken(Long userId, String username, String role, Long organizationId, long sessionVersion) {
        return generateToken(userId, username, role, organizationId, sessionVersion, null);
    }

    public String generateToken(Long userId, String username, String role, Long organizationId, long sessionVersion, String sessionId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);
        
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "access")
                .claim("sessionVersion", sessionVersion)
                .claim("sid", sessionId)
                .claim("username", username)
                .claim("role", role)
                .claim("organizationId", organizationId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    /**
     * 生成刷新Token
     */
    public String generateRefreshToken(Long userId) {
        return generateRefreshToken(userId, 0L);
    }

    public String generateRefreshToken(Long userId, long sessionVersion) {
        return generateRefreshToken(userId, sessionVersion, null);
    }

    public String generateRefreshToken(Long userId, long sessionVersion, String sessionId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration);
        
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .claim("sessionVersion", sessionVersion)
                .claim("sid", sessionId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    /**
     * 从Token中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }
    
    /**
     * 从Token中获取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("username", String.class);
    }
    
    /**
     * 从Token中获取角色
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("role", String.class);
    }
    
    /**
     * 从Token中获取组织ID
     */
    public Long getOrganizationIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("organizationId", Long.class);
    }

    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return "refresh".equals(claims.get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("刷新Token验证失败: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    public long getSessionVersionFromToken(String token) {
        return sessionVersion(parseClaims(token));
    }

    public record AccessClaims(Long userId, String username, String role, Long organizationId, long sessionVersion, String sessionId) { }

    public AccessClaims readAccessClaims(String token) {
        Claims claims = parseClaims(token);
        String type = claims.get("type", String.class);
        // Legacy access tokens did not carry a type/version. Never accept a
        // refresh token as a principal, even on merely-authenticated routes.
        if (type != null && !"access".equals(type)) throw new IllegalArgumentException("Not an access token");
        Long id = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        String role = claims.get("role", String.class);
        Long organizationId = claims.get("organizationId", Long.class);
        if (id <= 0 || username == null || username.isBlank() || role == null || role.isBlank()
                || organizationId == null || organizationId <= 0) throw new IllegalArgumentException("Incomplete access claims");
        return new AccessClaims(id, username, role, organizationId, sessionVersion(claims), sessionId(claims));
    }

    public String getSessionIdFromToken(String token) { return sessionId(parseClaims(token)); }

    private String sessionId(Claims claims) {
        String id = claims.get("sid", String.class);
        if (id != null && (id.length() != 36 || !java.util.UUID.fromString(id).toString().equals(id))) {
            throw new IllegalArgumentException("Invalid session ID");
        }
        return id;
    }

    private long sessionVersion(Claims claims) {
        Long version = claims.get("sessionVersion", Long.class);
        if (version != null && version < 0) throw new IllegalArgumentException("Invalid session version");
        return version == null ? 0L : version;
    }
    
    /**
     * 验证Token是否有效
     */
    public boolean validateToken(String token) {
        return validateTokenStatus(token) == TokenValidationStatus.VALID;
    }

    /**
     * Distinguish an expired access token from a malformed/forged token so the
     * security entry point can return an accurate 401 response to the client.
     */
    public TokenValidationStatus validateTokenStatus(String token) {
        try {
            readAccessClaims(token);
            return TokenValidationStatus.VALID;
        } catch (ExpiredJwtException e) {
            log.debug("JWT access token expired: {}", e.getClass().getSimpleName());
            return TokenValidationStatus.EXPIRED;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT access token invalid: {}", e.getClass().getSimpleName());
            return TokenValidationStatus.INVALID;
        }
    }
    
    /**
     * 解析Token Claims
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
