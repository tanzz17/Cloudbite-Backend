package com.cloudbite.service;

import com.cloudbite.enums.OrderStatus;
import com.cloudbite.enums.Role;
import com.cloudbite.model.Kitchen;
import com.cloudbite.model.User;
import com.cloudbite.repository.KitchenRepository;
import com.cloudbite.repository.OrderRepository;
import com.cloudbite.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final KitchenRepository kitchenRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;

    // ======== Dashboard Stats ========
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalKitchens", kitchenRepository.count());
        stats.put("activeKitchens", kitchenRepository.countByIsActiveTrue());
        stats.put("totalDeliveryPartners", userRepository.countByRole(Role.DELIVERY_PARTNER));
        stats.put("activeDeliveryPartners", userRepository.findByRoleAndIsAvailable(Role.DELIVERY_PARTNER, true).size());
        stats.put("totalCustomers", userRepository.countByRole(Role.CUSTOMER));
        stats.put("totalOrders", orderRepository.count());
        stats.put("pendingOrders", orderRepository.countByStatus(OrderStatus.PENDING));
        stats.put("deliveredOrders", orderRepository.countByStatus(OrderStatus.DELIVERED));
        stats.put("totalRevenue", orderRepository.getTotalRevenue());

        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime startOfWeek = LocalDateTime.now().minusDays(7);
        LocalDateTime startOfMonth = LocalDateTime.now().minusDays(30);
        stats.put("todayRevenue", orderRepository.getRevenueBetween(startOfDay, LocalDateTime.now()));
        stats.put("weeklyRevenue", orderRepository.getRevenueBetween(startOfWeek, LocalDateTime.now()));
        stats.put("monthlyRevenue", orderRepository.getRevenueBetween(startOfMonth, LocalDateTime.now()));
        return stats;
    }

    // ======== Kitchen Owner Management ========
    public User createKitchenOwner(Map<String, String> request) {
        if (userRepository.existsByEmail(request.get("email"))) {
            throw new RuntimeException("Email already exists");
        }
        User user = User.builder()
                .name(request.get("name"))
                .email(request.get("email"))
                .password(passwordEncoder.encode(request.get("password")))
                .phone(request.get("phone"))
                .role(Role.KITCHEN_OWNER)
                .isActive(true)
                .isVerified(true)
                .build();
        return userRepository.save(user);
    }

    public List<User> getAllKitchenOwners() {
        return userRepository.findByRole(Role.KITCHEN_OWNER);
    }

    public User toggleKitchenOwnerStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsActive(!user.getIsActive());
        return userRepository.save(user);
    }

    // ======== Delivery Partner Management ========
    public User createDeliveryPartner(Map<String, String> request) {
        if (userRepository.existsByEmail(request.get("email"))) {
            throw new RuntimeException("Email already exists");
        }
        User user = User.builder()
                .name(request.get("name"))
                .email(request.get("email"))
                .password(passwordEncoder.encode(request.get("password")))
                .phone(request.get("phone"))
                .role(Role.DELIVERY_PARTNER)
                .vehicleType(request.get("vehicleType"))
                .vehicleNumber(request.get("vehicleNumber"))
                .isActive(true)
                .isVerified(true)
                .isAvailable(false)
                .build();
        return userRepository.save(user);
    }

    public List<User> getAllDeliveryPartners() {
        return userRepository.findByRole(Role.DELIVERY_PARTNER);
    }

    public User toggleDeliveryPartnerStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsActive(!user.getIsActive());
        return userRepository.save(user);
    }

    // ======== Kitchen Management ========
    public List<Kitchen> getAllKitchens() {
        return kitchenRepository.findAll();
    }

    public Kitchen toggleKitchenStatus(Long kitchenId) {
        Kitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new RuntimeException("Kitchen not found"));
        kitchen.setIsActive(!kitchen.getIsActive());
        return kitchenRepository.save(kitchen);
    }

    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    public void deleteKitchen(Long kitchenId) {
        kitchenRepository.deleteById(kitchenId);
    }

    public User updateUserPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }
}
