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

import static com.smartelectro.smartelectro.StoreDtos.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private CustomerAddressRepository addressRepository;
    @Mock
    private CustomerOrderRepository orderRepository;
    @Mock
    private CouponService couponService;

    private StoreService storeService;
    private User user;
    private Product product;
    private CustomerAddress address;

    @BeforeEach
    void setUp() {
        storeService = new StoreService(userRepository, productRepository, cartItemRepository, addressRepository, orderRepository, couponService);
        user = new User("buyer", "buyer@example.com", "encoded-password", Role.USER);
        user.setId(41L);
        product = new Product();
        product.setId(12L);
        product.setName("Copper house wire");
        product.setBrand("Polycab");
        product.setPrice(500);
        product.setStock(5);
        product.setImageUrl("https://images.example.test/wire.jpg");
        address = new CustomerAddress();
        address.setUser(user);
        address.setRecipientName("Buyer");
        address.setPhone("9876543210");
        address.setAddressLine1("1 Main Street");
        address.setCity("Pune");
        address.setState("Maharashtra");
        address.setPostalCode("411001");
    }

    @Test
    void checkoutCalculatesDeliveryReducesStockAndSnapshotsItems() {
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(addressRepository.findByIdAndUserId(8L, 41L)).thenReturn(Optional.of(address));
        CartItem cartItem = new CartItem(user, product, 2);
        when(cartItemRepository.findByUserId(41L)).thenReturn(List.of(cartItem));
        when(productRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var order = storeService.placeOrder("buyer@example.com", new CheckoutRequest(8L, "CASH_ON_DELIVERY"));

        assertEquals("ORDER_PLACED", order.status());
        assertEquals(0, order.subtotal().compareTo(new java.math.BigDecimal("1000.00")));
        assertEquals(0, order.deliveryCharge().compareTo(new java.math.BigDecimal("99.00")));
        assertEquals(0, order.total().compareTo(new java.math.BigDecimal("1099.00")));
        assertEquals("Copper house wire", order.items().getFirst().name());
        assertEquals(3, product.getStock());
        verify(cartItemRepository).delete(cartItem);
    }

    @Test
    void checkoutRejectsOutOfStockQuantityWithoutCreatingOrder() {
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(addressRepository.findByIdAndUserId(8L, 41L)).thenReturn(Optional.of(address));
        when(cartItemRepository.findByUserId(41L)).thenReturn(List.of(new CartItem(user, product, 6)));
        when(productRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(product));

        assertThrows(ResponseStatusException.class,
                () -> storeService.placeOrder("buyer@example.com", new CheckoutRequest(8L, "CASH_ON_DELIVERY")));

        assertEquals(5, product.getStock());
        verify(orderRepository, never()).save(any());
        verify(cartItemRepository, never()).deleteAllByUserId(41L);
    }

    @Test
    void cannotAddDeactivatedProductToCart() {
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepository.findByIdForUpdate(product.getId())).thenReturn(Optional.of(product));
        product.setActive(false);

        var error = assertThrows(ResponseStatusException.class,
                () -> storeService.addToCart(user.getEmail(), new CartItemRequest(product.getId(), 1)));

        assertEquals(409, error.getStatusCode().value());
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void checkoutCannotUseAnotherUsersAddress() {
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(addressRepository.findByIdAndUserId(8L, 41L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> storeService.placeOrder("buyer@example.com", new CheckoutRequest(8L, "CASH_ON_DELIVERY")));

        verify(cartItemRepository, never()).findByUserId(41L);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void orderHistoryIsScopedToSignedInUser() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(41L)).thenReturn(List.of());

        assertEquals(List.of(), storeService.getOrders(user.getEmail()));

        verify(orderRepository).findByUserIdOrderByCreatedAtDesc(41L);
    }

    @Test
    void orderDetailsCannotBeReadThroughAnotherUsersOrderNumber() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(orderRepository.findByOrderNumberAndUserId("SE-OTHER-USER", 41L)).thenReturn(Optional.empty());

        var error = assertThrows(ResponseStatusException.class,
                () -> storeService.getOrder(user.getEmail(), "SE-OTHER-USER"));

        assertEquals(404, error.getStatusCode().value());
        verify(orderRepository).findByOrderNumberAndUserId("SE-OTHER-USER", 41L);
    }

    @Test
    void adminOrderStatusCanOnlyAdvanceOneTrackingStep() {
        CustomerOrder order = new CustomerOrder();
        order.setStatus("ORDER_PLACED");
        when(orderRepository.findByIdForUpdate(81L)).thenReturn(Optional.of(order));

        var updated = storeService.updateOrderStatus(81L, OrderStatus.CONFIRMED);

        assertEquals("CONFIRMED", updated.status());
        assertEquals("CONFIRMED", order.getStatus());
    }

    @Test
    void adminOrderStatusRejectsSkippingStepsAndCancellingShippedOrders() {
        CustomerOrder order = new CustomerOrder();
        order.setStatus("SHIPPED");
        when(orderRepository.findByIdForUpdate(82L)).thenReturn(Optional.of(order));

        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> storeService.updateOrderStatus(82L, OrderStatus.DELIVERED));
        assertEquals(409, error.getStatusCode().value());
        assertEquals("SHIPPED", order.getStatus());
    }

    @Test
    void couponQuoteUsesDatabaseProductPricesAndIncludesDeliveryInTotal() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepository.findById(12L)).thenReturn(Optional.of(product));
        Coupon coupon = new Coupon();
        coupon.setCode("SAVE10");
        coupon.setDiscountType(CouponDiscountType.FIXED_AMOUNT);
        when(couponService.evaluate(41L, "SAVE10", new java.math.BigDecimal("1000.00")))
                .thenReturn(new CouponService.Evaluation(coupon, new java.math.BigDecimal("100.00")));

        var quote = storeService.quoteCoupon(user.getEmail(),
                new CouponDtos.CouponValidationRequest("SAVE10", List.of(new CheckoutLineRequest(12L, 2))));

        assertEquals(new java.math.BigDecimal("1000.00"), quote.subtotal());
        assertEquals(new java.math.BigDecimal("100.00"), quote.discount());
        assertEquals(new java.math.BigDecimal("99.00"), quote.deliveryCharge());
        assertEquals(new java.math.BigDecimal("999.00"), quote.total());
    }

    @Test
    void placingOrderPersistsRecalculatedCouponDiscountAndRedeemsOnce() {
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(addressRepository.findByIdAndUserId(8L, 41L)).thenReturn(Optional.of(address));
        when(cartItemRepository.findByUserId(41L)).thenReturn(List.of(new CartItem(user, product, 2)));
        when(productRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(product));
        Coupon coupon = new Coupon();
        coupon.setCode("SAVE10");
        coupon.setDiscountType(CouponDiscountType.FIXED_AMOUNT);
        when(couponService.evaluateForCheckout(41L, "SAVE10", new java.math.BigDecimal("1000.00")))
                .thenReturn(new CouponService.Evaluation(coupon, new java.math.BigDecimal("100.00")));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CheckoutRequest request = new CheckoutRequest(8L, null, "CASH_ON_DELIVERY", "coupon-checkout-key",
                null, "SAVE10");

        var order = storeService.placeOrder(user.getEmail(), request);

        assertEquals("SAVE10", order.couponCode());
        assertEquals(new java.math.BigDecimal("100.00"), order.couponDiscount());
        assertEquals(new java.math.BigDecimal("999.00"), order.total());
        verify(couponService, times(1)).redeem(org.mockito.ArgumentMatchers.same(coupon),
                org.mockito.ArgumentMatchers.same(user), org.mockito.ArgumentMatchers.any(CustomerOrder.class),
                org.mockito.ArgumentMatchers.eq(new java.math.BigDecimal("100.00")));
    }

    @Test
    void buyNowPlacesOnlySelectedQuantityAndPreservesOtherCartItems() {
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(addressRepository.findByIdAndUserId(8L, 41L)).thenReturn(Optional.of(address));
        CartItem selected = new CartItem(user, product, 4);
        Product otherProduct = new Product("LED bulb", "Lighting", 250);
        otherProduct.setId(13L);
        otherProduct.setStock(3);
        CartItem other = new CartItem(user, otherProduct, 1);
        when(cartItemRepository.findByUserId(41L)).thenReturn(List.of(selected, other));
        when(productRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var order = storeService.placeOrder("buyer@example.com",
                new CheckoutRequest(8L, null, "CASH_ON_DELIVERY", "buy-now-key",
                        List.of(new CheckoutLineRequest(12L, 2))));

        assertEquals(1, order.items().size());
        assertEquals(2, order.items().getFirst().quantity());
        assertEquals(2, selected.getQuantity());
        assertEquals(1, other.getQuantity());
        assertEquals(3, otherProduct.getStock());
        assertEquals(3, product.getStock());
        verify(cartItemRepository, never()).delete(selected);
        verify(cartItemRepository, never()).delete(other);
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsExistingOrderWithoutReducingStockTwice() {
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(addressRepository.findByIdAndUserId(8L, 41L)).thenReturn(Optional.of(address));
        CartItem cartItem = new CartItem(user, product, 2);
        when(cartItemRepository.findByUserId(41L)).thenReturn(List.of(cartItem));
        when(productRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(product));
        AtomicReference<CustomerOrder> savedOrder = new AtomicReference<>();
        when(orderRepository.findByIdempotencyKeyAndUserId("repeat-safe-key", 41L))
                .thenAnswer(invocation -> Optional.ofNullable(savedOrder.get()));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> {
            CustomerOrder order = invocation.getArgument(0);
            savedOrder.set(order);
            return order;
        });
        CheckoutRequest request = new CheckoutRequest(8L, null, "CASH_ON_DELIVERY", "repeat-safe-key", null);

        var first = storeService.placeOrder("buyer@example.com", request);
        var retry = storeService.placeOrder("buyer@example.com", request);

        assertEquals(first.orderNumber(), retry.orderNumber());
        assertEquals(3, product.getStock());
        verify(orderRepository).save(any(CustomerOrder.class));
        verify(cartItemRepository).delete(cartItem);
    }

    @Test
    void checkoutRejectsOnlinePaymentUntilGatewayIsConfigured() {
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));

        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> storeService.placeOrder(user.getEmail(),
                        new CheckoutRequest(8L, null, "UPI_ON_DELIVERY", null, null)));

        assertEquals(400, error.getStatusCode().value());
        verify(orderRepository, never()).save(any());
    }
}
