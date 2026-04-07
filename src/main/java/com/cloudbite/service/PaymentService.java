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
import com.razorpay.PaymentLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
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

    @Value("${app.frontend.url:${FRONTEND_URL:http://localhost:5173}}")
    private String frontendUrl;

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
                existingPayment.getRazorpayPaymentLinkId() != null &&
                !existingPayment.getRazorpayPaymentLinkId().isBlank() &&
                existingPayment.getRazorpayPaymentLinkUrl() != null &&
                !existingPayment.getRazorpayPaymentLinkUrl().isBlank()) {
            return buildPaymentLinkResponse(order, existingPayment);
        }

        try {
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject paymentLinkRequest = new JSONObject();
            paymentLinkRequest.put("amount", (int) Math.round(order.getTotalAmount() * 100)); // paise
            paymentLinkRequest.put("currency", "INR");
            paymentLinkRequest.put("reference_id", order.getOrderNumber());
            paymentLinkRequest.put("description", "CloudBite order " + order.getOrderNumber());
            paymentLinkRequest.put("callback_url", frontendUrl + "/orders/" + order.getId() + "?payment=processing");
            paymentLinkRequest.put("callback_method", "get");
            paymentLinkRequest.put("reminder_enable", true);
            paymentLinkRequest.put("notify", new JSONObject().put("sms", false).put("email", false));
            paymentLinkRequest.put("customer", new JSONObject()
                    .put("name", order.getCustomer().getName() != null ? order.getCustomer().getName() : "")
                    .put("email", order.getCustomer().getEmail() != null ? order.getCustomer().getEmail() : "")
                    .put("contact", order.getCustomer().getPhone() != null ? order.getCustomer().getPhone() : ""));

            PaymentLink paymentLink = razorpayClient.paymentLink.create(paymentLinkRequest);

            Payment payment = existingPayment != null ? existingPayment : Payment.builder()
                    .order(order)
                    .method(PaymentMethod.RAZORPAY)
                    .build();
            payment.setStatus(PaymentStatus.PENDING);
            payment.setAmount(order.getTotalAmount());
            payment.setRazorpayOrderId(null);
            payment.setRazorpayPaymentLinkId(paymentLink.get("id").toString());
            payment.setRazorpayPaymentLinkUrl(paymentLink.get("short_url").toString());
            payment.setCurrency("INR");
            payment.setRazorpayPaymentId(null);
            payment.setRazorpaySignature(null);
            payment.setPaidAt(null);
            paymentRepository.save(payment);

            order.setRazorpayOrderId(null);
            orderRepository.save(order);

            return buildPaymentLinkResponse(order, payment);

        } catch (RazorpayException e) {
            log.error("Razorpay error: {}", e.getMessage());
            throw new RuntimeException("Payment initialization failed: " + e.getMessage());
        }
    }

    private Map<String, Object> buildPaymentLinkResponse(Order order, Payment payment) {
        Map<String, Object> response = new HashMap<>();
        response.put("paymentLinkId", payment.getRazorpayPaymentLinkId());
        response.put("paymentLinkUrl", payment.getRazorpayPaymentLinkUrl());
        response.put("amount", order.getTotalAmount());
        response.put("currency", "INR");
        response.put("keyId", razorpayKeyId);
        response.put("orderNumber", order.getOrderNumber());
        response.put("customerName", order.getCustomer().getName());
        response.put("customerEmail", order.getCustomer().getEmail());
        response.put("customerPhone", order.getCustomer().getPhone());
        return response;
    }

    public Map<String, Object> syncPaymentLinkStatus(Long orderId, User customer) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if (payment.getRazorpayPaymentLinkId() == null || payment.getRazorpayPaymentLinkId().isBlank()) {
            throw new RuntimeException("Payment link not found");
        }

        try {
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            PaymentLink paymentLink = razorpayClient.paymentLink.fetch(payment.getRazorpayPaymentLinkId());
            String status = paymentLink.get("status").toString();

            if ("paid".equalsIgnoreCase(status)) {
                JSONArray payments = paymentLink.has("payments") && !paymentLink.isNull("payments")
                        ? paymentLink.getJSONArray("payments")
                        : new JSONArray();
                if (!payments.isEmpty()) {
                    JSONObject paymentData = payments.getJSONObject(0);
                    order.setPaymentStatus(PaymentStatus.COMPLETED);
                    order.setRazorpayPaymentId(paymentData.optString("payment_id", null));
                    orderRepository.save(order);

                    payment.setStatus(PaymentStatus.COMPLETED);
                    payment.setRazorpayPaymentId(paymentData.optString("payment_id", null));
                    payment.setPaidAt(LocalDateTime.now());
                    paymentRepository.save(payment);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", status);
            response.put("paymentStatus", order.getPaymentStatus().name());
            response.put("paid", order.getPaymentStatus() == PaymentStatus.COMPLETED);
            return response;
        } catch (RazorpayException e) {
            log.error("Payment link status sync error: {}", e.getMessage());
            throw new RuntimeException("Unable to sync payment status: " + e.getMessage());
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
