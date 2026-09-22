package com.gym.fitness.security;

import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {

    private final SysUserRepository sysUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUserEntity user = sysUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));

        UserDetailsImpl details = new UserDetailsImpl();
        details.setId(user.getId());
        details.setUsername(user.getUsername());
        details.setPassword(user.getPasswordHash());
        details.setRealName(user.getRealName());
        details.setRole(user.getRole().getCode());
        details.setPermissions(RolePermissionCatalog.permissionsFor(user.getRole().getCode()));
        details.setOrganizationId(user.getVenueId());
        details.setOrganizationName(user.getVenue().getName());
        details.setEnabled("ACTIVE".equalsIgnoreCase(user.getStatus()));
        details.setMustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()));
        return details;
    }
}
