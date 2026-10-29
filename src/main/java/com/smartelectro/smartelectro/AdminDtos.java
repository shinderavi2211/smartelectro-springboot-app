package com.smartelectro.smartelectro;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record DashboardSummary(long totalOrders, long pendingOrders, long totalCustomers, long totalProducts,
                                   long lowStockProducts, BigDecimal nonCancelledOrderValue) {
    }

    public record LowStockProduct(Long id, String name, String brand, Integer stock, String category) {
    }

    public record CustomerSummary(Long id, String displayName, String maskedEmail, long orderCount,
                                  LocalDateTime createdAt) {
    }

    public record AdminOrder(StoreDtos.OrderView order) {
    }

    public record StockUpdateRequest(@jakarta.validation.constraints.NotNull
                                     @jakarta.validation.constraints.Min(0)
                                     @jakarta.validation.constraints.Max(1000000) Integer stock) {
    }

    public record AdminDashboard(DashboardSummary summary, List<LowStockProduct> lowStockProducts,
                                 List<CustomerSummary> customers, List<AdminOrder> orders) {
    }
}
