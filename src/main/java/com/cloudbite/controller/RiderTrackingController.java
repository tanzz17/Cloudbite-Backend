package com.cloudbite.controller;

import com.cloudbite.dto.LocationUpdateDTO;
import com.cloudbite.model.Order;
import com.cloudbite.model.RiderLocation;
import com.cloudbite.repository.OrderRepository;
import com.cloudbite.repository.RiderLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
public class RiderTrackingController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RiderLocationRepository locationRepo;
    private final OrderRepository orderRepo;

    @PostMapping("/location")
    public ResponseEntity<?> updateLocation(@RequestBody LocationUpdateDTO dto) {
        RiderLocation loc = RiderLocation.builder()
                .orderId(dto.getOrderId())
                .riderId(dto.getRiderId())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .bearing(dto.getBearing())
                .speed(dto.getSpeed())
                .timestamp(LocalDateTime.now())
                .build();
        locationRepo.save(loc);

        messagingTemplate.convertAndSend(
                "/topic/order/" + dto.getOrderId() + "/location",
                dto
        );

        return ResponseEntity.ok().build();
    }

    @PatchMapping("/order/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestParam String status) {

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        try {
            order.setStatus(com.cloudbite.enums.OrderStatus.valueOf(status));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status: " + status));
        }
        orderRepo.save(order);

        messagingTemplate.convertAndSend(
                "/topic/order/" + orderId + "/status",
                Map.of("orderId", orderId, "status", status)
        );

        return ResponseEntity.ok(Map.of("status", status));
    }

    @GetMapping("/order/{orderId}/location")
    public ResponseEntity<?> getLastLocation(@PathVariable Long orderId) {
        return locationRepo.findTopByOrderIdOrderByTimestampDesc(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
