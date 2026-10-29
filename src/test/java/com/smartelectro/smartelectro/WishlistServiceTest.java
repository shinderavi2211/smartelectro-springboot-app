package com.smartelectro.smartelectro;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private WishlistItemRepository wishlistRepository;
    @Mock
    private StoreService storeService;

    private WishlistService wishlistService;
    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        wishlistService = new WishlistService(userRepository, productRepository, wishlistRepository, storeService);
        user = new User("buyer", "buyer@example.com", "encoded-password", Role.USER);
        user.setId(41L);
        product = new Product("Copper house wire", "Wires & Cables", 500);
        product.setId(12L);
        product.setBrand("Polycab");
        product.setImageUrl("/images/copper-wire.jpg");
        product.setStock(8);
    }

    @Test
    void addPersistsProductAndDuplicateAddKeepsSingleWishlistEntry() {
        WishlistItem[] stored = new WishlistItem[1];
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepository.findById(12L)).thenReturn(Optional.of(product));
        when(wishlistRepository.findByUserIdAndProductId(41L, 12L))
                .thenAnswer(invocation -> Optional.ofNullable(stored[0]));
        when(wishlistRepository.save(any(WishlistItem.class))).thenAnswer(invocation -> {
            stored[0] = invocation.getArgument(0);
            return stored[0];
        });
        when(wishlistRepository.findByUserIdOrderByCreatedAtDesc(41L))
                .thenAnswer(invocation -> stored[0] == null ? List.of() : List.of(stored[0]));

        var first = wishlistService.add(user.getEmail(), 12L);
        var duplicate = wishlistService.add(user.getEmail(), 12L);

        assertEquals(1, first.count());
        assertEquals(1, duplicate.count());
        assertEquals("Polycab", duplicate.items().getFirst().brand());
        assertEquals("/images/copper-wire.jpg", duplicate.items().getFirst().imageUrl());
        verify(wishlistRepository).save(any(WishlistItem.class));
    }

    @Test
    void wishlistReadsOnlyTheAuthenticatedUsersSavedProducts() {
        WishlistItem saved = new WishlistItem(user, product);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(wishlistRepository.findByUserIdOrderByCreatedAtDesc(41L)).thenReturn(List.of(saved));

        var wishlist = wishlistService.getWishlist(user.getEmail());

        assertEquals(1, wishlist.count());
        assertEquals(12L, wishlist.items().getFirst().productId());
        assertEquals(500, wishlist.items().getFirst().price());
        verify(wishlistRepository).findByUserIdOrderByCreatedAtDesc(41L);
    }

    @Test
    void removeDeletesOnlyTheSignedInUsersProductEntry() {
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(wishlistRepository.findByUserIdOrderByCreatedAtDesc(41L)).thenReturn(List.of());

        var wishlist = wishlistService.remove(user.getEmail(), 12L);

        assertEquals(0, wishlist.count());
        verify(wishlistRepository).deleteByUserIdAndProductId(41L, 12L);
        verify(wishlistRepository, never()).deleteByUserIdAndProductId(99L, 12L);
    }

    @Test
    void moveToCartAddsOneUnitBeforeRemovingWishlistEntry() {
        WishlistItem saved = new WishlistItem(user, product);
        AtomicReference<WishlistItem> stored = new AtomicReference<>(saved);
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(wishlistRepository.findByUserIdAndProductId(41L, 12L))
                .thenReturn(Optional.of(saved));
        when(wishlistRepository.findByUserIdOrderByCreatedAtDesc(41L))
                .thenAnswer(invocation -> stored.get() == null ? List.of() : List.of(stored.get()));
        when(wishlistRepository.deleteByUserIdAndProductId(41L, 12L)).thenAnswer(invocation -> {
            stored.set(null);
            return 1L;
        });

        var wishlist = wishlistService.moveToCart(user.getEmail(), 12L);

        assertEquals(0, wishlist.count());
        verify(storeService).addToCart(user.getEmail(), new StoreDtos.CartItemRequest(12L, 1));
        verify(wishlistRepository).deleteByUserIdAndProductId(41L, 12L);
    }

    @Test
    void missingWishlistItemCannotBeMovedToCart() {
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(wishlistRepository.findByUserIdAndProductId(41L, 12L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> wishlistService.moveToCart(user.getEmail(), 12L));

        verify(storeService, never()).addToCart(any(), any());
    }

    @Test
    void wishlistOperationsRequireAnExistingSignedInAccount() {
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.empty());

        var error = assertThrows(ResponseStatusException.class,
                () -> wishlistService.remove(user.getEmail(), 12L));

        assertEquals(401, error.getStatusCode().value());
        verify(wishlistRepository, never()).deleteByUserIdAndProductId(any(), any());
    }
}
