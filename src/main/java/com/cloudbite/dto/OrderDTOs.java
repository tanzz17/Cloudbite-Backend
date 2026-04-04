package com.cloudbite.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

public class OrderDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        private Long menuItemId;
        private Integer quantity;
        private String specialInstructions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlaceOrderRequest {
        private Long kitchenId;
        private List<OrderItemRequest> items;
        private String deliveryAddress;
        private Double deliveryLatitude;
        private Double deliveryLongitude;
        private String deliveryInstructions;
        private String paymentMethod;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDTO {
        private Long id;
        private String itemName;
        private Double itemPrice;
        private Integer quantity;
        private Double totalPrice;
        private String specialInstructions;
        private String imageUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderDTO {
        private Long id;
        private String orderNumber;
        private Long customerId;
        private String customerName;
        private String customerPhone;
        private Long kitchenId;
        private String kitchenName;
        private Long deliveryPartnerId;
        private String deliveryPartnerName;
        private String deliveryPartnerPhone;
        private List<OrderItemDTO> items;
        private String status;
        private String paymentMethod;
        private String paymentStatus;
        private Double subtotal;
        private Double deliveryFee;
        private Double tax;
        private Double totalAmount;
        private String deliveryAddress;
        private Double deliveryLatitude;
        private Double deliveryLongitude;
        private String deliveryInstructions;
        private Integer estimatedDeliveryTime;
        private LocalDateTime createdAt;
        private LocalDateTime confirmedAt;
        private LocalDateTime readyAt;
        private LocalDateTime deliveredAt;
    }
}
