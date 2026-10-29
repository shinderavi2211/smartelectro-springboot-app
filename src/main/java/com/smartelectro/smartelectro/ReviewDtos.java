package com.smartelectro.smartelectro;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public record ReviewRequest(
            @Min(1) @Max(5) int rating,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 2000) String reviewText) {
    }

    public record ReviewView(
            Long id, String reviewerName, int rating, String title, String reviewText,
            LocalDateTime submittedAt, boolean verifiedPurchase, boolean ownedByCurrentUser) {
    }

    public record ProductReviewsView(Double averageRating, long reviewCount, List<ReviewView> reviews) {
    }
}
