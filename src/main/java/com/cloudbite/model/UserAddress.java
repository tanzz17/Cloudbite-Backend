package com.cloudbite.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_addresses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"password", "orders", "cart", "kitchen"})
    private User user;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false, length = 1000)
    private String fullAddress;

    private String receiverName;

    private String receiverPhone;

    @Column(columnDefinition = "DOUBLE")
    private Double latitude;

    @Column(columnDefinition = "DOUBLE")
    private Double longitude;

    @Builder.Default
    private Boolean isDefault = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }
}
