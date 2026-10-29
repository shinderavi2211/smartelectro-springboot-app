package com.smartelectro.smartelectro;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SmartelectroApplicationTests {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private WishlistItemRepository wishlistRepository;
	@Autowired
	private ProductReviewRepository reviewRepository;
	@Autowired
	private CustomerOrderRepository orderRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private CouponRepository couponRepository;
	@Autowired
	private CouponRedemptionRepository couponRedemptionRepository;
	@Autowired
	private StoreService storeService;
	@Autowired
	private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

	@Test
	void contextLoads() {
	}

	@Test
	void wishlistRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/wishlist"))
				.andExpect(status().isForbidden());
	}

	@Test
	void reviewWritesRequireAuthentication() throws Exception {
		mockMvc.perform(post("/api/products/1/reviews")
						.contentType("application/json")
						.content("""
								{"rating":5,"title":"Good product","reviewText":"Works as expected."}
								"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void productReviewsCanBeReadWithoutSigningIn() throws Exception {
		mockMvc.perform(get("/api/products/147/reviews"))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.reviews").isArray())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.reviewCount").isNumber());
	}

	@Test
	void wishlistPageUsesTheStorefrontInsteadOfAWhitelabelError() throws Exception {
		mockMvc.perform(get("/wishlist"))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("/index.html"));
	}

	@Test
	void orderDetailsRouteLoadsStorefrontButOrderDataRemainsProtected() throws Exception {
		mockMvc.perform(get("/orders/SE123456789012"))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("/index.html"));
		mockMvc.perform(get("/api/orders/SE123456789012"))
				.andExpect(status().isForbidden());
	}

	@Test
	void wishlistReviewAndVerifiedPurchaseQueriesRunAgainstTheUpdatedSchema() {
		assertEquals(List.of(), wishlistRepository.findByUserIdOrderByCreatedAtDesc(-1L));
		assertEquals(List.of(), reviewRepository.summarizeByProductIds(List.of(-1L)));
		assertEquals(List.of(), orderRepository.findVerifiedBuyerIdsForProduct(-1L));
	}

	@Test
	@Transactional
	void wishlistApiAddsListsPersistsAcrossRequestsAndRemovesForOnlyTheSignedInCustomer() throws Exception {
		var product = productRepository.findById(147L).orElseThrow();
		String email = "wishlist-test-" + UUID.randomUUID() + "@example.test";
		User user = userRepository.saveAndFlush(new User(
				"wishlist-test", email, passwordEncoder.encode(UUID.randomUUID().toString()), Role.USER));
		MockHttpSession session = authenticatedSession(user);

		mockMvc.perform(get("/api/wishlist").session(session))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.count").value(0));

		mockMvc.perform(post("/api/wishlist/{productId}", product.getId()).session(session))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.count").value(1))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].productId").value(product.getId()));

		mockMvc.perform(post("/api/wishlist/{productId}", product.getId()).session(session))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.count").value(1));

		User anotherUser = userRepository.saveAndFlush(new User(
				"wishlist-other", "wishlist-other-" + UUID.randomUUID() + "@example.test",
				passwordEncoder.encode(UUID.randomUUID().toString()), Role.USER));
		MockHttpSession anotherSession = authenticatedSession(anotherUser);
		mockMvc.perform(get("/api/wishlist").session(anotherSession))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.count").value(0));

		mockMvc.perform(get("/api/wishlist").session(session))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.count").value(1))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].name").value(product.getName()));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.delete("/api/wishlist/{productId}", product.getId()).session(session))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.count").value(0));
	}

	@Test
	@Transactional
	void orderTrackingIsAdminOnlyAndOrdersRemainCustomerScoped() throws Exception {
		User customer = userRepository.saveAndFlush(new User(
				"tracking-customer", "tracking-customer-" + UUID.randomUUID() + "@example.test",
				passwordEncoder.encode(UUID.randomUUID().toString()), Role.USER));
		User admin = userRepository.saveAndFlush(new User(
				"tracking-admin", "tracking-admin-" + UUID.randomUUID() + "@example.test",
				passwordEncoder.encode(UUID.randomUUID().toString()), Role.ADMIN));
		String orderNumber = "SE" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
		CustomerOrder order = new CustomerOrder();
		order.setOrderNumber(orderNumber);
		order.setUser(customer);
		order.setStatus(OrderStatus.ORDER_PLACED.name());
		order.setPaymentMethod("CASH_ON_DELIVERY");
		order.setSubtotal(new java.math.BigDecimal("100.00"));
		order.setDeliveryCharge(new java.math.BigDecimal("99.00"));
		order.setDiscount(java.math.BigDecimal.ZERO);
		order.setTotal(new java.math.BigDecimal("199.00"));
		order.setRecipientName("Tracking Customer");
		order.setPhone("9876543210");
		order.setAddressLine1("1 Test Street");
		order.setCity("Pune");
		order.setState("Maharashtra");
		order.setPostalCode("411001");
		order = orderRepository.saveAndFlush(order);

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.patch("/api/admin/orders/{id}/status", order.getId())
						.session(authenticatedSession(customer))
						.contentType("application/json")
						.content("{\"status\":\"CONFIRMED\"}"))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/orders/{orderNumber}", orderNumber).session(authenticatedSession(admin)))
				.andExpect(status().isNotFound());

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.patch("/api/admin/orders/{id}/status", order.getId())
						.session(authenticatedSession(admin))
						.contentType("application/json")
						.content("{\"status\":\"CONFIRMED\"}"))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("CONFIRMED"));
	}

	@Test
	@Transactional
	void adminDashboardAndApiAreRestrictedToAdmins() throws Exception {
		User customer = userRepository.saveAndFlush(new User(
				"dashboard-customer-" + UUID.randomUUID(), "dashboard-customer-" + UUID.randomUUID() + "@example.test",
				passwordEncoder.encode(UUID.randomUUID().toString()), Role.USER));
		User admin = userRepository.saveAndFlush(new User(
				"dashboard-admin-" + UUID.randomUUID(), "dashboard-admin-" + UUID.randomUUID() + "@example.test",
				passwordEncoder.encode(UUID.randomUUID().toString()), Role.ADMIN));

		mockMvc.perform(get("/admin").session(authenticatedSession(customer)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/summary").session(authenticatedSession(customer)))
				.andExpect(status().isForbidden());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/admin/coupons")
						.session(authenticatedSession(customer))
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/summary").session(authenticatedSession(admin)))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalOrders").isNumber())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.nonCancelledOrderValue").isNumber());
	}

	@Test
	@Transactional
	void couponQuoteUsesDatabasePriceAndRequiresAuthentication() throws Exception {
		var product = productRepository.findById(147L).orElseThrow();
		User customer = userRepository.saveAndFlush(new User(
				"coupon-customer-" + UUID.randomUUID(), "coupon-customer-" + UUID.randomUUID() + "@example.test",
				passwordEncoder.encode(UUID.randomUUID().toString()), Role.USER));
		Coupon coupon = new Coupon();
		coupon.setCode("TEST25");
		coupon.setDiscountType(CouponDiscountType.FIXED_AMOUNT);
		coupon.setDiscountValue(new java.math.BigDecimal("25.00"));
		coupon.setMinimumOrderValue(java.math.BigDecimal.ZERO);
		coupon.setStartsAt(java.time.LocalDateTime.now().minusMinutes(1));
		coupon.setExpiresAt(java.time.LocalDateTime.now().plusDays(1));
		coupon.setPerCustomerUsageLimit(1);
		coupon.setActive(true);
		couponRepository.saveAndFlush(coupon);
		String request = """
				{"code":"TEST25","items":[{"productId":%d,"quantity":1}]}
				""".formatted(product.getId());

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/coupons/validate")
						.contentType("application/json")
						.content(request))
				.andExpect(status().isForbidden());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/coupons/validate")
						.session(authenticatedSession(customer))
						.contentType("application/json")
						.content(request))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("TEST25"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.subtotal").value(product.getPrice()))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.discount").value(25.0));
	}

	@Test
	@Transactional
	void couponRedemptionIsStoredAndPerCustomerLimitAppliesDuringOrderPlacement() {
		var product = productRepository.findById(147L).orElseThrow();
		int initialStock = product.getStock();
		User customer = userRepository.saveAndFlush(new User(
				"redeem-customer-" + UUID.randomUUID(), "redeem-customer-" + UUID.randomUUID() + "@example.test",
				passwordEncoder.encode(UUID.randomUUID().toString()), Role.USER));
		Coupon coupon = new Coupon();
		coupon.setCode(("R" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)).toUpperCase());
		coupon.setDiscountType(CouponDiscountType.FIXED_AMOUNT);
		coupon.setDiscountValue(new java.math.BigDecimal("1.00"));
		coupon.setMinimumOrderValue(java.math.BigDecimal.ZERO);
		coupon.setStartsAt(java.time.LocalDateTime.now().minusMinutes(1));
		coupon.setExpiresAt(java.time.LocalDateTime.now().plusDays(1));
		coupon.setPerCustomerUsageLimit(1);
		coupon.setActive(true);
		coupon = couponRepository.saveAndFlush(coupon);
		var deliveryAddress = new StoreDtos.AddressRequest("Test Customer", "9876543210",
				"1 Test Street", "Test Area", "Pune", "Maharashtra", "411001",
				"Home", null, false);
		String couponCode = coupon.getCode();
		StoreDtos.CheckoutRequest firstRequest = new StoreDtos.CheckoutRequest(null, deliveryAddress,
				"CASH_ON_DELIVERY", UUID.randomUUID().toString(),
				List.of(new StoreDtos.CheckoutLineRequest(product.getId(), 1)), couponCode);

		var order = storeService.placeOrder(customer.getEmail(), firstRequest);

		assertEquals(couponCode, order.couponCode());
		assertEquals(new java.math.BigDecimal("1.00"), order.couponDiscount());
		assertEquals(1, couponRedemptionRepository.countByCouponIdAndUserId(coupon.getId(), customer.getId()));
		assertEquals(initialStock - 1, product.getStock());
		StoreDtos.CheckoutRequest repeatedRequest = new StoreDtos.CheckoutRequest(null, deliveryAddress,
				"CASH_ON_DELIVERY", UUID.randomUUID().toString(),
				List.of(new StoreDtos.CheckoutLineRequest(product.getId(), 1)), couponCode);
		var error = org.junit.jupiter.api.Assertions.assertThrows(
				org.springframework.web.server.ResponseStatusException.class,
				() -> storeService.placeOrder(customer.getEmail(), repeatedRequest));
		assertEquals(400, error.getStatusCode().value());
		assertEquals(1, couponRedemptionRepository.countByCouponIdAndUserId(coupon.getId(), customer.getId()));
		assertEquals(initialStock - 1, product.getStock());
	}

	@Test
	@Transactional
	void adminCanCreateEditActivateAndInspectCouponUsage() throws Exception {
		User admin = userRepository.saveAndFlush(new User(
				"coupon-admin-" + UUID.randomUUID(), "coupon-admin-" + UUID.randomUUID() + "@example.test",
				passwordEncoder.encode(UUID.randomUUID().toString()), Role.ADMIN));
		String couponCode = "ADMIN" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
		String startsAt = java.time.LocalDateTime.now().minusMinutes(1).toString();
		String expiresAt = java.time.LocalDateTime.now().plusDays(2).toString();
		String payload = """
				{"code":"%s","discountType":"PERCENTAGE","discountValue":10,
				"minimumOrderValue":100,"maximumDiscount":50,"startsAt":"%s","expiresAt":"%s",
				"usageLimit":20,"perCustomerUsageLimit":1,"active":true}
				""".formatted(couponCode, startsAt, expiresAt);
		var session = authenticatedSession(admin);

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/admin/coupons").session(session)
						.contentType("application/json").content(payload))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(couponCode));
		Coupon coupon = couponRepository.findByCodeIgnoreCase(couponCode).orElseThrow();

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.put("/api/admin/coupons/{id}", coupon.getId()).session(session)
						.contentType("application/json").content(payload.replace("\"active\":true", "\"active\":false")))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.active").value(false));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.patch("/api/admin/coupons/{id}/active", coupon.getId()).session(session)
						.contentType("application/json").content("{\"active\":true}"))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.active").value(true));
		mockMvc.perform(get("/api/admin/coupons/{id}/usage", coupon.getId()).session(session))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$").isArray());
	}

	private MockHttpSession authenticatedSession(User user) {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
		return session;
	}

}
