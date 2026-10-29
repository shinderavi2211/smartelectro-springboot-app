package com.smartelectro.smartelectro;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class StoreDtos {

    private StoreDtos() {
    }

    public record CartItemRequest(@NotNull Long productId, @NotNull Integer quantity) {
    }

    public record AddressRequest(
            @NotBlank @Size(max = 80) String recipientName,
            @NotBlank @Pattern(regexp = "(?:\\+91[- ]?)?[6-9]\\d{9}") String phone,
            @NotBlank @Size(max = 180) String addressLine1,
            @NotBlank @Size(max = 180) String addressLine2,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String state,
            @NotBlank @Pattern(regexp = "[1-9]\\d{5}") String postalCode,
            @Size(max = 20) String label,
            @Size(max = 500) String deliveryInstructions,
            boolean defaultAddress) {
    }

    public record CheckoutRequest(
            Long addressId,
            @jakarta.validation.Valid AddressRequest deliveryAddress,
            @NotBlank @Pattern(regexp = "CASH_ON_DELIVERY") String paymentMethod,
            @Size(max = 36) String idempotencyKey,
            List<@jakarta.validation.constraints.NotNull @jakarta.validation.Valid CheckoutLineRequest> items,
            @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{2,39}") String couponCode) {
        public CheckoutRequest(Long addressId, String paymentMethod) {
            this(addressId, null, paymentMethod, null, null, null);
        }

        public CheckoutRequest(Long addressId, AddressRequest deliveryAddress, String paymentMethod,
                               String idempotencyKey, List<CheckoutLineRequest> items) {
            this(addressId, deliveryAddress, paymentMethod, idempotencyKey, items, null);
        }
    }

    public record CheckoutLineRequest(@NotNull Long productId, @NotNull @jakarta.validation.constraints.Min(1) Integer quantity) {
    }

    public record AddressView(
            Long id, String recipientName, String phone, String addressLine1, String addressLine2,
            String city, String state, String postalCode, String label, boolean defaultAddress,
            String deliveryInstructions) {
    }

    public record CartLineView(
            Long id, Long productId, String name, String brand, String imageUrl,
            BigDecimal unitPrice, BigDecimal originalUnitPrice, BigDecimal discount,
            int quantity, int stock, BigDecimal lineTotal) {
    }

    public record CartView(List<CartLineView> items, BigDecimal subtotal, BigDecimal deliveryCharge,
                           BigDecimal discount, BigDecimal total, int itemCount) {
    }

    public record OrderLineView(
            Long productId, String name, String brand, String imageUrl,
            BigDecimal unitPrice, BigDecimal originalUnitPrice, BigDecimal discount,
            int quantity, BigDecimal lineTotal) {
    }

    public record OrderView(
            Long id, String orderNumber, String status, String paymentMethod, LocalDateTime createdAt,
            BigDecimal subtotal, BigDecimal deliveryCharge, BigDecimal discount, BigDecimal total,
            AddressView address, List<OrderLineView> items, String couponCode, BigDecimal couponDiscount) {
    }
}
