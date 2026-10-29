package com.smartelectro.smartelectro;

import java.time.LocalDateTime;
import java.util.List;

public final class WishlistDtos {

    private WishlistDtos() {
    }

    public record WishlistEntryView(
            Long productId, String name, String brand, String imageUrl, double price,
            Double originalPrice, Integer stock, LocalDateTime addedAt) {
    }

    public record WishlistView(List<WishlistEntryView> items, int count) {
    }
}
