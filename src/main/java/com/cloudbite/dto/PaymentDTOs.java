package com.cloudbite.dto;

import lombok.*;

public class PaymentDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePaymentOrderRequest {
        private Long orderId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentOrderResponse {
        private String razorpayOrderId;
        private Double amount;
        private String currency;
        private String keyId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifyPaymentRequest {
        private String razorpayOrderId;
        private String razorpayPaymentId;
        private String razorpaySignature;
        private Long orderId;
    }
}
