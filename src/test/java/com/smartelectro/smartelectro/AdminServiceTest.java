package com.smartelectro.smartelectro;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CustomerOrderRepository orderRepository;
    @Mock
    private StoreService storeService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userRepository, productRepository, orderRepository, storeService);
    }

    @Test
    void summaryCountsPendingOrdersAndActiveLowStockProducts() {
        when(orderRepository.count()).thenReturn(12L);
        when(orderRepository.countByStatusIn(List.of("PLACED", "ORDER_PLACED"))).thenReturn(3L);
        when(userRepository.countByRole(Role.USER)).thenReturn(8L);
        when(productRepository.count()).thenReturn(24L);
        when(productRepository.countByStockLessThanEqualAndActiveTrue(5)).thenReturn(2L);
        when(orderRepository.sumNonCancelledOrderTotal()).thenReturn(new BigDecimal("1250.00"));

        var summary = adminService.summary();

        assertEquals(12L, summary.totalOrders());
        assertEquals(3L, summary.pendingOrders());
        assertEquals(8L, summary.totalCustomers());
        assertEquals(24L, summary.totalProducts());
        assertEquals(2L, summary.lowStockProducts());
        assertEquals(new BigDecimal("1250.00"), summary.nonCancelledOrderValue());
    }

    @Test
    void adminCanReactivateProductWithoutDeletingItsRecord() {
        Product product = new Product("Wire", "Wires & Cables", 100);
        product.setActive(false);
        when(productRepository.findByIdForUpdate(45L)).thenReturn(Optional.of(product));

        Product reactivated = adminService.setProductActive(45L, true);

        assertEquals(true, reactivated.isActive());
        verify(productRepository).findByIdForUpdate(45L);
    }
}
