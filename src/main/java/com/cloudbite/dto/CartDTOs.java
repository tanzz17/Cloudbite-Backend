package com.cloudbite.dto;

import lombok.*;
import java.util.List;

public class CartDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddToCartRequest {
        private Long menuItemId;
        private Integer quantity;
        private String specialInstructions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemDTO {
        private Long cartItemId;
        private Long menuItemId;
        private String itemName;
        private Double itemPrice;
        private Integer quantity;
        private String imageUrl;
        private String specialInstructions;
        private Double totalPrice;
        private Boolean isVeg;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartDTO {
        private Long cartId;
        private Long kitchenId;
        private String kitchenName;
        private List<CartItemDTO> items;
        private Double totalAmount;
        private Integer itemCount;
    }
}
