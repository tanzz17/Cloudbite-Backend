package com.cloudbite.service;

import com.cloudbite.enums.PaymentMethod;
import com.cloudbite.enums.PaymentStatus;
import com.cloudbite.model.Order;
import com.cloudbite.model.Payment;
import com.cloudbite.model.User;
import com.cloudbite.repository.OrderRepository;
import com.cloudbite.repository.PaymentRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    public Map<String, Object> createRazorpayOrder(Long orderId, User customer) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        if (order.getPaymentMethod() != PaymentMethod.RAZORPAY) {
            throw new RuntimeException("This order does not use Razorpay");
        }
        if (order.getPaymentStatus() == PaymentStatus.COMPLETED) {
            throw new RuntimeException("Payment already completed for this order");
        }

        Payment existingPayment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (existingPayment != null &&
                existingPayment.getStatus() == PaymentStatus.PENDING &&
                existingPayment.getRazorpayOrderId() != null &&
                !existingPayment.getRazorpayOrderId().isBlank()) {
            order.setRazorpayOrderId(existingPayment.getRazorpayOrderId());
            orderRepository.save(order);
            return buildOrderResponse(order, existingPayment.getRazorpayOrderId());
        }

        try {
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", (int) (order.getTotalAmount() * 100)); // paise
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", order.getOrderNumber());

            com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);

            Payment payment = existingPayment != null ? existingPayment : Payment.builder()
                    .order(order)
                    .method(PaymentMethod.RAZORPAY)
                    .build();
            payment.setStatus(PaymentStatus.PENDING);
            payment.setAmount(order.getTotalAmount());
            payment.setRazorpayOrderId(razorpayOrder.get("id").toString());
            payment.setCurrency("INR");
            payment.setRazorpayPaymentId(null);
            payment.setRazorpaySignature(null);
            payment.setPaidAt(null);
            paymentRepository.save(payment);

            // Update order with razorpay order id
            order.setRazorpayOrderId(razorpayOrder.get("id").toString());
            orderRepository.save(order);

            return buildOrderResponse(order, razorpayOrder.get("id").toString());

        } catch (RazorpayException e) {
            log.error("Razorpay error: {}", e.getMessage());
            throw new RuntimeException("Payment initialization failed: " + e.getMessage());
        }
    }

    private Map<String, Object> buildOrderResponse(Order order, String razorpayOrderId) {
        Map<String, Object> response = new HashMap<>();
        response.put("razorpayOrderId", razorpayOrderId);
        response.put("amount", order.getTotalAmount());
        response.put("currency", "INR");
        response.put("keyId", razorpayKeyId);
        response.put("orderNumber", order.getOrderNumber());
        response.put("customerName", order.getCustomer().getName());
        response.put("customerEmail", order.getCustomer().getEmail());
        response.put("customerPhone", order.getCustomer().getPhone());
        return response;
    }

    public boolean verifyPayment(String razorpayOrderId, String razorpayPaymentId,
                                  String razorpaySignature, Long orderId) {
        try {
            String payload = razorpayOrderId + "|" + razorpayPaymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(razorpayKeySecret.getBytes(), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) hexString.append(String.format("%02x", b));
            boolean isValid = hexString.toString().equals(razorpaySignature);

            if (isValid) {
                Order order = orderRepository.findById(orderId)
                        .orElseThrow(() -> new RuntimeException("Order not found"));
                order.setPaymentStatus(PaymentStatus.COMPLETED);
                order.setRazorpayPaymentId(razorpayPaymentId);
                orderRepository.save(order);

                paymentRepository.findByRazorpayOrderId(razorpayOrderId).ifPresent(payment -> {
                    payment.setStatus(PaymentStatus.COMPLETED);
                    payment.setRazorpayPaymentId(razorpayPaymentId);
                    payment.setRazorpaySignature(razorpaySignature);
                    payment.setPaidAt(LocalDateTime.now());
                    paymentRepository.save(payment);
                });
            }
            return isValid;
        } catch (Exception e) {
            log.error("Payment verification error: {}", e.getMessage());
            return false;
        }
    }
}
