package com.gym.fitness.config;

import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.organization.entity.RoleEntity;
import com.gym.fitness.module.organization.entity.VenueEntity;
import com.gym.fitness.module.organization.repository.RoleRepository;
import com.gym.fitness.module.organization.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("prod & !demo")
@RequiredArgsConstructor
public class ProductionAdminInitializer implements ApplicationRunner {
    private final SysUserRepository users;
    private final RoleRepository roles;
    private final VenueRepository venues;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // A normal restart is not a password-reset operation. V5's disabled bootstrap account is the one exception.
        var existing=users.findByUsername("admin");
        boolean migrationBootstrap=existing.isPresent()&&"INACTIVE".equals(existing.get().getStatus())
                &&"$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy".equals(existing.get().getPasswordHash());
        if(existing.isPresent()&&!migrationBootstrap)return;
        if (adminPassword == null || adminPassword.length() < 8) {
            throw new IllegalStateException("生产环境必须设置至少 8 位的 APP_ADMIN_PASSWORD");
        }
        RoleEntity role = roles.findByCode("SUPER_ADMIN")
                .orElseThrow(() -> new IllegalStateException("缺少 SUPER_ADMIN 角色"));
        VenueEntity venue = venues.findAllByOrderByIdAsc().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("缺少总部场馆"));
        SysUserEntity admin = users.findByUsername("admin").orElseGet(SysUserEntity::new);
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRealName("系统管理员");
        admin.setRoleId(role.getId());
        admin.setVenueId(venue.getId());
            admin.setStatus("ACTIVE");
            admin.setMustChangePassword(false);
        admin.setCoach(false);
        users.save(admin);
    }
}
