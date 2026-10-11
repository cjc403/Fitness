package com.gym.fitness.module.cashshift.repository;
import com.gym.fitness.module.cashshift.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface CashShiftChannelRepository extends JpaRepository<CashShiftChannelEntity,Long> {
List<CashShiftChannelEntity> findByShiftIdOrderByIdAsc(Long id);
@Query("select c from CashShiftChannelEntity c where c.shiftId in :ids") List<CashShiftChannelEntity> forShifts(@Param("ids") List<Long> ids);
}
