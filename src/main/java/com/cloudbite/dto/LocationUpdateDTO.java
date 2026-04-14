package com.cloudbite.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationUpdateDTO {
    private Long orderId;
    private Long riderId;
    private Double latitude;
    private Double longitude;
    private Double bearing;
    private Double speed;
    private String orderStatus;
}
