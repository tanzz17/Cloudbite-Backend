package com.cloudbite.service;

import com.cloudbite.enums.OrderStatus;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.demo.mode:false}")
    private boolean demoMode;

    public Map<String, Object> createRazorpayPaymentLink(Long orderId, User customer) {
        Order order = getOwnedRazorpayOrder(orderId, customer, false);
        Payment existingPayment = paymentRepository.findByOrderId(orderId).orElse(null);

        if (demoMode) {
            return createDemoPaymentLink(order, existingPayment);
        }

        try {
            validateRazorpayConfiguration();
            log.info("Creating Razorpay order for orderId: {}, Key: {}", orderId, razorpayKeyId);
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", toSubunitAmount(order.getTotalAmount()));
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "rcpt_" + order.getId());
            
            JSONObject notes = new JSONObject();
            notes.put("orderId", order.getId());
            notes.put("orderNumber", order.getOrderNumber());
            orderRequest.put("notes", notes);

            log.info("Razorpay order request: {}", orderRequest.toString());
            
            com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);
            JSONObject razorpayOrderJson = razorpayOrder.toJson();
            
            log.info("Razorpay order created: {}", razorpayOrderJson.toString());

            Payment payment = existingPayment != null ? existingPayment : Payment.builder()
                    .order(order)
                    .method(PaymentMethod.RAZORPAY)
                    .build();
            payment.setAmount(order.getTotalAmount());
            payment.setCurrency("INR");
            payment.setRazorpayOrderId(razorpayOrderJson.optString("id", null));
            payment.setStatus(PaymentStatus.PENDING);
            paymentRepository.save(payment);

            order.setPaymentStatus(PaymentStatus.PENDING);
            order.setRazorpayOrderId(razorpayOrderJson.optString("id", null));
            orderRepository.save(order);

            Map<String, Object> response = new HashMap<>();
            response.put("razorpayOrderId", razorpayOrderJson.optString("id"));
            response.put("amount", order.getTotalAmount());
            response.put("currency", "INR");
            response.put("keyId", razorpayKeyId);
            response.put("orderNumber", order.getOrderNumber());
            response.put("orderId", order.getId());
            response.put("paymentStatus", order.getPaymentStatus());
            response.put("customerName", order.getCustomer().getName());
            response.put("customerEmail", order.getCustomer().getEmail());
            response.put("customerPhone", order.getCustomer().getPhone());
            return response;
            
        } catch (RazorpayException e) {
            String errorMsg = e.getMessage();
            log.error("=== RAZORPAY ERROR ===");
            log.error("Order ID: {}", orderId);
            log.error("Key ID being used: {}", razorpayKeyId);
            log.error("Error message: {}", errorMsg);
            log.error("======================");
            
            if (errorMsg != null && (errorMsg.contains("403") || errorMsg.contains("Bad authentication") || errorMsg.contains("authentication"))) {
                throw new RuntimeException("Razorpay authentication failed. Please check your API keys. Error: " + errorMsg);
            }
            throw new RuntimeException("Failed to create payment: " + errorMsg);
        }
    }

    public Map<String, Object> testRazorpayConnection() {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("Testing Razorpay connection with key: {}", razorpayKeyId);
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", 100);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "test_rcpt");
            
            com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);
            JSONObject razorpayOrderJson = razorpayOrder.toJson();
            
            result.put("success", true);
            result.put("message", "Razorpay connection successful!");
            result.put("testOrderId", razorpayOrderJson.optString("id"));
            result.put("keyId", razorpayKeyId);
            log.info("Razorpay test successful: {}", razorpayOrderJson.toString());
        } catch (RazorpayException e) {
            result.put("success", false);
            result.put("message", "Razorpay connection failed: " + e.getMessage());
            result.put("error", e.getMessage());
            result.put("keyId", razorpayKeyId);
            log.error("Razorpay test failed: {}", e.getMessage());
        }
        return result;
    }

    private void validateRazorpayConfiguration() {
        if (!hasText(razorpayKeyId) || !hasText(razorpayKeySecret) ||
                razorpayKeyId.contains("your-razorpay-key-id") ||
                razorpayKeySecret.contains("your-razorpay-key-secret")) {
            throw new RuntimeException("Razorpay is not configured on the backend");
        }
    }

    public Map<String, Object> syncPaymentLinkStatus(Long orderId, User customer) {
        Order order = getOwnedRazorpayOrder(orderId, customer, true);
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment record not found"));

        if (payment.getRazorpayPaymentLinkId() == null || payment.getRazorpayPaymentLinkId().isBlank()) {
            return buildPaymentLinkResponse(order, payment);
        }

        if (demoMode && payment.getRazorpayPaymentLinkId() != null && payment.getRazorpayPaymentLinkId().startsWith("demo_link_")) {
            return buildPaymentLinkResponse(order, payment);
        }

        try {
            validateRazorpayConfiguration();
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            
            Map<String, Object> response = new HashMap<>();
            response.put("orderId", order.getId());
            response.put("razorpayOrderId", order.getRazorpayOrderId());
            response.put("paymentStatus", order.getPaymentStatus());
            response.put("keyId", razorpayKeyId);
            return response;
        } catch (RazorpayException e) {
            String errorMsg = e.getMessage();
            log.error("Razorpay sync error - Key ID: {}, Message: {}", razorpayKeyId, errorMsg);
            throw new RuntimeException("Failed to refresh payment status: " + errorMsg);
        }
    }

    public Map<String, Object> completeDemoPayment(Long orderId, User customer) {
        Order order = getOwnedRazorpayOrder(orderId, customer, true);
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment record not found"));

        if (!payment.getRazorpayPaymentLinkId().startsWith("demo_link_")) {
            throw new RuntimeException("Not a demo payment");
        }

        log.info("DEMO MODE: Completing simulated payment for order {}", orderId);
        
        order.setPaymentStatus(PaymentStatus.COMPLETED);
        order.setRazorpayPaymentId("demo_payment_" + System.currentTimeMillis());
        orderRepository.save(order);

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setRazorpayPaymentId(order.getRazorpayPaymentId());
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        notifyKitchenAboutOrder(order.getId(), order.getKitchen().getId());

        return buildPaymentLinkResponse(order, payment);
    }

    private Order getOwnedRazorpayOrder(Long orderId, User customer, boolean allowCompleted) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        if (order.getPaymentMethod() != PaymentMethod.RAZORPAY) {
            throw new RuntimeException("This order does not use Razorpay");
        }
        if (!allowCompleted && order.getPaymentStatus() == PaymentStatus.COMPLETED) {
            throw new RuntimeException("Payment already completed for this order");
        }
        return order;
    }

    private Map<String, Object> createDemoPaymentLink(Order order, Payment existingPayment) {
        log.info("DEMO MODE: Creating simulated payment link for order {}", order.getId());
        
        Payment payment = existingPayment != null ? existingPayment : Payment.builder()
                .order(order)
                .method(PaymentMethod.RAZORPAY)
                .build();
        payment.setAmount(order.getTotalAmount());
        payment.setCurrency("INR");
        payment.setRazorpayPaymentLinkId("demo_link_" + order.getId());
        payment.setRazorpayPaymentLinkUrl(frontendUrl + "/demo-payment?orderId=" + order.getId());
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        order.setPaymentStatus(PaymentStatus.PENDING);
        orderRepository.save(order);

        Map<String, Object> response = new HashMap<>();
        response.put("paymentLinkId", payment.getRazorpayPaymentLinkId());
        response.put("paymentLinkUrl", payment.getRazorpayPaymentLinkUrl());
        response.put("amount", order.getTotalAmount());
        response.put("currency", "INR");
        response.put("orderNumber", order.getOrderNumber());
        response.put("orderId", order.getId());
        response.put("paymentStatus", order.getPaymentStatus());
        response.put("linkStatus", payment.getStatus());
        response.put("customerName", order.getCustomer().getName());
        response.put("customerEmail", order.getCustomer().getEmail());
        response.put("customerPhone", order.getCustomer().getPhone());
        response.put("demoMode", true);
        return response;
    }

    private Map<String, Object> buildPaymentLinkResponse(Order order, Payment payment) {
        Map<String, Object> response = new HashMap<>();
        response.put("paymentLinkId", payment.getRazorpayPaymentLinkId());
        response.put("paymentLinkUrl", payment.getRazorpayPaymentLinkUrl());
        response.put("amount", order.getTotalAmount());
        response.put("currency", "INR");
        response.put("orderNumber", order.getOrderNumber());
        response.put("orderId", order.getId());
        response.put("paymentStatus", order.getPaymentStatus());
        response.put("linkStatus", payment.getStatus());
        response.put("customerName", order.getCustomer().getName());
        response.put("customerEmail", order.getCustomer().getEmail());
        response.put("customerPhone", order.getCustomer().getPhone());
        return response;
    }

    private void applyPaymentLinkState(Order order, Payment payment, JSONObject paymentLinkJson) {
        String linkStatus = paymentLinkJson.optString("status", "");
        payment.setRazorpayPaymentLinkId(paymentLinkJson.optString("id", payment.getRazorpayPaymentLinkId()));
        payment.setRazorpayPaymentLinkUrl(paymentLinkJson.optString("short_url", payment.getRazorpayPaymentLinkUrl()));
        payment.setCurrency(paymentLinkJson.optString("currency", payment.getCurrency()));

        if ("paid".equalsIgnoreCase(linkStatus)) {
            JSONObject paymentJson = getCapturedPayment(paymentLinkJson);
            String paymentId = paymentJson.optString("payment_id", paymentJson.optString("id", null));

            order.setPaymentStatus(PaymentStatus.COMPLETED);
            order.setRazorpayPaymentId(paymentId);
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setRazorpayPaymentId(paymentId);
            payment.setPaidAt(LocalDateTime.now());
            
            paymentRepository.save(payment);
            orderRepository.save(order);
            
            notifyKitchenAboutOrder(order.getId(), order.getKitchen().getId());
        } else if ("cancelled".equalsIgnoreCase(linkStatus) || "expired".equalsIgnoreCase(linkStatus)) {
            order.setPaymentStatus(PaymentStatus.FAILED);
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            payment.setStatus(PaymentStatus.FAILED);
            
            paymentRepository.save(payment);
            orderRepository.save(order);
        } else {
            order.setPaymentStatus(PaymentStatus.PENDING);
            payment.setStatus(PaymentStatus.PENDING);
            
            paymentRepository.save(payment);
            orderRepository.save(order);
        }
    }

    private JSONObject getCapturedPayment(JSONObject paymentLinkJson) {
        if (!paymentLinkJson.has("payments") || paymentLinkJson.isNull("payments")) {
            return new JSONObject();
        }
        var payments = paymentLinkJson.optJSONArray("payments");
        if (payments == null || payments.length() == 0) {
            return new JSONObject();
        }
        return payments.optJSONObject(0) != null ? payments.optJSONObject(0) : new JSONObject();
    }

    private String buildReferenceId(Order order) {
        String referenceId = "CB-" + order.getId() + "-" + System.currentTimeMillis();
        return referenceId.substring(0, Math.min(40, referenceId.length()));
    }

    private String buildCallbackUrl(Long orderId) {
        String normalizedFrontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return normalizedFrontendUrl + "/orders/" + orderId + "?payment_return=razorpay";
    }

    private int toSubunitAmount(Double amount) {
        return (int) Math.round(amount * 100);
    }

    private void clearStalePaymentLink(Payment payment) {
        payment.setRazorpayPaymentLinkId(null);
        payment.setRazorpayPaymentLinkUrl(null);
        payment.setRazorpayPaymentId(null);
        payment.setRazorpaySignature(null);
        payment.setPaidAt(null);
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);
    }

    private String normalizePhone(String phone) {
        if (!hasText(phone)) {
            return null;
        }
        String digitsOnly = phone.replaceAll("\\D", "");
        return digitsOnly.isBlank() ? null : digitsOnly;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void notifyKitchenAboutOrder(Long orderId, Long kitchenId) {
        messagingTemplate.convertAndSend("/topic/kitchen/" + kitchenId + "/orders", orderId);
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
                
                notifyKitchenAboutOrder(order.getId(), order.getKitchen().getId());
                log.info("Payment verified and completed for order: {}", orderId);
            }
            return isValid;
        } catch (Exception e) {
            log.error("Payment verification error: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Object> checkRazorpayConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("keyIdConfigured", hasText(razorpayKeyId) && !razorpayKeyId.contains("your-razorpay-key-id"));
        result.put("keySecretConfigured", hasText(razorpayKeySecret) && !razorpayKeySecret.contains("your-razorpay-key-secret"));
        result.put("keyIdPrefix", razorpayKeyId != null && razorpayKeyId.length() >= 10 ? razorpayKeyId.substring(0, 10) : "not-set");
        result.put("frontendUrl", frontendUrl);
        result.put("demoMode", demoMode);
        return result;
    }
}
