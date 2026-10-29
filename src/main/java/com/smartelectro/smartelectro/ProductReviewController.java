package com.smartelectro.smartelectro;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.smartelectro.smartelectro.ReviewDtos.*;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ProductReviewController {

    private final ProductReviewService reviewService;

    public ProductReviewController(ProductReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ProductReviewsView reviews(@PathVariable Long productId,
                                      @AuthenticationPrincipal UserDetails user) {
        return reviewService.getReviews(productId, user == null ? null : user.getUsername());
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ProductReviewsView createOrUpdate(@AuthenticationPrincipal UserDetails user,
                                             @PathVariable Long productId,
                                             @Valid @RequestBody ReviewRequest request) {
        return reviewService.createOrUpdate(user.getUsername(), productId, request);
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping
    public ProductReviewsView update(@AuthenticationPrincipal UserDetails user,
                                     @PathVariable Long productId,
                                     @Valid @RequestBody ReviewRequest request) {
        return reviewService.update(user.getUsername(), productId, request);
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping
    public ProductReviewsView delete(@AuthenticationPrincipal UserDetails user,
                                     @PathVariable Long productId) {
        return reviewService.delete(user.getUsername(), productId);
    }
}
