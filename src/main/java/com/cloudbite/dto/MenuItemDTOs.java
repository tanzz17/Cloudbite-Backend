package com.cloudbite.dto;

import lombok.*;

public class MenuItemDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuItemDTO {
        private Long id;
        private String name;
        private String description;
        private Double price;
        private String category;
        private String imageUrl;
        private Boolean isVeg;
        private Boolean isAvailable;
        private Boolean isBestSeller;
        private Integer preparationTime;
        private Long kitchenId;
        private String kitchenName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateMenuItemRequest {
        private String name;
        private String description;
        private Double price;
        private String category;
        private String imageUrl;
        private Boolean isVeg;
        private Integer preparationTime;
    }
}
