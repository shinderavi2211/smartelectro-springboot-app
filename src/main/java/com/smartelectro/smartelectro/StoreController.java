package com.smartelectro.smartelectro;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.smartelectro.smartelectro.StoreDtos.*;
import static com.smartelectro.smartelectro.CouponDtos.*;

@Validated
@RestController
@RequestMapping("/api")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping("/cart")
    public CartView cart(@AuthenticationPrincipal UserDetails user) {
        return storeService.getCart(user.getUsername());
    }

    @PostMapping("/cart/items")
    public CartView addToCart(@AuthenticationPrincipal UserDetails user, @Valid @RequestBody CartItemRequest request) {
        return storeService.addToCart(user.getUsername(), request);
    }

    @PutMapping("/cart/items/{id}")
    public CartView updateCartItem(@AuthenticationPrincipal UserDetails user, @PathVariable Long id,
                                   @RequestParam @Min(1) int quantity) {
        return storeService.updateCartItem(user.getUsername(), id, quantity);
    }

    @DeleteMapping("/cart/items/{id}")
    public CartView removeCartItem(@AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        return storeService.removeCartItem(user.getUsername(), id);
    }

    @GetMapping("/addresses")
    public List<AddressView> addresses(@AuthenticationPrincipal UserDetails user) {
        return storeService.getAddresses(user.getUsername());
    }

    @PostMapping("/addresses")
    public AddressView addAddress(@AuthenticationPrincipal UserDetails user, @Valid @RequestBody AddressRequest request) {
        return storeService.addAddress(user.getUsername(), request);
    }

    @DeleteMapping("/addresses/{id}")
    public void deleteAddress(@AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        storeService.deleteAddress(user.getUsername(), id);
    }

    @PostMapping("/orders")
    public OrderView placeOrder(@AuthenticationPrincipal UserDetails user, @Valid @RequestBody CheckoutRequest request) {
        return storeService.placeOrder(user.getUsername(), request);
    }

    @PostMapping("/coupons/validate")
    public CouponQuote validateCoupon(@AuthenticationPrincipal UserDetails user,
                                      @Valid @RequestBody CouponValidationRequest request) {
        return storeService.quoteCoupon(user.getUsername(), request);
    }

    @GetMapping("/coupons/offers")
    public List<OfferView> couponOffers(@AuthenticationPrincipal UserDetails user) {
        return storeService.activeCouponOffers(user.getUsername());
    }

    @GetMapping("/orders")
    public List<OrderView> orders(@AuthenticationPrincipal UserDetails user) {
        return storeService.getOrders(user.getUsername());
    }

    @GetMapping("/orders/{orderNumber}")
    public OrderView order(@AuthenticationPrincipal UserDetails user, @PathVariable String orderNumber) {
        return storeService.getOrder(user.getUsername(), orderNumber);
    }

    @PatchMapping("/orders/{id}/cancel")
    public OrderView cancelOrder(@AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        return storeService.cancelOrder(user.getUsername(), id);
    }
}
