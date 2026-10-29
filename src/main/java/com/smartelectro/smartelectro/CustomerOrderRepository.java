package com.smartelectro.smartelectro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    List<CustomerOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<CustomerOrder> findAllByOrderByCreatedAtDesc();
    long countByUserId(Long userId);
    long countByStatusIn(List<String> statuses);
    Optional<CustomerOrder> findByIdAndUserId(Long id, Long userId);
    Optional<CustomerOrder> findByOrderNumberAndUserId(String orderNumber, Long userId);
    Optional<CustomerOrder> findByIdempotencyKeyAndUserId(String idempotencyKey, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o left join fetch o.items where o.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") Long id);

    @Query("select coalesce(sum(o.total), 0) from CustomerOrder o where o.status <> 'CANCELLED'")
    java.math.BigDecimal sumNonCancelledOrderTotal();

    @Query("""
            select distinct o.user.id
            from CustomerOrder o join o.items i
            where i.productId = :productId and o.status <> 'CANCELLED'
            """)
    List<Long> findVerifiedBuyerIdsForProduct(@Param("productId") Long productId);
}
