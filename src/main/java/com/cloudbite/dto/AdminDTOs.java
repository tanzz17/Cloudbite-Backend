package com.cloudbite.dto;

import lombok.*;

public class AdminDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardStatsDTO {
        private long totalKitchens;
        private long activeKitchens;
        private long totalDeliveryPartners;
        private long activeDeliveryPartners;
        private long totalCustomers;
        private long totalOrders;
        private long pendingOrders;
        private long deliveredOrders;
        private Double totalRevenue;
        private Double todayRevenue;
        private Double weeklyRevenue;
        private Double monthlyRevenue;
    }
}
