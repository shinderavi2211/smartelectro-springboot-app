package com.smartelectro.smartelectro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    List<WishlistItem> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<WishlistItem> findByUserIdAndProductId(Long userId, Long productId);

    long deleteByUserIdAndProductId(Long userId, Long productId);
}
