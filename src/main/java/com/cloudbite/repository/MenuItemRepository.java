package com.cloudbite.repository;

import com.cloudbite.model.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByKitchenId(Long kitchenId);
    List<MenuItem> findByKitchenIdAndIsAvailableTrue(Long kitchenId);
    List<MenuItem> findByKitchenIdAndCategory(Long kitchenId, String category);

    @Query("SELECT DISTINCT m.category FROM MenuItem m WHERE m.kitchen.id = :kitchenId")
    List<String> findDistinctCategoriesByKitchenId(Long kitchenId);

    @Query("SELECT DISTINCT m.subCategory FROM MenuItem m WHERE m.kitchen.id = :kitchenId AND m.category = :category")
    List<String> findDistinctSubCategoriesByKitchenIdAndCategory(Long kitchenId, String category);
    
    @Query("SELECT DISTINCT m.subCategory FROM MenuItem m WHERE m.category = :category")
    List<String> findDistinctSubCategoriesByCategory(String category);
}
