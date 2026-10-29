package com.smartelectro.smartelectro;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.smartelectro.smartelectro.StoreDtos.*;
import static com.smartelectro.smartelectro.CouponDtos.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StoreService {

    private static final BigDecimal FREE_DELIVERY_MINIMUM = new BigDecimal("2500.00");
    private static final BigDecimal STANDARD_DELIVERY_CHARGE = new BigDecimal("99.00");

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final CustomerAddressRepository addressRepository;
    private final CustomerOrderRepository orderRepository;
    private final CouponService couponService;

    public StoreService(UserRepository userRepository, ProductRepository productRepository,
                       CartItemRepository cartItemRepository, CustomerAddressRepository addressRepository,
                       CustomerOrderRepository orderRepository, CouponService couponService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.cartItemRepository = cartItemRepository;
        this.addressRepository = addressRepository;
        this.orderRepository = orderRepository;
        this.couponService = couponService;
    }

    @Transactional(readOnly = true)
    public CartView getCart(String email) {
        User user = requireUser(email);
        List<CartLineView> lines = cartItemRepository.findByUserId(user.getId()).stream()
                .map(this::toCartLine)
                .toList();
        return cartView(lines);
    }

    @Transactional
    public CartView addToCart(String email, CartItemRequest request) {
        User user = requireUserForUpdate(email);
        if (request.quantity() < 1 || request.quantity() > 999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a quantity between 1 and 999.");
        }
        Product product = productRepository.findByIdForUpdate(request.productId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        ensureProductActive(product);
        int stock = availableStock(product);
        CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId())
                .orElseGet(() -> new CartItem(user, product, 0));
        if (item.getQuantity() + request.quantity() > stock) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Requested quantity exceeds available stock.");
        }
        item.setQuantity(item.getQuantity() + request.quantity());
        cartItemRepository.save(item);
        return getCart(email);
    }

    @Transactional
    public CartView updateCartItem(String email, Long itemId, int quantity) {
        User user = requireUserForUpdate(email);
        CartItem item = cartItemRepository.findByIdAndUserId(itemId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found."));
        if (quantity < 1 || quantity > 999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a quantity between 1 and 999.");
        }
        Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        ensureProductActive(product);
        if (quantity > availableStock(product)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Requested quantity exceeds available stock.");
        }
        item.setQuantity(quantity);
        return getCart(email);
    }

    @Transactional
    public CartView removeCartItem(String email, Long itemId) {
        User user = requireUserForUpdate(email);
        CartItem item = cartItemRepository.findByIdAndUserId(itemId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found."));
        cartItemRepository.delete(item);
        return getCart(email);
    }

    @Transactional(readOnly = true)
    public List<AddressView> getAddresses(String email) {
        User user = requireUser(email);
        return addressRepository.findByUserIdOrderByDefaultAddressDescIdDesc(user.getId()).stream()
                .map(this::toAddressView)
                .toList();
    }

    @Transactional
    public AddressView addAddress(String email, AddressRequest request) {
        User user = requireUserForUpdate(email);
        CustomerAddress address = new CustomerAddress();
        address.setUser(user);
        applyAddress(address, request);
        address.setDeliveryInstructions(request.deliveryInstructions());
        address.setDefaultAddress(request.defaultAddress() || addressRepository.countByUserId(user.getId()) == 0);
        if (address.isDefaultAddress()) {
            addressRepository.findByUserIdOrderByDefaultAddressDescIdDesc(user.getId())
                    .forEach(existing -> existing.setDefaultAddress(false));
        }
        return toAddressView(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(String email, Long addressId) {
        User user = requireUserForUpdate(email);
        CustomerAddress address = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found."));
        boolean wasDefault = address.isDefaultAddress();
        addressRepository.delete(address);
        if (wasDefault) {
            addressRepository.findByUserIdOrderByDefaultAddressDescIdDesc(user.getId()).stream()
                    .findFirst().ifPresent(first -> first.setDefaultAddress(true));
        }
    }

    @Transactional
    public OrderView placeOrder(String email, CheckoutRequest request) {
        User user = requireUserForUpdate(email);
        if (!"CASH_ON_DELIVERY".equals(request.paymentMethod())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Online payment is not available yet. Choose Cash on Delivery.");
        }
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var existingOrder = orderRepository.findByIdempotencyKeyAndUserId(request.idempotencyKey(), user.getId());
            if (existingOrder.isPresent()) {
                return toOrderView(existingOrder.get());
            }
        }
        CustomerAddress address;
        if (request.deliveryAddress() != null) {
            address = new CustomerAddress();
            applyAddress(address, request.deliveryAddress());
            address.setDeliveryInstructions(request.deliveryAddress().deliveryInstructions());
        } else if (request.addressId() != null) {
            address = addressRepository.findByIdAndUserId(request.addressId(), user.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Select one of your saved addresses."));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid delivery address.");
        }

        List<CartItem> cartItems = cartItemRepository.findByUserId(user.getId());
        Map<Long, Integer> requestedProducts = new LinkedHashMap<>();
        if (request.items() != null && !request.items().isEmpty()) {
            request.items().forEach(line -> requestedProducts.merge(line.productId(), line.quantity(), Integer::sum));
        } else {
            cartItems.forEach(item -> requestedProducts.put(item.getProduct().getId(), item.getQuantity()));
        }
        if (requestedProducts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your cart is empty.");
        }
        if (requestedProducts.values().stream().anyMatch(quantity -> quantity < 1 || quantity > 999)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a quantity between 1 and 999.");
        }
        Map<Long, CartItem> cartByProductId = new LinkedHashMap<>();
        cartItems.forEach(item -> cartByProductId.put(item.getProduct().getId(), item));
        List<Map.Entry<Product, Integer>> productsToOrder = requestedProducts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> Map.entry(
                        productRepository.findByIdForUpdate(entry.getKey())
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                                        "A product in your order is no longer available.")),
                        entry.getValue()))
                .toList();
        productsToOrder.forEach(entry -> ensureProductActive(entry.getKey()));

        CustomerOrder order = new CustomerOrder();
        order.setUser(user);
        order.setOrderNumber("SE" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        order.setIdempotencyKey(request.idempotencyKey());
        order.setStatus(OrderStatus.ORDER_PLACED.name());
        order.setPaymentMethod(request.paymentMethod());
        order.setRecipientName(address.getRecipientName());
        order.setPhone(address.getPhone());
        order.setAddressLine1(address.getAddressLine1());
        order.setAddressLine2(address.getAddressLine2());
        order.setDeliveryInstructions(address.getDeliveryInstructions());
        order.setCity(address.getCity());
        order.setState(address.getState());
        order.setPostalCode(address.getPostalCode());

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        for (Map.Entry<Product, Integer> entry : productsToOrder) {
            Product product = entry.getKey();
            int quantity = entry.getValue();
            if (quantity > availableStock(product)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, product.getName() + " no longer has enough stock.");
            }
            BigDecimal unitPrice = money(product.getPrice());
            BigDecimal originalPrice = originalPrice(product, unitPrice);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(lineTotal);
            discount = discount.add(originalPrice.subtract(unitPrice).multiply(BigDecimal.valueOf(quantity)));
            order.addItem(new OrderItem(product.getId(), product.getName(), product.getBrand(),
                    product.getImageUrl(), unitPrice, originalPrice, quantity));
        }
        BigDecimal delivery = subtotal.compareTo(FREE_DELIVERY_MINIMUM) >= 0
                ? BigDecimal.ZERO.setScale(2) : STANDARD_DELIVERY_CHARGE;
        CouponService.Evaluation couponEvaluation = request.couponCode() == null || request.couponCode().isBlank()
                ? null : couponService.evaluateForCheckout(user.getId(), request.couponCode(), subtotal);
        BigDecimal couponDiscount = couponEvaluation == null ? BigDecimal.ZERO.setScale(2)
                : couponEvaluation.discount();
        order.setSubtotal(subtotal);
        order.setDeliveryCharge(delivery);
        order.setDiscount(discount);
        order.setCouponCode(couponEvaluation == null ? null : couponEvaluation.coupon().getCode());
        order.setCouponDiscount(couponDiscount);
        order.setTotal(subtotal.subtract(couponDiscount).add(delivery));
        CustomerOrder saved = orderRepository.save(order);
        if (couponEvaluation != null) {
            couponService.redeem(couponEvaluation.coupon(), user, saved, couponDiscount);
        }
        for (Map.Entry<Product, Integer> entry : productsToOrder) {
            Product product = entry.getKey();
            int quantity = entry.getValue();
            product.setStock(availableStock(product) - quantity);
            CartItem cartItem = cartByProductId.get(product.getId());
            if (cartItem != null) {
                int remaining = cartItem.getQuantity() - quantity;
                if (remaining > 0) {
                    cartItem.setQuantity(remaining);
                } else {
                    cartItemRepository.delete(cartItem);
                }
            }
        }
        return toOrderView(saved);
    }

    @Transactional(readOnly = true)
    public CouponQuote quoteCoupon(String email, CouponValidationRequest request) {
        User user = requireUser(email);
        Map<Long, Integer> requestedProducts = new LinkedHashMap<>();
        if (request.items() != null && !request.items().isEmpty()) {
            request.items().forEach(line -> requestedProducts.merge(line.productId(), line.quantity(), Integer::sum));
        } else {
            cartItemRepository.findByUserId(user.getId())
                    .forEach(item -> requestedProducts.put(item.getProduct().getId(), item.getQuantity()));
        }
        if (requestedProducts.isEmpty() || requestedProducts.values().stream()
                .anyMatch(quantity -> quantity < 1 || quantity > 999)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your order items are invalid.");
        }
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        for (Map.Entry<Long, Integer> entry : requestedProducts.entrySet()) {
            Product product = productRepository.findById(entry.getKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "A product in your order is no longer available."));
            ensureProductActive(product);
            if (entry.getValue() > availableStock(product)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, product.getName() + " no longer has enough stock.");
            }
            subtotal = subtotal.add(money(product.getPrice()).multiply(BigDecimal.valueOf(entry.getValue())));
        }
        CouponService.Evaluation evaluation = couponService.evaluate(user.getId(), request.code(), subtotal);
        BigDecimal delivery = subtotal.compareTo(FREE_DELIVERY_MINIMUM) >= 0
                ? BigDecimal.ZERO.setScale(2) : STANDARD_DELIVERY_CHARGE;
        return new CouponQuote(evaluation.coupon().getCode(), evaluation.coupon().getDiscountType(),
                subtotal, evaluation.discount(), delivery, subtotal.subtract(evaluation.discount()).add(delivery));
    }

    @Transactional(readOnly = true)
    public List<OfferView> activeCouponOffers(String email) {
        return couponService.activeOffers(requireUser(email).getId());
    }

    @Transactional(readOnly = true)
    public List<OrderView> getOrders(String email) {
        User user = requireUser(email);
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toOrderView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderView> getAllOrdersForAdmin() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toOrderView)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderView getOrder(String email, String orderNumber) {
        User user = requireUser(email);
        return orderRepository.findByOrderNumberAndUserId(orderNumber, user.getId())
                .map(this::toOrderView)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
    }

    @Transactional
    public OrderView cancelOrder(String email, Long orderId) {
        User user = requireUserForUpdate(email);
        CustomerOrder order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
        if (!OrderStatus.canTransition(order.getStatus(), OrderStatus.CANCELLED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only newly placed orders can be cancelled.");
        }
        for (OrderItem item : order.getItems()) {
            productRepository.findByIdForUpdate(item.getProductId()).ifPresent(product ->
                    product.setStock(availableStock(product) + item.getQuantity()));
        }
        order.setStatus(OrderStatus.CANCELLED.name());
        return toOrderView(order);
    }

    @Transactional
    public OrderView updateOrderStatus(Long orderId, OrderStatus nextStatus) {
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
        if (!OrderStatus.canTransition(order.getStatus(), nextStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order status cannot move from " + order.getStatus() + " to " + nextStatus + ".");
        }
        if (nextStatus == OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                productRepository.findByIdForUpdate(item.getProductId()).ifPresent(product ->
                        product.setStock(availableStock(product) + item.getQuantity()));
            }
        }
        order.setStatus(nextStatus.name());
        return toOrderView(order);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue."));
    }

    private User requireUserForUpdate(String email) {
        return userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue."));
    }

    private int availableStock(Product product) {
        return product.getStock() == null ? 0 : Math.max(product.getStock(), 0);
    }

    private void ensureProductActive(Product product) {
        if (!product.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    product.getName() + " is no longer available for purchase.");
        }
    }

    private BigDecimal money(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal originalPrice(Product product, BigDecimal unitPrice) {
        BigDecimal original = product.getOriginalPrice() == null ? unitPrice : money(product.getOriginalPrice());
        return original.max(unitPrice);
    }

    private CartLineView toCartLine(CartItem item) {
        Product product = item.getProduct();
        BigDecimal unitPrice = money(product.getPrice());
        BigDecimal originalPrice = originalPrice(product, unitPrice);
        BigDecimal discount = originalPrice.subtract(unitPrice).multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartLineView(item.getId(), product.getId(), product.getName(), product.getBrand(),
                product.getImageUrl(), unitPrice, originalPrice, discount, item.getQuantity(), availableStock(product),
                unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
    }

    private CartView cartView(List<CartLineView> items) {
        BigDecimal subtotal = items.stream().map(CartLineView::lineTotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal discount = items.stream().map(CartLineView::discount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal delivery = items.isEmpty() || subtotal.compareTo(FREE_DELIVERY_MINIMUM) >= 0
                ? BigDecimal.ZERO.setScale(2) : STANDARD_DELIVERY_CHARGE;
        int itemCount = items.stream().mapToInt(CartLineView::quantity).sum();
        return new CartView(items, subtotal, delivery, discount, subtotal.add(delivery), itemCount);
    }

    private void applyAddress(CustomerAddress address, AddressRequest request) {
        address.setRecipientName(request.recipientName().trim());
        address.setPhone(request.phone().trim());
        address.setAddressLine1(request.addressLine1().trim());
        address.setAddressLine2(request.addressLine2() == null ? null : request.addressLine2().trim());
        address.setCity(request.city().trim());
        address.setState(request.state().trim());
        address.setPostalCode(request.postalCode().trim());
        address.setLabel(request.label() == null || request.label().isBlank() ? "Home" : request.label().trim());
        address.setDeliveryInstructions(request.deliveryInstructions() == null ? null : request.deliveryInstructions().trim());
    }

    private AddressView toAddressView(CustomerAddress address) {
        return new AddressView(address.getId(), address.getRecipientName(), address.getPhone(),
                address.getAddressLine1(), address.getAddressLine2(), address.getCity(), address.getState(),
                address.getPostalCode(), address.getLabel(), address.isDefaultAddress(), address.getDeliveryInstructions());
    }

    private OrderView toOrderView(CustomerOrder order) {
        AddressView address = new AddressView(null, order.getRecipientName(), order.getPhone(),
                order.getAddressLine1(), order.getAddressLine2(), order.getCity(), order.getState(),
                order.getPostalCode(), "Delivery", false, order.getDeliveryInstructions());
        List<OrderLineView> items = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            items.add(new OrderLineView(item.getProductId(), item.getProductName(), item.getBrand(),
                    item.getImageUrl(), item.getUnitPrice(),
                    item.getOriginalUnitPrice() == null ? item.getUnitPrice() : item.getOriginalUnitPrice(),
                    (item.getOriginalUnitPrice() == null ? item.getUnitPrice() : item.getOriginalUnitPrice())
                            .subtract(item.getUnitPrice()).multiply(BigDecimal.valueOf(item.getQuantity())),
                    item.getQuantity(),
                    item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))));
        }
        return new OrderView(order.getId(), order.getOrderNumber(), order.getStatus(), order.getPaymentMethod(),
                order.getCreatedAt(), order.getSubtotal(), order.getDeliveryCharge(),
                order.getDiscount() == null ? BigDecimal.ZERO : order.getDiscount(), order.getTotal(), address, items,
                order.getCouponCode(), order.getCouponDiscount() == null ? BigDecimal.ZERO : order.getCouponDiscount());
    }
}
