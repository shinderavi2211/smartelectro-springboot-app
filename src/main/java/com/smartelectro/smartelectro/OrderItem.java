package com.smartelectro.smartelectro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 180)
    private String productName;

    @Column(length = 80)
    private String brand;

    @Column(length = 1000)
    private String imageUrl;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal originalUnitPrice;

    @Column(nullable = false)
    private int quantity;

    public OrderItem() {
    }

    public OrderItem(Long productId, String productName, String brand, String imageUrl, BigDecimal unitPrice,
                     BigDecimal originalUnitPrice, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.brand = brand;
        this.imageUrl = imageUrl;
        this.unitPrice = unitPrice;
        this.originalUnitPrice = originalUnitPrice;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public CustomerOrder getOrder() { return order; }
    public void setOrder(CustomerOrder order) { this.order = order; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getBrand() { return brand; }
    public String getImageUrl() { return imageUrl; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getOriginalUnitPrice() { return originalUnitPrice; }
    public int getQuantity() { return quantity; }
}
