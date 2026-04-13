package com.cloudbite.repository;

import com.cloudbite.model.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    
    List<UserAddress> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);
    
    Optional<UserAddress> findByUserIdAndIsDefaultTrue(Long userId);
    
    @Modifying
    @Query("UPDATE UserAddress ua SET ua.isDefault = false WHERE ua.user.id = :userId")
    void clearDefaultAddress(Long userId);
    
    long countByUserId(Long userId);
}
