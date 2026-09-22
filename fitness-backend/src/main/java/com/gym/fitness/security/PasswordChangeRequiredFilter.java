package com.gym.fitness.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {
    private final SysUserRepository users;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserDetailsImpl principal
                && users.findById(principal.getId()).map(user -> Boolean.TRUE.equals(user.getMustChangePassword())).orElse(false)
                && !isPasswordLifecyclePath(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Result.error(ErrorCode.PASSWORD_CHANGE_REQUIRED));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPasswordLifecyclePath(String path) {
        return path.equals("/api/v1/auth/change-password")
                || path.equals("/api/v1/auth/logout")
                || path.equals("/api/v1/auth/refresh");
    }
}
