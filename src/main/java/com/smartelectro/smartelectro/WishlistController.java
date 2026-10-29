package com.smartelectro.smartelectro;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.smartelectro.smartelectro.WishlistDtos.WishlistView;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public WishlistView wishlist(@AuthenticationPrincipal UserDetails user) {
        return wishlistService.getWishlist(user.getUsername());
    }

    @PostMapping("/{productId}")
    public WishlistView add(@AuthenticationPrincipal UserDetails user, @PathVariable Long productId) {
        return wishlistService.add(user.getUsername(), productId);
    }

    @DeleteMapping("/{productId}")
    public WishlistView remove(@AuthenticationPrincipal UserDetails user, @PathVariable Long productId) {
        return wishlistService.remove(user.getUsername(), productId);
    }

    @PostMapping("/{productId}/cart")
    public WishlistView moveToCart(@AuthenticationPrincipal UserDetails user, @PathVariable Long productId) {
        return wishlistService.moveToCart(user.getUsername(), productId);
    }
}
