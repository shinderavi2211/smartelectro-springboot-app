package com.smartelectro.smartelectro;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.smartelectro.smartelectro.StoreDtos.CheckoutLineRequest;

public final class CouponDtos {

    private CouponDtos() {
    }

    public record CouponValidationRequest(
            @NotBlank @Size(max = 40) String code,
            java.util.List<@NotNull @jakarta.validation.Valid CheckoutLineRequest> items) {
    }

    public record CouponQuote(String code, CouponDiscountType discountType, BigDecimal subtotal,
                              BigDecimal discount, BigDecimal deliveryCharge, BigDecimal total) {
    }

    public record OfferView(String code, CouponDiscountType discountType, BigDecimal discountValue,
                            BigDecimal minimumOrderValue, BigDecimal maximumDiscount,
                            LocalDateTime expiresAt) {
    }

    public record CouponAdminRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{2,39}") String code,
            @NotNull CouponDiscountType discountType,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal discountValue,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal minimumOrderValue,
            @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal maximumDiscount,
            @NotNull LocalDateTime startsAt,
            @NotNull LocalDateTime expiresAt,
            @Min(1) Integer usageLimit,
            @NotNull @Min(1) @Max(1000000) Integer perCustomerUsageLimit,
            @NotNull Boolean active) {
    }

    public record CouponAdminView(Long id, String code, CouponDiscountType discountType,
                                  BigDecimal discountValue, BigDecimal minimumOrderValue,
                                  BigDecimal maximumDiscount, LocalDateTime startsAt, LocalDateTime expiresAt,
                                  Integer usageLimit, int perCustomerUsageLimit, boolean active,
                                  long totalRedemptions) {
    }

    public record CouponUsageView(String maskedEmail, String orderNumber, BigDecimal discountAmount,
                                  LocalDateTime createdAt) {
    }
}
