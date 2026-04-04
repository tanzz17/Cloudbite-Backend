package com.cloudbite.repository;

import com.cloudbite.model.Kitchen;
import com.cloudbite.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KitchenRepository extends JpaRepository<Kitchen, Long> {
    Optional<Kitchen> findByOwner(User owner);
    Optional<Kitchen> findByOwnerId(Long ownerId);
    List<Kitchen> findByIsActiveTrue();
    List<Kitchen> findByCityContainingIgnoreCase(String city);
    List<Kitchen> findByCuisineTypeContainingIgnoreCase(String cuisineType);

    @Query("SELECT k FROM Kitchen k WHERE k.isActive = true AND " +
           "(LOWER(k.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(k.cuisineType) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(k.city) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Kitchen> searchKitchens(String query);

    long countByIsActiveTrue();
}
