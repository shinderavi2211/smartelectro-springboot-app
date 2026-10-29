package com.smartelectro.smartelectro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
    long countByCouponId(Long couponId);
    long countByCouponIdAndUserId(Long couponId, Long userId);

    @Query("""
            select r from CouponRedemption r
            join fetch r.user
            join fetch r.order
            where r.coupon.id = :couponId
            order by r.createdAt desc
            """)
    List<CouponRedemption> findUsageByCouponId(@Param("couponId") Long couponId);
}
