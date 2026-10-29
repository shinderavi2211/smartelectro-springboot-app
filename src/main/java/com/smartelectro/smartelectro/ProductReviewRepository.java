package com.smartelectro.smartelectro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    List<ProductReview> findByProductIdOrderBySubmittedAtDesc(Long productId);

    Optional<ProductReview> findByProductIdAndUserId(Long productId, Long userId);

    @Query("""
            select r.product.id as productId, avg(r.rating) as averageRating, count(r.id) as reviewCount
            from ProductReview r
            where r.product.id in :productIds
            group by r.product.id
            """)
    List<RatingAggregate> summarizeByProductIds(@Param("productIds") Collection<Long> productIds);

    interface RatingAggregate {
        Long getProductId();

        Double getAverageRating();

        Long getReviewCount();
    }
}
