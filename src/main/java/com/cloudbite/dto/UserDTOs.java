package com.cloudbite.dto;

import lombok.*;
import java.time.LocalDateTime;

public class UserDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDTO {
        private Long id;
        private String name;
        private String email;
        private String phone;
        private String role;
        private String profileImage;
        private String address;
        private Boolean isActive;
        private Boolean isVerified;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateUserRequest {
        private String name;
        private String email;
        private String password;
        private String phone;
        private String role;
        private String address;
        private String vehicleType;
        private String vehicleNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateProfileRequest {
        private String name;
        private String phone;
        private String address;
        private String profileImage;
        private Double latitude;
        private Double longitude;
    }
}
