package com.gym.fitness.module.member.repository;

import com.gym.fitness.module.member.entity.MemberEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<MemberEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MemberEntity m where m.id=:id")
    Optional<MemberEntity> findByIdForUpdate(@Param("id") Long id);
    /** 本门店会员（经营分析与报表用）。 */
    List<MemberEntity> findByVenueIdOrderByIdDesc(Long venueId);

    /** 归属某位会籍顾问的会员（顾问工作台）。 */
    List<MemberEntity> findBySalesUserIdOrderByIdDesc(Long salesUserId);
    /** 归属某位教练的会员。 */
    List<MemberEntity> findByCoachIdOrderByIdDesc(Long coachId);

    boolean existsByMemberNoIgnoreCase(String memberNo);

    boolean existsByMemberNoIgnoreCaseAndIdNot(String memberNo, Long id);

    Optional<MemberEntity> findByIdAndVenueId(Long id, Long venueId);

    Optional<MemberEntity> findByUserId(Long userId);

    Optional<MemberEntity> findByAccessToken(String accessToken);

    boolean existsByPhone(String phone);

    long countByStatus(String status);

    long countByVenueId(Long venueId);

    long countByVenueIdAndStatus(Long venueId, String status);

    long countByBlacklistFlagTrue();

    long countByVenueIdAndBlacklistFlagTrue(Long venueId);

    @Query("""
            select m from MemberEntity m
            where (:venueId is null or m.venueId = :venueId)
              and (:keyword is null or :keyword = ''
                   or lower(m.realName) like lower(concat('%', :keyword, '%'))
                   or lower(m.memberNo) like lower(concat('%', :keyword, '%'))
                   or m.phone like concat('%', :keyword, '%'))
            order by m.id desc
            """)
    Page<MemberEntity> search(@Param("venueId") Long venueId,
                              @Param("keyword") String keyword,
                              Pageable pageable);
}
