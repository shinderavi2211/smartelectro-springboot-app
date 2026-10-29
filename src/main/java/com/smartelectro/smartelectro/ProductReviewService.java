package com.smartelectro.smartelectro;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.smartelectro.smartelectro.ReviewDtos.*;

@Service
public class ProductReviewService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;
    private final CustomerOrderRepository orderRepository;

    public ProductReviewService(UserRepository userRepository, ProductRepository productRepository,
                                ProductReviewRepository reviewRepository, CustomerOrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public ProductReviewsView getReviews(Long productId, String email) {
        if (!productRepository.existsById(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }
        User currentUser = email == null ? null : userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue."));
        List<ProductReview> reviews = reviewRepository.findByProductIdOrderBySubmittedAtDesc(productId);
        Set<Long> verifiedBuyerIds = orderRepository.findVerifiedBuyerIdsForProduct(productId).stream()
                .collect(Collectors.toSet());
        List<ReviewView> reviewViews = reviews.stream().map(review -> new ReviewView(
                review.getId(), review.getUser().getDisplayName(), review.getRating(), review.getTitle(),
                review.getReviewText(), review.getSubmittedAt(), verifiedBuyerIds.contains(review.getUser().getId()),
                currentUser != null && currentUser.getId().equals(review.getUser().getId()))).toList();
        Double average = reviews.isEmpty() ? null
                : Math.round(reviews.stream().mapToInt(ProductReview::getRating).average().orElseThrow() * 100.0) / 100.0;
        return new ProductReviewsView(average, reviews.size(), reviewViews);
    }

    @Transactional
    public ProductReviewsView createOrUpdate(String email, Long productId, ReviewRequest request) {
        User user = requireUserForUpdate(email);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        ProductReview review = reviewRepository.findByProductIdAndUserId(productId, user.getId())
                .orElseGet(() -> new ProductReview(user, product, request.rating(),
                        request.title().trim(), request.reviewText().trim()));
        review.setRating(request.rating());
        review.setTitle(request.title().trim());
        review.setReviewText(request.reviewText().trim());
        reviewRepository.save(review);
        return getReviews(productId, email);
    }

    @Transactional
    public ProductReviewsView update(String email, Long productId, ReviewRequest request) {
        User user = requireUserForUpdate(email);
        ProductReview review = reviewRepository.findByProductIdAndUserId(productId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Your review was not found."));
        review.setRating(request.rating());
        review.setTitle(request.title().trim());
        review.setReviewText(request.reviewText().trim());
        reviewRepository.save(review);
        return getReviews(productId, email);
    }

    @Transactional
    public ProductReviewsView delete(String email, Long productId) {
        User user = requireUserForUpdate(email);
        ProductReview review = reviewRepository.findByProductIdAndUserId(productId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Your review was not found."));
        reviewRepository.delete(review);
        return getReviews(productId, email);
    }

    private User requireUserForUpdate(String email) {
        return userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue."));
    }
}
