
package com.smartelectro.smartelectro;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final List<String> ELECTRICAL_CATEGORIES = List.of(
            "Wires & Cables",
            "Switches & Sockets",
            "Protection & Distribution",
            "Conduits & Trunking",
            "Connectors & Terminals",
            "Wiring Accessories",
            "Lighting",
            "Fans",
            "Accessories"
    );

    private final ProductRepository productRepository;
    private final ProductReviewRepository reviewRepository;

    public ProductController(ProductRepository productRepository, ProductReviewRepository reviewRepository) {
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
    }

    // Get all products
    @GetMapping
    public List<Product> getProducts() {
        return includeRatings(productRepository.findByCategoryInAndActiveTrue(ELECTRICAL_CATEGORIES));
    }

    // Get a single product by ID
    @GetMapping("/{id}")
    public Product getProductById(@PathVariable Long id) {
        return productRepository.findById(id)
            .filter(product -> product.isActive() && ELECTRICAL_CATEGORIES.contains(product.getCategory()))
            .map(product -> includeRatings(List.of(product)).getFirst())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
    }

    @GetMapping(value = "/{id}/image", produces = "image/svg+xml")
    public String getProductImage(@PathVariable Long id) {
        Product product = productRepository.findById(id)
                .filter(candidate -> candidate.isActive() && ELECTRICAL_CATEGORIES.contains(candidate.getCategory()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        String name = escapeXml(product.getName() == null ? "Electrical product" : product.getName());
        String category = escapeXml(product.getCategory() == null ? "Electrical supplies" : product.getCategory());
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 600 420\" role=\"img\">"
                + "<title>Product photo unavailable</title>"
                + "<rect width=\"600\" height=\"420\" fill=\"#f0f2ed\"/>"
                + "<rect x=\"172\" y=\"92\" width=\"256\" height=\"140\" rx=\"8\" fill=\"none\" stroke=\"#bac3bb\" stroke-width=\"4\" stroke-dasharray=\"10 9\"/>"
                + "<circle cx=\"244\" cy=\"137\" r=\"12\" fill=\"#bac3bb\"/>"
                + "<path d=\"m190 210 72-48 40 30 35-24 73 42\" fill=\"none\" stroke=\"#bac3bb\" stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"7\"/>"
                + "<text x=\"300\" y=\"276\" fill=\"#66736f\" font-family=\"Arial,sans-serif\" font-size=\"18\" font-weight=\"700\" text-anchor=\"middle\">PRODUCT PHOTO UNAVAILABLE</text>"
                + "<text x=\"300\" y=\"314\" fill=\"#172321\" font-family=\"Arial,sans-serif\" font-size=\"17\" font-weight=\"700\" text-anchor=\"middle\">"
                + name + "</text>"
                + "<text x=\"300\" y=\"345\" fill=\"#66736f\" font-family=\"Arial,sans-serif\" font-size=\"14\" text-anchor=\"middle\">"
                + category + "</text></svg>";
    }

    // Search products by name
    @GetMapping("/search")
    public List<Product> searchProducts(@RequestParam String query) {
        return includeRatings(productRepository.findByNameContainingIgnoreCaseAndCategoryInAndActiveTrue(query, ELECTRICAL_CATEGORIES));
    }

    // Filter products by category
    @GetMapping("/category/{category}")
    public List<Product> getProductsByCategory(@PathVariable String category) {
        if (!ELECTRICAL_CATEGORIES.contains(category)) {
            return List.of();
        }
        return includeRatings(productRepository.findByCategoryAndActiveTrue(category));
    }

    // Get all unique categories
    @GetMapping("/categories/all")
    public List<String> getAllCategories() {
        return ELECTRICAL_CATEGORIES;
    }

    // Add a new product
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Product addProduct(@Valid @RequestBody Product product) {
        validateProduct(product);
        validateCategory(product.getCategory());
        product.setActive(true);
        return includeRatings(List.of(productRepository.save(product))).getFirst();
    }

    // Update an existing product
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Product updateProduct(@PathVariable Long id, @Valid @RequestBody Product product) {
        validateProduct(product);
        validateCategory(product.getCategory());
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        existingProduct.setName(product.getName());
        existingProduct.setCategory(product.getCategory());
        existingProduct.setPrice(product.getPrice());
        existingProduct.setImageUrl(product.getImageUrl());
        existingProduct.setDescription(product.getDescription());
        existingProduct.setStock(product.getStock());
        existingProduct.setBrand(product.getBrand());
        existingProduct.setSpecifications(product.getSpecifications());
        existingProduct.setWarranty(product.getWarranty());
        existingProduct.setOriginalPrice(product.getOriginalPrice());
        existingProduct.setOffer(product.getOffer());
        return includeRatings(List.of(productRepository.save(existingProduct))).getFirst();
    }

    // Keep product and order history intact when removing a product from the storefront.
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void deleteProduct(@PathVariable Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        product.setActive(false);
        productRepository.save(product);
    }

    private void validateProduct(Product product) {
        if (product.getName() == null || product.getName().isBlank() || product.getName().length() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a product name up to 180 characters.");
        }
        if (!Double.isFinite(product.getPrice()) || product.getPrice() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product price must be greater than zero.");
        }
        if (product.getStock() == null || product.getStock() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product stock cannot be negative.");
        }
        if (product.getOriginalPrice() != null
                && (!Double.isFinite(product.getOriginalPrice()) || product.getOriginalPrice() < product.getPrice())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Original price must be at least the current price.");
        }
    }

    private void validateCategory(String category) {
        if (!ELECTRICAL_CATEGORIES.contains(category)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an electrical product category.");
        }
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private List<Product> includeRatings(List<Product> products) {
        if (products.isEmpty()) {
            return products;
        }
        Map<Long, ProductReviewRepository.RatingAggregate> ratings =
                reviewRepository.summarizeByProductIds(products.stream().map(Product::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(ProductReviewRepository.RatingAggregate::getProductId,
                                Function.identity()));
        products.forEach(product -> {
            ProductReviewRepository.RatingAggregate rating = ratings.get(product.getId());
            product.setAverageRating(rating == null ? null : rating.getAverageRating());
            product.setReviewCount(rating == null ? 0 : rating.getReviewCount());
        });
        return products;
    }
}
