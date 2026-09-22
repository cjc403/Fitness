package com.gym.fitness.security;

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

/**
 * JWT认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "FITCHAIN_AUTH_ERROR";
    
    private final JwtTokenProvider tokenProvider;
    
    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String HEADER_NAME = "Authorization";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = getTokenFromRequest(request);
            
            if (StringUtils.hasText(token)) {
                JwtTokenProvider.TokenValidationStatus status = tokenProvider.validateTokenStatus(token);
                if (status != JwtTokenProvider.TokenValidationStatus.VALID) {
                    request.setAttribute(AUTH_ERROR_ATTRIBUTE, status);
                } else {
                    Long userId = tokenProvider.getUserIdFromToken(token);
                    String username = tokenProvider.getUsernameFromToken(token);
                    String role = tokenProvider.getRoleFromToken(token);
                    Long organizationId = tokenProvider.getOrganizationIdFromToken(token);

                    UserDetailsImpl userDetails = new UserDetailsImpl();
                    userDetails.setId(userId);
                    userDetails.setUsername(username);
                    userDetails.setRole(role);
                    userDetails.setPermissions(RolePermissionCatalog.permissionsFor(role));
                    userDetails.setOrganizationId(organizationId);

                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.error("无法设置用户认证", e);
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
