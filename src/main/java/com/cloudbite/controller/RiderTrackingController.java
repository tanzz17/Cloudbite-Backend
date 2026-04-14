package com.cloudbite.controller;

import com.cloudbite.dto.TrackingDTOs.UpdateLocationRequest;
import com.cloudbite.model.Order;
import com.cloudbite.model.User;
import com.cloudbite.repository.OrderRepository;
import com.cloudbite.repository.UserRepository;
import com.cloudbite.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
public class RiderTrackingController {

    private final DeliveryService deliveryService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @PostMapping("/location")
    public ResponseEntity<?> updateLocation(@RequestBody UpdateLocationRequest request) {
        User rider = userRepository.findById(request.getRiderId())
                .orElseThrow(() -> new RuntimeException("Rider not found"));
        deliveryService.updateLocation(rider, request);
        return ResponseEntity.ok(Map.of("message", "Location updated"));
    }

    @GetMapping("/order/{orderId}/location")
    public ResponseEntity<?> getLastLocation(@PathVariable Long orderId) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    User partner = order.getDeliveryPartner();
                    if (partner != null) {
                        return ResponseEntity.ok(Map.of(
                                "latitude", partner.getCurrentLatitude() != null ? partner.getCurrentLatitude() : 0.0,
                                "longitude", partner.getCurrentLongitude() != null ? partner.getCurrentLongitude() : 0.0,
                                "status", order.getStatus().name()
                        ));
                    }
                    return ResponseEntity.ok(Map.of(
                            "latitude", 0.0,
                            "longitude", 0.0,
                            "status", order.getStatus().name()
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
