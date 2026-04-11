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
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            if (existingPayment != null &&
                    existingPayment.getRazorpayPaymentLinkId() != null &&
                    !existingPayment.getRazorpayPaymentLinkId().isBlank()) {
                try {
                    JSONObject existingLink = razorpayClient.paymentLink
                            .fetch(existingPayment.getRazorpayPaymentLinkId())
                            .toJson();
                    applyPaymentLinkState(order, existingPayment, existingLink);

                    if (order.getPaymentStatus() == PaymentStatus.COMPLETED) {
                        return buildPaymentLinkResponse(order, existingPayment);
                    }

                    String linkStatus = existingLink.optString("status", "");
                    if ("created".equalsIgnoreCase(linkStatus) || "partially_paid".equalsIgnoreCase(linkStatus)) {
                        return buildPaymentLinkResponse(order, existingPayment);
                    }
                } catch (RazorpayException e) {
                    log.warn("Stored Razorpay payment link {} is no longer reusable for order {}: {}",
                            existingPayment.getRazorpayPaymentLinkId(), orderId, e.getMessage());
                    clearStalePaymentLink(existingPayment);
                }
            }

            JSONObject linkRequest = new JSONObject();
            linkRequest.put("amount", toSubunitAmount(order.getTotalAmount()));
            linkRequest.put("currency", "INR");
            linkRequest.put("reference_id", buildReferenceId(order));
            linkRequest.put("description", "Payment for order " + order.getOrderNumber());
            linkRequest.put("callback_url", buildCallbackUrl(order.getId()));
            linkRequest.put("callback_method", "get");

            JSONObject customerJson = new JSONObject();
            if (hasText(order.getCustomer().getName())) {
                customerJson.put("name", order.getCustomer().getName().trim());
            }
            if (hasText(order.getCustomer().getEmail())) {
                customerJson.put("email", order.getCustomer().getEmail().trim());
            }
            String normalizedPhone = normalizePhone(order.getCustomer().getPhone());
            if (hasText(normalizedPhone)) {
                customerJson.put("contact", normalizedPhone);
            }
            linkRequest.put("customer", customerJson);

            JSONObject notifyJson = new JSONObject();
            notifyJson.put("sms", false);
            notifyJson.put("email", false);
            linkRequest.put("notify", notifyJson);
            linkRequest.put("reminder_enable", false);
            linkRequest.put("expire_by", LocalDateTime.now().plusHours(24).toEpochSecond(ZoneOffset.UTC));

            JSONObject notes = new JSONObject();
            notes.put("orderId", order.getId());
            notes.put("orderNumber", order.getOrderNumber());
            linkRequest.put("notes", notes);

            JSONObject paymentLinkJson = razorpayClient.paymentLink.create(linkRequest).toJson();

            Payment payment = existingPayment != null ? existingPayment : Payment.builder()
                    .order(order)
                    .method(PaymentMethod.RAZORPAY)
                    .build();
            payment.setAmount(order.getTotalAmount());
            payment.setCurrency("INR");
            payment.setRazorpayOrderId(null);
            payment.setRazorpayPaymentLinkId(paymentLinkJson.optString("id", null));
            payment.setRazorpayPaymentLinkUrl(paymentLinkJson.optString("short_url", null));
            payment.setRazorpayPaymentId(null);
            payment.setRazorpaySignature(null);
            payment.setPaidAt(null);
            payment.setStatus(PaymentStatus.PENDING);
            paymentRepository.save(payment);

            order.setPaymentStatus(PaymentStatus.PENDING);
            order.setRazorpayOrderId(null);
            orderRepository.save(order);

            return buildPaymentLinkResponse(order, payment);
        } catch (RazorpayException e) {
            String errorMsg = e.getMessage();
            log.error("=== RAZORPAY ERROR ===");
            log.error("Order ID: {}", orderId);
            log.error("Key ID being used: {}", razorpayKeyId);
            log.error("Error message: {}", errorMsg);
            log.error("Error HTTP Status: {}", e.getHttpStatusCode());
            log.error("Error code: {}", e.getCode());
            try {
                log.error("Error response body: {}", new JSONObject(e.getMessage()).toString(2));
            } catch (Exception ex) {
                log.error("Could not parse error response");
            }
            log.error("======================");
            
            if (errorMsg != null && (errorMsg.contains("403") || errorMsg.contains("Bad authentication"))) {
                throw new RuntimeException("Payment service authentication failed. Please check your Razorpay API keys in Render dashboard.");
            }
            throw new RuntimeException("Payment initialization failed: " + errorMsg);
        }
    }

    public Map<String, Object> syncPaymentLinkStatus(Long orderId, User customer) {
        Order order = getOwnedRazorpayOrder(orderId, customer, true);
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment record not found"));

        if (payment.getRazorpayPaymentLinkId() == null || payment.getRazorpayPaymentLinkId().isBlank()) {
            return buildPaymentLinkResponse(order, payment);
        }

        if (demoMode && payment.getRazorpayPaymentLinkId().startsWith("demo_link_")) {
            return buildPaymentLinkResponse(order, payment);
        }

        try {
            validateRazorpayConfiguration();
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject paymentLinkJson = razorpayClient.paymentLink.fetch(payment.getRazorpayPaymentLinkId()).toJson();
            applyPaymentLinkState(order, payment, paymentLinkJson);
            return buildPaymentLinkResponse(order, payment);
        } catch (RazorpayException e) {
            String errorMsg = e.getMessage();
            log.error("Razorpay sync error - Key ID: {}, Message: {}", razorpayKeyId, errorMsg);
            if (errorMsg != null && (errorMsg.contains("403") || errorMsg.contains("Bad authentication"))) {
                throw new RuntimeException("Payment service authentication failed. Please check your Razorpay API keys.");
            }
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

    private void validateRazorpayConfiguration() {
        if (!hasText(razorpayKeyId) || !hasText(razorpayKeySecret) ||
                razorpayKeyId.contains("your-razorpay-key-id") ||
                razorpayKeySecret.contains("your-razorpay-key-secret")) {
            throw new RuntimeException("Razorpay is not configured on the backend");
        }
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
        return result;
    }
}
