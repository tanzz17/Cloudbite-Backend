package com.cloudbite.service;

import com.cloudbite.model.User;
import com.cloudbite.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User updateProfile(User user, Map<String, Object> request) {
        if (request.containsKey("name")) user.setName((String) request.get("name"));
        if (request.containsKey("phone")) user.setPhone((String) request.get("phone"));
        if (request.containsKey("address")) user.setAddress((String) request.get("address"));
        if (request.containsKey("profileImage")) user.setProfileImage((String) request.get("profileImage"));
        if (request.containsKey("latitude"))
            user.setLatitude(((Number) request.get("latitude")).doubleValue());
        if (request.containsKey("longitude"))
            user.setLongitude(((Number) request.get("longitude")).doubleValue());
        if (request.containsKey("vehicleType")) user.setVehicleType((String) request.get("vehicleType"));
        if (request.containsKey("vehicleNumber")) user.setVehicleNumber((String) request.get("vehicleNumber"));
        return userRepository.save(user);
    }

    public void changePassword(User user, String oldPassword, String newPassword, PasswordEncoder encoder) {
        if (!encoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
