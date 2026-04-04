package com.cloudbite.controller;

import com.cloudbite.service.AdminService;
import com.cloudbite.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final OrderService orderService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    // ======== Kitchen Owner CRUD ========
    @PostMapping("/kitchen-owners")
    public ResponseEntity<?> createKitchenOwner(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(adminService.createKitchenOwner(request));
    }

    @GetMapping("/kitchen-owners")
    public ResponseEntity<?> getAllKitchenOwners() {
        return ResponseEntity.ok(adminService.getAllKitchenOwners());
    }

    @PatchMapping("/kitchen-owners/{id}/toggle-status")
    public ResponseEntity<?> toggleKitchenOwnerStatus(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.toggleKitchenOwnerStatus(id));
    }

    @PatchMapping("/users/{id}/password")
    public ResponseEntity<?> updateUserPassword(@PathVariable Long id, @RequestBody Map<String, String> req) {
        return ResponseEntity.ok(adminService.updateUserPassword(id, req.get("password")));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    // ======== Delivery Partner CRUD ========
    @PostMapping("/delivery-partners")
    public ResponseEntity<?> createDeliveryPartner(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(adminService.createDeliveryPartner(request));
    }

    @GetMapping("/delivery-partners")
    public ResponseEntity<?> getAllDeliveryPartners() {
        return ResponseEntity.ok(adminService.getAllDeliveryPartners());
    }

    @PatchMapping("/delivery-partners/{id}/toggle-status")
    public ResponseEntity<?> toggleDeliveryPartnerStatus(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.toggleDeliveryPartnerStatus(id));
    }

    // ======== Kitchen Management ========
    @GetMapping("/kitchens")
    public ResponseEntity<?> getAllKitchens() {
        return ResponseEntity.ok(adminService.getAllKitchens());
    }

    @PatchMapping("/kitchens/{id}/toggle-status")
    public ResponseEntity<?> toggleKitchenStatus(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.toggleKitchenStatus(id));
    }

    @DeleteMapping("/kitchens/{id}")
    public ResponseEntity<?> deleteKitchen(@PathVariable Long id) {
        adminService.deleteKitchen(id);
        return ResponseEntity.ok(Map.of("message", "Kitchen deleted successfully"));
    }

    // ======== Orders ========
    @GetMapping("/orders")
    public ResponseEntity<?> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }
}
