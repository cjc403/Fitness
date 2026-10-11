package com.gym.fitness.module.cashshift.repository;
import com.gym.fitness.module.cashshift.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface CashShiftRepository extends JpaRepository<CashShiftEntity,Long> {
@Query("select s from CashShiftEntity s where s.venueId=:venue and s.status='OPEN'") Optional<CashShiftEntity> current(@Param("venue") Long venue);
@Query("select s from CashShiftEntity s where s.venueId=:venue and s.closedAt is not null order by s.id desc") List<CashShiftEntity> closed(@Param("venue") Long venue, org.springframework.data.domain.Pageable p);
@Query("select s from CashShiftEntity s where (:venue is null or s.venueId=:venue) and s.businessDate=:day order by s.id desc") List<CashShiftEntity> day(@Param("venue") Long venue,@Param("day") java.time.LocalDate day);
@Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select s from CashShiftEntity s where s.id=:id") Optional<CashShiftEntity> locked(@Param("id") Long id);
@Query("select s from CashShiftEntity s where (:venue is null or s.venueId=:venue) and s.businessDate=:day and (:status='ALL' or s.status=:status) order by s.id desc") org.springframework.data.domain.Page<CashShiftEntity> filtered(@Param("venue") Long venue,@Param("day") java.time.LocalDate day,@Param("status") String status,org.springframework.data.domain.Pageable p);
}
