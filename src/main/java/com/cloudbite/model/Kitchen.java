package com.cloudbite.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "kitchens")
@JsonIgnoreProperties({"owner", "menuItems", "orders"})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Kitchen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    private String cuisineType;
    private String address;
    private String city;
    private String pincode;
    private Double latitude;
    private Double longitude;
    private String phone;
    private String email;
    private String logoImage;
    private String coverImage;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Boolean isOpen = true;

    private Double rating;

    @Builder.Default
    private Integer totalRatings = 0;

    private LocalTime openingTime;
    private LocalTime closingTime;

    @Builder.Default
    private Double deliveryRadius = 5.0;

    @Builder.Default
    private Integer minOrderAmount = 100;

    @Builder.Default
    private Integer estimatedDeliveryTime = 30;

    @Builder.Default
    private Double deliveryFee = 30.0;

    @OneToOne
    @JoinColumn(name = "owner_id")
    private User owner;

    @OneToMany(mappedBy = "kitchen", cascade = CascadeType.ALL)
    private List<MenuItem> menuItems;

    @OneToMany(mappedBy = "kitchen", cascade = CascadeType.ALL)
    private List<Order> orders;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public Long getId() { return id; }

    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
    public void setMinOrderAmount(Integer minOrderAmount) { this.minOrderAmount = minOrderAmount; }
    public void setEstimatedDeliveryTime(Integer estimatedDeliveryTime) { this.estimatedDeliveryTime = estimatedDeliveryTime; }
    public void setDeliveryFee(Double deliveryFee) { this.deliveryFee = deliveryFee; }
    public void setDeliveryRadius(Double deliveryRadius) { this.deliveryRadius = deliveryRadius; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
}
