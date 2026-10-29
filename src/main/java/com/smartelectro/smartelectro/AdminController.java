package com.smartelectro.smartelectro;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.smartelectro.smartelectro.AdminDtos.*;
import static com.smartelectro.smartelectro.CouponDtos.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final CouponService couponService;

    public AdminController(AdminService adminService, CouponService couponService) {
        this.adminService = adminService;
        this.couponService = couponService;
    }

    @GetMapping("/summary")
    public DashboardSummary summary() {
        return adminService.summary();
    }

    @GetMapping("/orders")
    public List<AdminOrder> orders() {
        return adminService.orders();
    }

    @GetMapping("/customers")
    public List<CustomerSummary> customers() {
        return adminService.customers();
    }

    @GetMapping("/products")
    public List<Product> products(@RequestParam(required = false) String query) {
        return adminService.products(query);
    }

    @GetMapping("/products/{id}")
    public Product product(@PathVariable Long id) {
        return adminService.product(id);
    }

    @PatchMapping("/products/{id}/active")
    public Product setProductActive(@PathVariable Long id, @Valid @RequestBody ActiveProductRequest request) {
        return adminService.setProductActive(id, request.active());
    }

    @GetMapping("/inventory/low-stock")
    public List<LowStockProduct> lowStockProducts() {
        return adminService.lowStockProducts();
    }

    @PatchMapping("/products/{id}/stock")
    public LowStockProduct updateStock(@PathVariable Long id, @Valid @RequestBody StockUpdateRequest request) {
        return adminService.updateStock(id, request.stock());
    }

    @GetMapping("/coupons")
    public List<CouponAdminView> coupons() {
        return couponService.adminCoupons();
    }

    @org.springframework.web.bind.annotation.PostMapping("/coupons")
    public CouponAdminView createCoupon(@Valid @RequestBody CouponAdminRequest request) {
        return couponService.create(request);
    }

    @org.springframework.web.bind.annotation.PutMapping("/coupons/{id}")
    public CouponAdminView updateCoupon(@PathVariable Long id, @Valid @RequestBody CouponAdminRequest request) {
        return couponService.update(id, request);
    }

    @PatchMapping("/coupons/{id}/active")
    public CouponAdminView setCouponActive(@PathVariable Long id, @Valid @RequestBody ActiveCouponRequest request) {
        return couponService.setActive(id, request.active());
    }

    @GetMapping("/coupons/{id}/usage")
    public List<CouponUsageView> couponUsage(@PathVariable Long id) {
        return couponService.usage(id);
    }

    public record ActiveCouponRequest(@jakarta.validation.constraints.NotNull Boolean active) {
    }

    public record ActiveProductRequest(@jakarta.validation.constraints.NotNull Boolean active) {
    }
}
