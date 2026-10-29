package com.smartelectro.smartelectro;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static com.smartelectro.smartelectro.StoreDtos.CartItemRequest;
import static com.smartelectro.smartelectro.WishlistDtos.*;

@Service
public class WishlistService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final WishlistItemRepository wishlistRepository;
    private final StoreService storeService;

    public WishlistService(UserRepository userRepository, ProductRepository productRepository,
                           WishlistItemRepository wishlistRepository, StoreService storeService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.wishlistRepository = wishlistRepository;
        this.storeService = storeService;
    }

    @Transactional(readOnly = true)
    public WishlistView getWishlist(String email) {
        User user = requireUser(email);
        return toWishlistView(wishlistRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
    }

    @Transactional
    public WishlistView add(String email, Long productId) {
        User user = requireUserForUpdate(email);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        if (wishlistRepository.findByUserIdAndProductId(user.getId(), productId).isEmpty()) {
            wishlistRepository.save(new WishlistItem(user, product));
        }
        return getWishlist(email);
    }

    @Transactional
    public WishlistView remove(String email, Long productId) {
        User user = requireUserForUpdate(email);
        wishlistRepository.deleteByUserIdAndProductId(user.getId(), productId);
        return getWishlist(email);
    }

    @Transactional
    public WishlistView moveToCart(String email, Long productId) {
        User user = requireUserForUpdate(email);
        if (wishlistRepository.findByUserIdAndProductId(user.getId(), productId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Wishlist item not found.");
        }
        storeService.addToCart(email, new CartItemRequest(productId, 1));
        wishlistRepository.deleteByUserIdAndProductId(user.getId(), productId);
        return getWishlist(email);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue."));
    }

    private User requireUserForUpdate(String email) {
        return userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue."));
    }

    private WishlistView toWishlistView(List<WishlistItem> items) {
        List<WishlistEntryView> entries = items.stream().map(item -> {
            Product product = item.getProduct();
            return new WishlistEntryView(product.getId(), product.getName(), product.getBrand(),
                    product.getImageUrl(), product.getPrice(), product.getOriginalPrice(),
                    product.getStock(), item.getCreatedAt());
        }).toList();
        return new WishlistView(entries, entries.size());
    }
}
