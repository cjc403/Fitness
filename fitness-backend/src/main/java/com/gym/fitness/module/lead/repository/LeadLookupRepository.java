package com.gym.fitness.module.lead.repository;

import com.gym.fitness.module.lead.entity.SalesLeadEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Collection;

/** Scoped projections avoid loading unrelated employee/member accounts or historic orders. */
@Repository @RequiredArgsConstructor
public class LeadLookupRepository {
    private final EntityManager em;
    public boolean activeProductExists(Long productId,Long venueId) {
        return em.createQuery("select count(p.id) from MembershipProductEntity p where p.id=:product and p.venueId=:venue and p.status='ACTIVE'",Long.class)
            .setParameter("product",productId).setParameter("venue",venueId).getSingleResult()>0;
    }
    public boolean memberExists(Long venueId,String phone) {
        return em.createQuery("select count(m.id) from MemberEntity m where m.venueId=:venue and m.phone=:phone",Long.class)
            .setParameter("venue",venueId).setParameter("phone",phone).getSingleResult()>0;
    }
    public List<Object[]> ownerNames(Collection<Long> ids) {
        if(ids.isEmpty())return List.of();
        return em.createQuery("select u.id,u.realName from SysUserEntity u where u.id in :ids",Object[].class).setParameter("ids",ids).getResultList();
    }
    public List<Object[]> venueNames(Collection<Long> ids) {
        if(ids.isEmpty())return List.of();
        return em.createQuery("select v.id,v.name from VenueEntity v where v.id in :ids",Object[].class).setParameter("ids",ids).getResultList();
    }
    public List<Object[]> venues(Long venueId) {
        return em.createQuery("select v.id,v.name from VenueEntity v where (:venueId is null or v.id=:venueId) order by v.id",Object[].class)
            .setParameter("venueId",venueId).getResultList();
    }
    public List<Object[]> advisors(Long venueId) {
        return em.createQuery("select u.id,u.realName,u.venueId from SysUserEntity u where u.status='ACTIVE' and u.role.code='SALES' and (:venueId is null or u.venueId=:venueId) order by u.id",Object[].class)
            .setParameter("venueId",venueId).getResultList();
    }
    public List<Object[]> products(Long venueId) {
        return em.createQuery("select p.id,p.name,p.price,p.venueId,p.productType from MembershipProductEntity p where p.status='ACTIVE' and (:venueId is null or p.venueId=:venueId) order by p.id desc",Object[].class)
            .setParameter("venueId",venueId).getResultList();
    }
    public List<Object[]> conversionCandidates(SalesLeadEntity lead,Long ownerId) {
        return em.createQuery("""
            select o,m,p from SalesOrderEntity o,MemberEntity m,MembershipProductEntity p
            where o.memberId=m.id and o.productId=p.id
              and o.venueId=:venue and m.venueId=:venue and m.phone=:phone
              and (:owner is null or m.salesUserId=:owner)
              and m.createdAt>=:created and o.paidAt>=:created
              and o.orderType='NEW' and o.status in ('PAID','PARTIALLY_REFUNDED')
              and o.paidAmount>coalesce(o.refundedAmount,0)
              and not exists (select l.id from SalesLeadEntity l where l.convertedOrderId=o.id)
            order by o.paidAt desc,o.id desc
            """,Object[].class).setParameter("venue",lead.getVenueId()).setParameter("phone",lead.getPhone())
            .setParameter("owner",ownerId).setParameter("created",lead.getCreatedAt()).getResultList();
    }
}
