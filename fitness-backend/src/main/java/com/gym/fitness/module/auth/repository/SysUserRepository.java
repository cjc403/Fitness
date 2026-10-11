package com.gym.fitness.module.auth.repository;

import com.gym.fitness.module.auth.entity.SysUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysUserRepository extends JpaRepository<SysUserEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from SysUserEntity u where u.id=:id")
    Optional<SysUserEntity> findByIdForUpdate(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from SysUserEntity u where u.username=:username")
    Optional<SysUserEntity> findByUsernameForUpdate(@Param("username") String username);

    Optional<SysUserEntity> findByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Query("""
            select u from SysUserEntity u
            where u.role.code <> 'MEMBER'
              and (:venueId is null or u.venueId = :venueId)
              and (:roleCode is null or :roleCode = '' or u.role.code = :roleCode)
              and (:status is null or :status = '' or u.status = :status)
              and (:keyword is null or :keyword = ''
                   or lower(u.username) like lower(concat('%', :keyword, '%'))
                   or lower(u.realName) like lower(concat('%', :keyword, '%'))
                   or u.phone like concat('%', :keyword, '%'))
            order by u.id desc
            """)
    Page<SysUserEntity> searchEmployees(@Param("venueId") Long venueId,
                                        @Param("roleCode") String roleCode,
                                        @Param("status") String status,
                                        @Param("keyword") String keyword,
                                        Pageable pageable);
}
