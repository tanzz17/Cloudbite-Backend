package com.cloudbite.repository;

import com.cloudbite.model.RiderLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiderLocationRepository extends JpaRepository<RiderLocation, Long> {
    Optional<RiderLocation> findTopByOrderIdOrderByTimestampDesc(Long orderId);
}
