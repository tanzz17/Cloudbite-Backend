package com.cloudbite.repository;

import com.cloudbite.model.DeliveryTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryTrackingRepository extends JpaRepository<DeliveryTracking, Long> {
    Optional<DeliveryTracking> findTopByOrderIdOrderByRecordedAtDesc(Long orderId);
    List<DeliveryTracking> findByOrderIdOrderByRecordedAtDesc(Long orderId);
}
