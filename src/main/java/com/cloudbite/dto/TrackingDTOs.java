package com.cloudbite.dto;

import lombok.*;
import java.time.LocalDateTime;

public class TrackingDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateLocationRequest {
        private Long orderId;
        private Long riderId;
        private Double latitude;
        private Double longitude;
        private Double bearing;
        private Double speed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationDTO {
        private Double latitude;
        private Double longitude;
        private Double bearing;
        private Double speed;
        private Long orderId;
        private Long riderId;
        private String orderStatus;
        private LocalDateTime recordedAt;
    }
}
