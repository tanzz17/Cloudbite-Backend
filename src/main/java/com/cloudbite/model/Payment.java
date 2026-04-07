package com.cloudbite.model;

import com.cloudbite.enums.PaymentMethod;
import com.cloudbite.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@JsonIgnoreProperties({"order"})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    private Double amount;
    private String razorpayOrderId;
    private String razorpayPaymentLinkId;
    private String razorpayPaymentLinkUrl;
    private String razorpayPaymentId;
    private String razorpaySignature;
    private String currency;

    @CreationTimestamp
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
