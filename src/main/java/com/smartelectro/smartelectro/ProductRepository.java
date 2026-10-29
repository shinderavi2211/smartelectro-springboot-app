package com.smartelectro.smartelectro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByNameContainingIgnoreCaseAndCategoryIn(String query, List<String> categories);

    List<Product> findByNameContainingIgnoreCaseAndCategoryInAndActiveTrue(String query, List<String> categories);

    List<Product> findByCategory(String category);

    List<Product> findByCategoryAndActiveTrue(String category);

    List<Product> findByCategoryIn(List<String> categories);
    List<Product> findByCategoryInAndActiveTrue(List<String> categories);
    List<Product> findAllByOrderByIdAsc();
    List<Product> findByNameContainingIgnoreCaseOrderByIdAsc(String query);
    long countByStockLessThanEqual(Integer stock);
    long countByStockLessThanEqualAndActiveTrue(Integer stock);
    List<Product> findByStockLessThanEqualOrderByStockAsc(Integer stock);
    List<Product> findByActiveTrueAndStockLessThanEqualOrderByStockAsc(Integer stock);

    @Query("SELECT DISTINCT p.category FROM Product p")
    List<String> findAllCategories();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}