package com.smartelectro.smartelectro;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static com.smartelectro.smartelectro.ReviewDtos.ReviewRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductReviewServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductReviewRepository reviewRepository;
    @Mock
    private CustomerOrderRepository orderRepository;

    private ProductReviewService reviewService;
    private User buyer;
    private User otherBuyer;
    private Product product;

    @BeforeEach
    void setUp() {
        reviewService = new ProductReviewService(userRepository, productRepository, reviewRepository, orderRepository);
        buyer = new User("buyer", "buyer@example.com", "encoded-password", Role.USER);
        buyer.setId(41L);
        otherBuyer = new User("other", "other@example.com", "encoded-password", Role.USER);
        otherBuyer.setId(42L);
        product = new Product("Copper house wire", "Wires & Cables", 500);
        product.setId(12L);
    }

    @Test
    void submittingReviewPersistsItAndReportsRealAverageAndVerifiedPurchase() {
        ProductReview review = new ProductReview(buyer, product, 5, "Excellent quality", "The cable is sturdy and works well.");
        when(userRepository.findByEmailForUpdate(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(userRepository.findByEmail(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(productRepository.findById(12L)).thenReturn(Optional.of(product));
        when(productRepository.existsById(12L)).thenReturn(true);
        when(reviewRepository.findByProductIdAndUserId(12L, 41L)).thenReturn(Optional.empty());
        when(reviewRepository.save(org.mockito.ArgumentMatchers.any(ProductReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewRepository.findByProductIdOrderBySubmittedAtDesc(12L)).thenReturn(List.of(review));
        when(orderRepository.findVerifiedBuyerIdsForProduct(12L)).thenReturn(List.of(41L));

        var result = reviewService.createOrUpdate(buyer.getEmail(), 12L,
                new ReviewRequest(5, "Excellent quality", "The cable is sturdy and works well."));

        assertEquals(5.0, result.averageRating());
        assertEquals(1, result.reviewCount());
        assertEquals("buyer", result.reviews().getFirst().reviewerName());
        assertTrue(result.reviews().getFirst().verifiedPurchase());
        assertTrue(result.reviews().getFirst().ownedByCurrentUser());
        assertTrue(result.reviews().getFirst().submittedAt() != null);
    }

    @Test
    void postingAgainUpdatesTheExistingCustomerReviewInsteadOfDuplicatingIt() {
        ProductReview existing = new ProductReview(buyer, product, 2, "Old title", "Old review text.");
        when(userRepository.findByEmailForUpdate(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(userRepository.findByEmail(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(productRepository.findById(12L)).thenReturn(Optional.of(product));
        when(productRepository.existsById(12L)).thenReturn(true);
        when(reviewRepository.findByProductIdAndUserId(12L, 41L)).thenReturn(Optional.of(existing));
        when(reviewRepository.save(existing)).thenReturn(existing);
        when(reviewRepository.findByProductIdOrderBySubmittedAtDesc(12L)).thenReturn(List.of(existing));
        when(orderRepository.findVerifiedBuyerIdsForProduct(12L)).thenReturn(List.of());

        var result = reviewService.createOrUpdate(buyer.getEmail(), 12L,
                new ReviewRequest(4, "Updated title", "Updated review text."));

        assertEquals(1, result.reviewCount());
        assertEquals("Updated title", existing.getTitle());
        assertEquals("Updated review text.", existing.getReviewText());
        assertFalse(result.reviews().getFirst().verifiedPurchase());
        verify(reviewRepository).save(existing);
    }

    @Test
    void reviewAverageIsCalculatedFromSavedReviewsAndOwnerIsIdentified() {
        ProductReview ownReview = new ProductReview(buyer, product, 5, "Great", "A useful product.");
        ProductReview anotherReview = new ProductReview(otherBuyer, product, 3, "Average", "It works as expected.");
        when(productRepository.existsById(12L)).thenReturn(true);
        when(userRepository.findByEmail(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(reviewRepository.findByProductIdOrderBySubmittedAtDesc(12L))
                .thenReturn(List.of(ownReview, anotherReview));
        when(orderRepository.findVerifiedBuyerIdsForProduct(12L)).thenReturn(List.of());

        var result = reviewService.getReviews(12L, buyer.getEmail());

        assertEquals(4.0, result.averageRating());
        assertEquals(2, result.reviewCount());
        assertTrue(result.reviews().getFirst().ownedByCurrentUser());
        assertFalse(result.reviews().getLast().ownedByCurrentUser());
    }

    @Test
    void customerCannotEditOrDeleteAnotherCustomersReview() {
        when(userRepository.findByEmailForUpdate(otherBuyer.getEmail())).thenReturn(Optional.of(otherBuyer));
        when(reviewRepository.findByProductIdAndUserId(12L, 42L)).thenReturn(Optional.empty());

        var updateError = assertThrows(ResponseStatusException.class,
                () -> reviewService.update(otherBuyer.getEmail(), 12L,
                        new ReviewRequest(4, "Not mine", "I cannot change this review.")));
        var deleteError = assertThrows(ResponseStatusException.class,
                () -> reviewService.delete(otherBuyer.getEmail(), 12L));

        assertEquals(404, updateError.getStatusCode().value());
        assertEquals(404, deleteError.getStatusCode().value());
        verify(reviewRepository, never()).delete(org.mockito.ArgumentMatchers.any(ProductReview.class));
    }

    @Test
    void customerCanDeleteTheirOwnReviewAndTheAverageReturnsToEmpty() {
        ProductReview ownReview = new ProductReview(buyer, product, 4, "Good", "A useful product.");
        when(userRepository.findByEmailForUpdate(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(userRepository.findByEmail(buyer.getEmail())).thenReturn(Optional.of(buyer));
        when(reviewRepository.findByProductIdAndUserId(12L, 41L)).thenReturn(Optional.of(ownReview));
        when(productRepository.existsById(12L)).thenReturn(true);
        when(reviewRepository.findByProductIdOrderBySubmittedAtDesc(12L)).thenReturn(List.of());
        when(orderRepository.findVerifiedBuyerIdsForProduct(12L)).thenReturn(List.of());

        var result = reviewService.delete(buyer.getEmail(), 12L);

        assertEquals(0, result.reviewCount());
        assertNull(result.averageRating());
        assertTrue(result.reviews().isEmpty());
        verify(reviewRepository).delete(ownReview);
    }

    @Test
    void reviewsForUnknownProductsReturnNotFound() {
        when(productRepository.existsById(999L)).thenReturn(false);

        var error = assertThrows(ResponseStatusException.class, () -> reviewService.getReviews(999L, null));

        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void reviewRequestValidatesRatingTitleAndTextLimits() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertFalse(validator.validate(new ReviewRequest(0, "A title", "A review.")).isEmpty());
            assertFalse(validator.validate(new ReviewRequest(6, "A title", "A review.")).isEmpty());
            assertFalse(validator.validate(new ReviewRequest(5, " ", "A review.")).isEmpty());
            assertFalse(validator.validate(new ReviewRequest(5, "A title", " ")).isEmpty());
            assertFalse(validator.validate(new ReviewRequest(5, "x".repeat(121), "A review.")).isEmpty());
            assertFalse(validator.validate(new ReviewRequest(5, "A title", "x".repeat(2001))).isEmpty());
            assertTrue(validator.validate(new ReviewRequest(5, "Good product", "Works as expected.")).isEmpty());
        }
    }
}
