package com.smartelectro.smartelectro;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponRedemptionRepository redemptionRepository;

    private CouponService couponService;

    @BeforeEach
    void setUp() {
        couponService = new CouponService(couponRepository, redemptionRepository);
    }

    @Test
    void percentageCouponAppliesMaximumDiscountAndRoundsToCurrency() {
        Coupon coupon = coupon(CouponDiscountType.PERCENTAGE, "12.5", "200", true);
        coupon.setMaximumDiscount(new BigDecimal("50.00"));
        when(couponRepository.findByCodeIgnoreCase("SAVE")).thenReturn(Optional.of(coupon));
        when(redemptionRepository.countByCouponIdAndUserId(null, 4L)).thenReturn(0L);

        var result = couponService.evaluate(4L, " save ", new BigDecimal("1000.00"));

        assertEquals(new BigDecimal("50.00"), result.discount());
    }

    @Test
    void fixedCouponCannotDiscountMoreThanTheEligibleSubtotal() {
        Coupon coupon = coupon(CouponDiscountType.FIXED_AMOUNT, "500", "0", true);
        when(couponRepository.findByCodeIgnoreCase("LESS")).thenReturn(Optional.of(coupon));
        when(redemptionRepository.countByCouponIdAndUserId(null, 4L)).thenReturn(0L);

        var result = couponService.evaluate(4L, "LESS", new BigDecimal("100.00"));

        assertEquals(new BigDecimal("100.00"), result.discount());
    }

    @Test
    void inactiveCouponIsRejected() {
        Coupon coupon = coupon(CouponDiscountType.FIXED_AMOUNT, "10", "0", false);
        when(couponRepository.findByCodeIgnoreCase("OFF")).thenReturn(Optional.of(coupon));

        var error = assertThrows(ResponseStatusException.class,
                () -> couponService.evaluate(4L, "OFF", new BigDecimal("100.00")));

        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void perCustomerUsageLimitIsEnforced() {
        Coupon coupon = coupon(CouponDiscountType.FIXED_AMOUNT, "10", "0", true);
        coupon.setId(8L);
        when(couponRepository.findByCodeIgnoreCase("ONE")).thenReturn(Optional.of(coupon));
        when(redemptionRepository.countByCouponIdAndUserId(8L, 4L)).thenReturn(1L);

        var error = assertThrows(ResponseStatusException.class,
                () -> couponService.evaluate(4L, "ONE", new BigDecimal("100.00")));

        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void globalUsageLimitIsEnforced() {
        Coupon coupon = coupon(CouponDiscountType.FIXED_AMOUNT, "10", "0", true);
        coupon.setId(9L);
        coupon.setUsageLimit(3);
        coupon.setPerCustomerUsageLimit(5);
        when(couponRepository.findByCodeIgnoreCase("LIMIT")).thenReturn(Optional.of(coupon));
        when(redemptionRepository.countByCouponId(9L)).thenReturn(3L);

        var error = assertThrows(ResponseStatusException.class,
                () -> couponService.evaluate(4L, "LIMIT", new BigDecimal("100.00")));

        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void belowMinimumOrderValueIsRejected() {
        Coupon coupon = coupon(CouponDiscountType.FIXED_AMOUNT, "10", "200", true);
        when(couponRepository.findByCodeIgnoreCase("MINIMUM")).thenReturn(Optional.of(coupon));

        var error = assertThrows(ResponseStatusException.class,
                () -> couponService.evaluate(4L, "MINIMUM", new BigDecimal("199.99")));

        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void expiredCouponIsRejected() {
        Coupon coupon = coupon(CouponDiscountType.FIXED_AMOUNT, "10", "0", true);
        coupon.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(couponRepository.findByCodeIgnoreCase("OLD")).thenReturn(Optional.of(coupon));

        var error = assertThrows(ResponseStatusException.class,
                () -> couponService.evaluate(4L, "OLD", new BigDecimal("100.00")));

        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void couponThatHasNotStartedIsRejected() {
        Coupon coupon = coupon(CouponDiscountType.FIXED_AMOUNT, "10", "0", true);
        coupon.setStartsAt(LocalDateTime.now().plusDays(1));
        when(couponRepository.findByCodeIgnoreCase("LATER")).thenReturn(Optional.of(coupon));

        var error = assertThrows(ResponseStatusException.class,
                () -> couponService.evaluate(4L, "LATER", new BigDecimal("100.00")));

        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void checkoutRevalidatesCouponUnderTheRepositoryWriteLock() {
        Coupon coupon = coupon(CouponDiscountType.FIXED_AMOUNT, "10", "0", true);
        coupon.setId(11L);
        when(couponRepository.findByCodeForUpdate("LOCKED")).thenReturn(Optional.of(coupon));
        when(redemptionRepository.countByCouponIdAndUserId(11L, 4L)).thenReturn(0L);

        couponService.evaluateForCheckout(4L, "LOCKED", new BigDecimal("100.00"));

        verify(couponRepository).findByCodeForUpdate("LOCKED");
    }

    private Coupon coupon(CouponDiscountType type, String amount, String minimum, boolean active) {
        Coupon coupon = new Coupon();
        coupon.setCode("TEST");
        coupon.setDiscountType(type);
        coupon.setDiscountValue(new BigDecimal(amount));
        coupon.setMinimumOrderValue(new BigDecimal(minimum));
        coupon.setStartsAt(LocalDateTime.now().minusDays(1));
        coupon.setExpiresAt(LocalDateTime.now().plusDays(1));
        coupon.setPerCustomerUsageLimit(1);
        coupon.setActive(active);
        return coupon;
    }
}
