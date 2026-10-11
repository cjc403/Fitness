package com.gym.fitness.module.lead.repository;
import com.gym.fitness.module.lead.entity.SalesLeadEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface SalesLeadRepository extends JpaRepository<SalesLeadEntity,Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select l from SalesLeadEntity l where l.id=:id")
    Optional<SalesLeadEntity> findByIdForUpdate(@Param("id")Long id);
    boolean existsByVenueIdAndPhone(Long venueId,String phone);
    boolean existsByConvertedOrderId(Long orderId);
    List<SalesLeadEntity> findAllByOrderByIdDesc();
    @Query("select l from SalesLeadEntity l where (:venueId is null or l.venueId=:venueId) and (:ownerId is null or l.ownerId=:ownerId) order by l.id desc")
    List<SalesLeadEntity> findScoped(@Param("venueId")Long venueId,@Param("ownerId")Long ownerId);
}
