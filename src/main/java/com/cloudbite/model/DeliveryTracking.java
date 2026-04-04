package com.cloudbite.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_tracking")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne
    @JoinColumn(name = "delivery_partner_id")
    private User deliveryPartner;

    private Double latitude;
    private Double longitude;
    private Double speed;
    private String address;

    @CreationTimestamp
    private LocalDateTime recordedAt;
}
