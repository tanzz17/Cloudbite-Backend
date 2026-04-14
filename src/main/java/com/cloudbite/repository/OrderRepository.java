package com.cloudbite.repository;

import com.cloudbite.enums.OrderStatus;
import com.cloudbite.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<Order> findByKitchenIdOrderByCreatedAtDesc(Long kitchenId);
    List<Order> findByKitchenIdAndStatusOrderByCreatedAtDesc(Long kitchenId, OrderStatus status);
    List<Order> findByDeliveryPartnerIdOrderByCreatedAtDesc(Long deliveryPartnerId);
    List<Order> findByDeliveryPartnerIdAndStatusOrderByCreatedAtDesc(Long deliveryPartnerId, OrderStatus status);
    Optional<Order> findByOrderNumber(String orderNumber);

    @Query("SELECT o FROM Order o WHERE o.kitchen.id = :kitchenId AND o.status NOT IN ('PAYMENT_FAILED', 'CANCELLED') ORDER BY o.createdAt DESC")
    List<Order> findActiveOrdersByKitchenId(Long kitchenId);

    @Query("SELECT o FROM Order o WHERE o.status IN ('READY_FOR_PICKUP','WAITING_FOR_PARTNER') AND o.deliveryPartner IS NULL ORDER BY o.createdAt ASC")
    List<Order> findAvailableOrdersForDelivery();

    @Query("SELECT o FROM Order o WHERE o.status = 'PENDING' AND o.paymentMethod = 'RAZORPAY' AND o.createdAt < :threshold")
    List<Order> findStalePendingOrders(LocalDateTime threshold);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = 'DELIVERED'")
    Double getTotalRevenue();

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = 'DELIVERED' AND o.kitchen.id = :kitchenId")
    Double getKitchenRevenue(Long kitchenId);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = 'DELIVERED' AND o.createdAt BETWEEN :start AND :end")
    Double getRevenueBetween(LocalDateTime start, LocalDateTime end);

    long countByStatus(OrderStatus status);
    long countByKitchenId(Long kitchenId);
}
