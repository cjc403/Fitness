package com.gym.fitness.security;

import com.gym.fitness.module.auth.repository.SysUserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * JWT认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "FITCHAIN_AUTH_ERROR";
    
    private final JwtTokenProvider tokenProvider;
    private final SysUserRepository users;
    private final com.gym.fitness.module.auth.repository.RevokedAuthSessionRepository revokedSessions;
    
    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String HEADER_NAME = "Authorization";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = getTokenFromRequest(request);
            
            if (StringUtils.hasText(token)) {
                var claims = tokenProvider.readAccessClaims(token);
                var account = users.findById(claims.userId()).orElse(null);
                if (account == null || !"ACTIVE".equalsIgnoreCase(account.getStatus())
                        || account.currentSessionVersion() != claims.sessionVersion()
                        || (claims.sessionId() != null && revokedSessions.existsById(claims.sessionId()))
                        || !Objects.equals(account.getUsername(), claims.username())
                        || account.getRole() == null || !Objects.equals(account.getRole().getCode(), claims.role())
                        || (!"SUPER_ADMIN".equals(claims.role()) && !Objects.equals(account.getVenueId(), claims.organizationId()))) {
                    request.setAttribute(AUTH_ERROR_ATTRIBUTE, JwtTokenProvider.TokenValidationStatus.INVALID);
                } else {

                    UserDetailsImpl userDetails = new UserDetailsImpl();
                    userDetails.setId(account.getId());
                    userDetails.setUsername(account.getUsername());
                    userDetails.setRealName(account.getRealName());
                    userDetails.setRole(account.getRole().getCode());
                    userDetails.setPermissions(RolePermissionCatalog.permissionsFor(account.getRole().getCode()));
                    userDetails.setOrganizationId(claims.organizationId());
                    userDetails.setSessionVersion(account.currentSessionVersion());
                    userDetails.setSessionId(claims.sessionId());
                    userDetails.setMustChangePassword(Boolean.TRUE.equals(account.getMustChangePassword()));

                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (ExpiredJwtException e) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, JwtTokenProvider.TokenValidationStatus.EXPIRED);
        } catch (Exception e) {
            // Do not log the supplied credential, JWT claims or parser source.
            log.debug("Request authentication rejected: {}", e.getClass().getSimpleName());
            SecurityContextHolder.clearContext();
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, JwtTokenProvider.TokenValidationStatus.INVALID);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(HEADER_NAME);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(TOKEN_PREFIX)) {
            return bearerToken.substring(TOKEN_PREFIX.length());
        }
        return null;
    }
}
