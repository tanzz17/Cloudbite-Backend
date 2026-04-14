package com.cloudbite.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "rider_locations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiderLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private Long riderId;
    private Double latitude;
    private Double longitude;
    private Double bearing;
    private Double speed;
    private LocalDateTime timestamp;
}
