package com.cloudbite.service;

import com.cloudbite.model.DeliveryTracking;
import com.cloudbite.model.User;
import com.cloudbite.repository.DeliveryTrackingRepository;
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
    private final SimpMessagingTemplate messagingTemplate;

    public User toggleAvailability(User partner) {
        partner.setIsAvailable(!Boolean.TRUE.equals(partner.getIsAvailable()));
        return userRepository.save(partner);
    }

    public void updateLocation(User partner, Long orderId, double lat, double lng) {
        // Update partner's current location
        partner.setCurrentLatitude(lat);
        partner.setCurrentLongitude(lng);
        userRepository.save(partner);

        // Save tracking record if on active delivery
        if (orderId != null) {
            DeliveryTracking tracking = DeliveryTracking.builder()
                    .deliveryPartner(partner)
                    .latitude(lat)
                    .longitude(lng)
                    .build();
            deliveryTrackingRepository.save(tracking);

            // Broadcast to customer tracking topic
            messagingTemplate.convertAndSend(
                    "/topic/order/" + orderId + "/location",
                    Map.of("lat", lat, "lng", lng, "partnerId", partner.getId())
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
