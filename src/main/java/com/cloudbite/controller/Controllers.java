package com.cloudbite.controller;

import com.cloudbite.model.User;
import com.cloudbite.repository.UserRepository;
import com.cloudbite.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.security.Principal;

// ==================== KITCHEN OWNER CONTROLLER ====================
@RestController
@RequestMapping("/api/kitchen")
@RequiredArgsConstructor
@PreAuthorize("hasRole('KITCHEN_OWNER')")
class KitchenController {

    private final KitchenService kitchenService;
    private final OrderService orderService;
    private final UserRepository userRepository;

    private User getUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getKitchen(Principal principal) {
        return ResponseEntity.ok(kitchenService.getKitchenByOwner(getUser(principal)));
    }

    @PostMapping("/profile")
    public ResponseEntity<?> updateKitchen(Principal principal, @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(kitchenService.createOrUpdateKitchen(getUser(principal), request));
    }

    @PatchMapping("/toggle-open")
    public ResponseEntity<?> toggleOpen(Principal principal) {
        return ResponseEntity.ok(kitchenService.toggleKitchenOpen(getUser(principal)));
    }

    // Menu
    @GetMapping("/menu")
    public ResponseEntity<?> getMenu(Principal principal) {
        var kitchen = kitchenService.getKitchenByOwner(getUser(principal));
        return ResponseEntity.ok(kitchenService.getMenuItems(kitchen.getId()));
    }

    @PostMapping("/menu")
    public ResponseEntity<?> addMenuItem(Principal principal, @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(kitchenService.addMenuItem(getUser(principal), request));
    }

    @PutMapping("/menu/{itemId}")
    public ResponseEntity<?> updateMenuItem(Principal principal, @PathVariable Long itemId,
                                            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(kitchenService.updateMenuItem(itemId, getUser(principal), request));
    }

    @DeleteMapping("/menu/{itemId}")
    public ResponseEntity<?> deleteMenuItem(Principal principal, @PathVariable Long itemId) {
        kitchenService.deleteMenuItem(itemId, getUser(principal));
        return ResponseEntity.ok(Map.of("message", "Item deleted"));
    }

    @PatchMapping("/menu/{itemId}/toggle-availability")
    public ResponseEntity<?> toggleAvailability(Principal principal, @PathVariable Long itemId) {
        return ResponseEntity.ok(kitchenService.toggleItemAvailability(itemId, getUser(principal)));
    }

    // Orders
    @GetMapping("/orders")
    public ResponseEntity<?> getOrders(Principal principal) {
        return ResponseEntity.ok(orderService.getOrdersForKitchen(getUser(principal)));
    }

    @PatchMapping("/orders/{orderId}/confirm")
    public ResponseEntity<?> confirmOrder(Principal principal, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.confirmOrder(orderId, getUser(principal)));
    }

    @PatchMapping("/orders/{orderId}/preparing")
    public ResponseEntity<?> markPreparing(Principal principal, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.markPreparing(orderId, getUser(principal)));
    }

    @PatchMapping("/orders/{orderId}/ready")
    public ResponseEntity<?> markReady(Principal principal, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.markReadyForPickup(orderId, getUser(principal)));
    }

    @PatchMapping("/orders/{orderId}/handover")
    public ResponseEntity<?> markHandover(Principal principal, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.markHandover(orderId, getUser(principal)));
    }

    @PatchMapping("/orders/{orderId}/out-for-delivery")
    public ResponseEntity<?> markOutForDelivery(Principal principal, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.markOutForDelivery(orderId, getUser(principal)));
    }

    @PatchMapping("/orders/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(Principal principal, @PathVariable Long orderId,
                                         @RequestBody Map<String, String> req) {
        return ResponseEntity.ok(orderService.cancelOrderByKitchenOwner(orderId, getUser(principal), req.get("reason")));
    }

    @GetMapping("/revenue")
    public ResponseEntity<?> getRevenue(Principal principal) {
        var kitchen = kitchenService.getKitchenByOwner(getUser(principal));
        return ResponseEntity.ok(Map.of("revenue", orderService.getKitchenRevenue(kitchen.getId())));
    }
}

// ==================== CUSTOMER CONTROLLER ====================
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
class CustomerController {

    private final OrderService orderService;
    private final CartService cartService;
    private final KitchenService kitchenService;
    private final PaymentService paymentService;
    private final AddressService addressService;
    private final UserRepository userRepository;
    private final com.cloudbite.repository.KitchenRepository kitchenRepository;

    private User getUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping("/kitchens")
    public ResponseEntity<?> getKitchens(@RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng) {
        var kitchens = kitchenRepository.findByIsActiveTrue();
        if (lat != null && lng != null) {
            kitchens.forEach(k -> {
                if (k.getLatitude() != null && k.getLongitude() != null) {
                    double distance = calculateDistance(lat, lng, k.getLatitude(), k.getLongitude());
                    k.setDeliveryRadius(distance);
                }
            });
            kitchens.sort((a, b) -> Double.compare(a.getDeliveryRadius() != null ? a.getDeliveryRadius() : 999, b.getDeliveryRadius() != null ? b.getDeliveryRadius() : 999));
        }
        return ResponseEntity.ok(kitchens);
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(R * c * 10.0) / 10.0;
    }

    @GetMapping("/kitchens/search")
    public ResponseEntity<?> searchKitchens(@RequestParam String q) {
        return ResponseEntity.ok(kitchenRepository.searchKitchens(q));
    }

    @GetMapping("/kitchens/{kitchenId}")
    public ResponseEntity<?> getKitchen(@PathVariable Long kitchenId) {
        return ResponseEntity.ok(kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new RuntimeException("Kitchen not found")));
    }

    @GetMapping("/kitchens/{kitchenId}/menu")
    public ResponseEntity<?> getMenu(@PathVariable Long kitchenId) {
        return ResponseEntity.ok(kitchenService.getMenuItems(kitchenId));
    }

    // Cart
    @GetMapping("/cart")
    public ResponseEntity<?> getCart(Principal principal) {
        return ResponseEntity.ok(cartService.getCart(getUser(principal)));
    }

    @PostMapping("/cart")
    public ResponseEntity<?> addToCart(Principal principal, @RequestBody Map<String, Object> req) {
        Long menuItemId = ((Number) req.get("menuItemId")).longValue();
        int quantity = ((Number) req.get("quantity")).intValue();
        String instructions = (String) req.get("specialInstructions");
        return ResponseEntity.ok(cartService.addToCart(getUser(principal), menuItemId, quantity, instructions));
    }

    @PutMapping("/cart/item/{cartItemId}")
    public ResponseEntity<?> updateCartItem(Principal principal, @PathVariable Long cartItemId,
                                             @RequestBody Map<String, Integer> req) {
        return ResponseEntity.ok(cartService.updateCartItem(getUser(principal), cartItemId, req.get("quantity")));
    }

    @DeleteMapping("/cart")
    public ResponseEntity<?> clearCart(Principal principal) {
        cartService.clearCart(getUser(principal));
        return ResponseEntity.ok(Map.of("message", "Cart cleared"));
    }

    // Orders
    @PostMapping("/orders")
    public ResponseEntity<?> placeOrder(Principal principal, @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(orderService.placeOrder(getUser(principal), request));
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getOrders(Principal principal) {
        return ResponseEntity.ok(orderService.getCustomerOrders(getUser(principal)));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getOrder(Principal principal, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrderById(orderId, getUser(principal)));
    }

    @PatchMapping("/orders/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(Principal principal, @PathVariable Long orderId,
                                         @RequestBody Map<String, String> req) {
        return ResponseEntity.ok(orderService.cancelOrderByCustomer(orderId, getUser(principal), req.get("reason")));
    }

    @PostMapping("/orders/{orderId}/payment-link")
    public ResponseEntity<?> createPaymentLink(Principal principal, @PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.createRazorpayPaymentLink(orderId, getUser(principal)));
    }

    @PostMapping("/orders/{orderId}/payment-sync")
    public ResponseEntity<?> syncPaymentStatus(Principal principal, @PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.syncPaymentLinkStatus(orderId, getUser(principal)));
    }

    @PostMapping("/orders/{orderId}/payment-failed")
    public ResponseEntity<?> markPaymentFailed(Principal principal, @PathVariable Long orderId,
                                               @RequestBody Map<String, String> req) {
        paymentService.markPaymentFailed(orderId, getUser(principal), req.getOrDefault("reason", "Payment failed or cancelled"));
        return ResponseEntity.ok(Map.of("success", true, "message", "Order marked as payment failed"));
    }

    // Address Management
    @GetMapping("/addresses")
    public ResponseEntity<?> getAddresses(Principal principal) {
        return ResponseEntity.ok(addressService.getUserAddresses(getUser(principal).getId()));
    }

    @GetMapping("/addresses/default")
    public ResponseEntity<?> getDefaultAddress(Principal principal) {
        return ResponseEntity.ok(addressService.getDefaultAddress(getUser(principal).getId()));
    }

    @PostMapping("/addresses")
    public ResponseEntity<?> addAddress(Principal principal, @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(addressService.addAddress(getUser(principal), request));
    }

    @PutMapping("/addresses/{addressId}")
    public ResponseEntity<?> updateAddress(Principal principal, @PathVariable Long addressId,
                                          @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(addressService.updateAddress(addressId, getUser(principal), request));
    }

    @PatchMapping("/addresses/{addressId}/default")
    public ResponseEntity<?> setDefaultAddress(Principal principal, @PathVariable Long addressId) {
        addressService.setDefaultAddress(addressId, getUser(principal));
        return ResponseEntity.ok(Map.of("success", true, "message", "Default address updated"));
    }

    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<?> deleteAddress(Principal principal, @PathVariable Long addressId) {
        addressService.deleteAddress(addressId, getUser(principal));
        return ResponseEntity.ok(Map.of("success", true, "message", "Address deleted"));
    }
}

// ==================== DELIVERY CONTROLLER ====================
@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DELIVERY_PARTNER')")
class DeliveryController {

    private final DeliveryService deliveryService;
    private final OrderService orderService;
    private final UserRepository userRepository;

    private User getUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @PatchMapping("/availability")
    public ResponseEntity<?> toggleAvailability(Principal principal) {
        return ResponseEntity.ok(deliveryService.toggleAvailability(getUser(principal)));
    }

    @GetMapping("/available-orders")
    public ResponseEntity<?> getAvailableOrders() {
        return ResponseEntity.ok(orderService.getAvailableDeliveryOrders());
    }

    @GetMapping("/my-orders")
    public ResponseEntity<?> getMyOrders(Principal principal) {
        return ResponseEntity.ok(orderService.getDeliveryPartnerOrders(getUser(principal)));
    }

    @PatchMapping("/orders/{orderId}/accept")
    public ResponseEntity<?> acceptOrder(Principal principal, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.acceptDelivery(orderId, getUser(principal)));
    }

    @PatchMapping("/orders/{orderId}/delivered")
    public ResponseEntity<?> markDelivered(Principal principal, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.markDelivered(orderId, getUser(principal)));
    }

    @PostMapping("/location")
    public ResponseEntity<?> updateLocation(Principal principal, @RequestBody Map<String, Object> req) {
        Long orderId = req.containsKey("orderId") ? ((Number) req.get("orderId")).longValue() : null;
        double lat = ((Number) req.get("latitude")).doubleValue();
        double lng = ((Number) req.get("longitude")).doubleValue();
        deliveryService.updateLocation(getUser(principal), orderId, lat, lng);
        return ResponseEntity.ok(Map.of("message", "Location updated"));
    }
}

// ==================== PAYMENT CONTROLLER ====================
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;

    private User getUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @PostMapping("/create-order")
    public ResponseEntity<?> createPaymentOrder(Principal principal, @RequestBody Map<String, Long> req) {
        return ResponseEntity.ok(paymentService.createRazorpayPaymentLink(req.get("orderId"), getUser(principal)));
    }

    @PostMapping("/create-link")
    public ResponseEntity<?> createPaymentLink(Principal principal, @RequestBody Map<String, Long> req) {
        return ResponseEntity.ok(paymentService.createRazorpayPaymentLink(req.get("orderId"), getUser(principal)));
    }

    @PostMapping("/orders/{orderId}/sync")
    public ResponseEntity<?> syncPaymentStatus(Principal principal, @PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.syncPaymentLinkStatus(orderId, getUser(principal)));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, Object> req) {
        boolean verified = paymentService.verifyPayment(
                (String) req.get("razorpayOrderId"),
                (String) req.get("razorpayPaymentId"),
                (String) req.get("razorpaySignature"),
                ((Number) req.get("orderId")).longValue()
        );
        if (verified) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Payment verified successfully"));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Payment verification failed"));
    }

    @PostMapping("/demo-complete/{orderId}")
    public ResponseEntity<?> completeDemoPayment(Principal principal, @PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.completeDemoPayment(orderId, getUser(principal)));
    }
}

// ==================== PUBLIC CONTROLLER ====================
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
class PublicController {

    private final com.cloudbite.repository.KitchenRepository kitchenRepository;
    private final KitchenService kitchenService;
    private final DeliveryService deliveryService;
    private final PaymentService paymentService;

    @GetMapping("/kitchens")
    public ResponseEntity<?> getKitchens() {
        return ResponseEntity.ok(kitchenRepository.findByIsActiveTrue());
    }

    @GetMapping("/kitchens/search")
    public ResponseEntity<?> search(@RequestParam String q) {
        return ResponseEntity.ok(kitchenRepository.searchKitchens(q));
    }

    @GetMapping("/kitchens/{id}")
    public ResponseEntity<?> getKitchen(@PathVariable Long id) {
        return ResponseEntity.ok(kitchenRepository.findById(id).orElseThrow());
    }

    @GetMapping("/kitchens/{id}/menu")
    public ResponseEntity<?> getMenu(@PathVariable Long id) {
        return ResponseEntity.ok(kitchenService.getMenuItems(id));
    }

    @GetMapping("/delivery/{partnerId}/location")
    public ResponseEntity<?> getPartnerLocation(@PathVariable Long partnerId) {
        return ResponseEntity.ok(deliveryService.getPartnerLocation(partnerId));
    }

    @GetMapping("/payment-config")
    public ResponseEntity<?> checkPaymentConfig() {
        return ResponseEntity.ok(paymentService.checkRazorpayConfig());
    }

    @GetMapping("/test-razorpay")
    public ResponseEntity<?> testRazorpayConnection() {
        return ResponseEntity.ok(paymentService.testRazorpayConnection());
    }
}

// ==================== PROFILE CONTROLLER ====================
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
class ProfileController {

    private final ProfileService profileService;
    private final UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private User getUser(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @PutMapping
    public ResponseEntity<?> updateProfile(Principal principal, @RequestBody Map<String, Object> req) {
        return ResponseEntity.ok(profileService.updateProfile(getUser(principal), req));
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(Principal principal, @RequestBody Map<String, String> req) {
        profileService.changePassword(getUser(principal), req.get("oldPassword"), req.get("newPassword"), passwordEncoder);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
