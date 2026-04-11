package com.cloudbite.service;

import com.cloudbite.enums.OrderStatus;
import com.cloudbite.enums.PaymentMethod;
import com.cloudbite.enums.PaymentStatus;
import com.cloudbite.model.Order;
import com.cloudbite.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCleanupService {

    private final OrderRepository orderRepository;

    @Scheduled(fixedRate = 300000)
    @Transactional
    public void cleanupStalePendingOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        List<Order> staleOrders = orderRepository.findStalePendingOrders(threshold);
        
        for (Order order : staleOrders) {
            log.info("Cleaning up stale PENDING order: {} (created: {})", order.getId(), order.getCreatedAt());
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            order.setPaymentStatus(PaymentStatus.FAILED);
            orderRepository.save(order);
        }
        
        if (!staleOrders.isEmpty()) {
            log.info("Cleaned up {} stale pending orders", staleOrders.size());
        }
    }
}
