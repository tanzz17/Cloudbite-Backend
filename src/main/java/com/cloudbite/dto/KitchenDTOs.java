package com.cloudbite.dto;

import lombok.*;
import java.time.LocalDateTime;

public class KitchenDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KitchenDTO {
        private Long id;
        private String name;
        private String description;
        private String cuisineType;
        private String address;
        private String city;
        private String pincode;
        private Double latitude;
        private Double longitude;
        private String phone;
        private String email;
        private String logoImage;
        private String coverImage;
        private Boolean isActive;
        private Boolean isOpen;
        private Double rating;
        private Integer totalRatings;
        private String openingTime;
        private String closingTime;
        private Double deliveryRadius;
        private Integer minOrderAmount;
        private Integer estimatedDeliveryTime;
        private Double deliveryFee;
        private Long ownerId;
        private String ownerName;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateKitchenRequest {
        private String name;
        private String description;
        private String cuisineType;
        private String address;
        private String city;
        private String pincode;
        private Double latitude;
        private Double longitude;
        private String phone;
        private String logoImage;
        private String coverImage;
        private String openingTime;
        private String closingTime;
        private Double deliveryRadius;
        private Integer minOrderAmount;
        private Integer estimatedDeliveryTime;
        private Double deliveryFee;
    }
}
