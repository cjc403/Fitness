package com.gym.fitness.common.util;

import com.gym.fitness.security.UserDetailsImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全工具类
 */
public class SecurityUtil {
    
    /**
     * 获取当前登录用户
     */
    public static UserDetailsImpl getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl) {
            return (UserDetailsImpl) authentication.getPrincipal();
        }
        return null;
    }
    
    /**
     * 获取当前用户ID
     */
    public static Long getCurrentUserId() {
        UserDetailsImpl user = getCurrentUser();
        return user != null ? user.getId() : null;
    }
    
    /**
     * 获取当前用户名
     */
    public static String getCurrentUsername() {
        UserDetailsImpl user = getCurrentUser();
        return user != null ? user.getUsername() : null;
    }
    
    /**
     * 获取当前用户角色
     */
    public static String getCurrentUserRole() {
        UserDetailsImpl user = getCurrentUser();
        return user != null ? user.getRole() : null;
    }
    
    /**
     * 获取当前组织ID
     */
    public static Long getCurrentOrganizationId() {
        UserDetailsImpl user = getCurrentUser();
        return user != null ? user.getOrganizationId() : null;
    }

    public static boolean hasRole(String role) {
        return role != null && role.equals(getCurrentUserRole());
    }

    public static boolean isSuperAdmin() {
        return hasRole("SUPER_ADMIN");
    }
}
