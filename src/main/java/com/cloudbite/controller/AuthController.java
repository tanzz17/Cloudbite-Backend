package com.cloudbite.controller;

import com.cloudbite.dto.AuthDTOs.*;
import com.cloudbite.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.registerCustomer(request));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(java.security.Principal principal) {
        var user = authService.getCurrentUser(principal.getName());

        // Map.of() only supports 10 pairs — use HashMap for more fields
        Map<String, Object> response = new HashMap<>();
        response.put("id",            user.getId());
        response.put("name",          user.getName());
        response.put("email",         user.getEmail());
        response.put("role",          user.getRole().name());
        response.put("phone",         user.getPhone()         != null ? user.getPhone()         : "");
        response.put("address",       user.getAddress()       != null ? user.getAddress()       : "");
        response.put("profileImage",  user.getProfileImage()  != null ? user.getProfileImage()  : "");
        response.put("isActive",      user.getIsActive()      != null ? user.getIsActive()      : false);
        response.put("isVerified",    user.getIsVerified()    != null ? user.getIsVerified()    : false);
        response.put("vehicleType",   user.getVehicleType()   != null ? user.getVehicleType()   : "");
        response.put("vehicleNumber", user.getVehicleNumber() != null ? user.getVehicleNumber() : "");
        response.put("isAvailable",   user.getIsAvailable()   != null ? user.getIsAvailable()   : false);

        return ResponseEntity.ok(response);
    }
}
