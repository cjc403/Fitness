package com.gym.fitness.module.auth.entity;

import com.gym.fitness.module.organization.entity.RoleEntity;
import com.gym.fitness.module.organization.entity.VenueEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user")
@Getter
@Setter
@NoArgsConstructor
public class SysUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "real_name", length = 50)
    private String realName;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private RoleEntity role;

    @Column(name = "venue_id", nullable = false)
    private Long venueId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "venue_id", insertable = false, updatable = false)
    private VenueEntity venue;

    @Column(name = "is_coach")
    private Boolean coach;

    @Column(length = 20)
    private String status;

    // Keep the local H2 column nullable so Hibernate can upgrade databases created
    // by older project versions without failing while copying existing rows.
    // PostgreSQL enforces NOT NULL through Flyway V6.
    @Column(name = "must_change_password")
    private Boolean mustChangePassword = false;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
