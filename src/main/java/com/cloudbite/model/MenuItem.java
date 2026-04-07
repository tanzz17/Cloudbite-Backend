package com.cloudbite.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "menu_items")
@JsonIgnoreProperties({"kitchen"})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Double price;

    private String category;
    private String imageUrl;

    @Builder.Default
    private Boolean isVeg = true;

    @Builder.Default
    private Boolean isAvailable = true;

    @Builder.Default
    private Boolean isBestSeller = false;

    private Integer preparationTime; // minutes
    private Double rating;

    @Builder.Default
    private Integer totalOrders = 0;

    @ManyToOne
    @JoinColumn(name = "kitchen_id")
    private Kitchen kitchen;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
