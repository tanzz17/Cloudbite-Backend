package com.cloudbite.service;

import com.cloudbite.enums.PaymentMethod;
import com.cloudbite.enums.PaymentStatus;
import com.cloudbite.model.Order;
import com.cloudbite.model.Payment;
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

    public Map<String, Object> createRazorpayOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        try {
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", (int) (order.getTotalAmount() * 100)); // paise
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", order.getOrderNumber());

            com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);

            // Save payment record
            Payment payment = Payment.builder()
                    .order(order)
                    .method(PaymentMethod.RAZORPAY)
                    .status(PaymentStatus.PENDING)
                    .amount(order.getTotalAmount())
                    .razorpayOrderId(razorpayOrder.get("id"))
                    .currency("INR")
                    .build();
            paymentRepository.save(payment);

            // Update order with razorpay order id
            order.setRazorpayOrderId(razorpayOrder.get("id"));
            orderRepository.save(order);

            Map<String, Object> response = new HashMap<>();
            response.put("razorpayOrderId", razorpayOrder.get("id").toString());
            response.put("amount", order.getTotalAmount());
            response.put("currency", "INR");
            response.put("keyId", razorpayKeyId);
            response.put("orderNumber", order.getOrderNumber());
            response.put("customerName", order.getCustomer().getName());
            response.put("customerEmail", order.getCustomer().getEmail());
            response.put("customerPhone", order.getCustomer().getPhone());
            return response;

        } catch (RazorpayException e) {
            log.error("Razorpay error: {}", e.getMessage());
            throw new RuntimeException("Payment initialization failed: " + e.getMessage());
        }
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
