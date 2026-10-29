package com.smartelectro.smartelectro;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static com.smartelectro.smartelectro.AdminDtos.*;

@Service
public class AdminService {

    private static final int LOW_STOCK_THRESHOLD = 5;

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CustomerOrderRepository orderRepository;
    private final StoreService storeService;

    public AdminService(UserRepository userRepository, ProductRepository productRepository,
                        CustomerOrderRepository orderRepository, StoreService storeService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.storeService = storeService;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        return new DashboardSummary(orderRepository.count(),
                orderRepository.countByStatusIn(List.of("PLACED", OrderStatus.ORDER_PLACED.name())),
                userRepository.countByRole(Role.USER), productRepository.count(),
                productRepository.countByStockLessThanEqualAndActiveTrue(LOW_STOCK_THRESHOLD),
                orderRepository.sumNonCancelledOrderTotal());
    }

    @Transactional(readOnly = true)
    public List<LowStockProduct> lowStockProducts() {
        return productRepository.findByActiveTrueAndStockLessThanEqualOrderByStockAsc(LOW_STOCK_THRESHOLD).stream()
                .map(product -> new LowStockProduct(product.getId(), product.getName(), product.getBrand(),
                        product.getStock(), product.getCategory()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Product> products(String query) {
        return query == null || query.isBlank()
                ? productRepository.findAllByOrderByIdAsc()
                : productRepository.findByNameContainingIgnoreCaseOrderByIdAsc(query.trim());
    }

    @Transactional(readOnly = true)
    public Product product(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
    }

    @Transactional(readOnly = true)
    public List<CustomerSummary> customers() {
        return userRepository.findByRoleOrderByCreatedAtDesc(Role.USER).stream()
                .map(user -> new CustomerSummary(user.getId(), user.getDisplayName(), maskEmail(user.getEmail()),
                        orderRepository.countByUserId(user.getId()), user.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminOrder> orders() {
        return storeService.getAllOrdersForAdmin().stream().map(AdminOrder::new).toList();
    }

    @Transactional
    public LowStockProduct updateStock(Long productId, int stock) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        product.setStock(stock);
        return new LowStockProduct(product.getId(), product.getName(), product.getBrand(),
                product.getStock(), product.getCategory());
    }

    @Transactional
    public Product setProductActive(Long productId, boolean active) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        product.setActive(active);
        return product;
    }

    private String maskEmail(String email) {
        int separator = email.indexOf('@');
        if (separator <= 0) {
            return "***";
        }
        String localPart = email.substring(0, separator);
        String domain = email.substring(separator + 1);
        return localPart.substring(0, 1) + "***@" + domain;
    }
}
