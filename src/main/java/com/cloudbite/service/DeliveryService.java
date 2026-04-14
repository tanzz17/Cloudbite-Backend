package com.cloudbite.service;

import com.cloudbite.dto.TrackingDTOs.UpdateLocationRequest;
import com.cloudbite.model.DeliveryTracking;
import com.cloudbite.model.Order;
import com.cloudbite.model.User;
import com.cloudbite.repository.DeliveryTrackingRepository;
import com.cloudbite.repository.OrderRepository;
import com.cloudbite.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final UserRepository userRepository;
    private final DeliveryTrackingRepository deliveryTrackingRepository;
    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public User toggleAvailability(User partner) {
        partner.setIsAvailable(!Boolean.TRUE.equals(partner.getIsAvailable()));
        return userRepository.save(partner);
    }

    public void updateLocation(User partner, UpdateLocationRequest request) {
        double lat = request.getLatitude();
        double lng = request.getLongitude();
        Long orderId = request.getOrderId();
        Double bearing = request.getBearing();
        Double speed = request.getSpeed();

        partner.setCurrentLatitude(lat);
        partner.setCurrentLongitude(lng);
        userRepository.save(partner);

        if (orderId != null) {
            DeliveryTracking tracking = DeliveryTracking.builder()
                    .deliveryPartner(partner)
                    .latitude(lat)
                    .longitude(lng)
                    .speed(speed)
                    .build();
            
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isPresent()) {
                tracking.setOrder(orderOpt.get());
            }
            deliveryTrackingRepository.save(tracking);

            String orderStatus = orderOpt.map(Order::getStatus).map(s -> s.name()).orElse(null);

            messagingTemplate.convertAndSend(
                    "/topic/order/" + orderId + "/location",
                    Map.of(
                            "latitude", lat,
                            "longitude", lng,
                            "bearing", bearing != null ? bearing : 0.0,
                            "speed", speed != null ? speed : 0.0,
                            "orderId", orderId,
                            "riderId", partner.getId(),
                            "orderStatus", orderStatus != null ? orderStatus : ""
                    )
            );
        }
    }

    public Map<String, Double> getPartnerLocation(Long partnerId) {
        User partner = userRepository.findById(partnerId)
                .orElseThrow(() -> new RuntimeException("Partner not found"));
        return Map.of(
                "latitude", partner.getCurrentLatitude() != null ? partner.getCurrentLatitude() : 0.0,
                "longitude", partner.getCurrentLongitude() != null ? partner.getCurrentLongitude() : 0.0
        );
    }
}
