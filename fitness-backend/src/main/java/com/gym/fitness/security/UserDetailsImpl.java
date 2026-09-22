package com.gym.fitness.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户详情实现
 */
@Getter
@Setter
public class UserDetailsImpl implements UserDetails {
    
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String role;
    private Long organizationId;
    private String organizationName;
    private List<String> permissions;
    private boolean mustChangePassword;
    private boolean enabled = true;
    private boolean accountNonExpired = true;
    private boolean accountNonLocked = true;
    private boolean credentialsNonExpired = true;
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (role != null && !role.isBlank()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        if (permissions != null) {
            permissions.stream()
                    .filter(permission -> permission != null && !permission.isBlank())
                    .distinct()
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }
        return authorities;
    }
    
    @Override
    public String getPassword() {
        return password;
    }
    
    @Override
    public String getUsername() {
        return username;
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
