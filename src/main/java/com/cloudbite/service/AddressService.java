package com.cloudbite.service;

import com.cloudbite.model.User;
import com.cloudbite.model.UserAddress;
import com.cloudbite.repository.UserAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final UserAddressRepository userAddressRepository;

    public List<UserAddress> getUserAddresses(Long userId) {
        return userAddressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
    }

    public UserAddress getDefaultAddress(Long userId) {
        return userAddressRepository.findByUserIdAndIsDefaultTrue(userId).orElse(null);
    }

    @Transactional
    public UserAddress addAddress(User user, Map<String, Object> request) {
        String label = (String) request.get("label");
        String fullAddress = (String) request.get("fullAddress");
        String receiverName = (String) request.get("receiverName");
        String receiverPhone = (String) request.get("receiverPhone");
        Double latitude = request.get("latitude") != null ? ((Number) request.get("latitude")).doubleValue() : null;
        Double longitude = request.get("longitude") != null ? ((Number) request.get("longitude")).doubleValue() : null;
        Boolean isDefault = request.containsKey("isDefault") && (Boolean) request.get("isDefault");

        if (isDefault != null && isDefault) {
            userAddressRepository.clearDefaultAddress(user.getId());
        }

        boolean makeDefault = false;
        if (isDefault != null && isDefault) {
            makeDefault = true;
        } else if (userAddressRepository.countByUserId(user.getId()) == 0) {
            makeDefault = true;
        }

        UserAddress address = UserAddress.builder()
                .user(user)
                .label(label)
                .fullAddress(fullAddress)
                .receiverName(receiverName)
                .receiverPhone(receiverPhone)
                .latitude(latitude)
                .longitude(longitude)
                .isDefault(makeDefault)
                .build();

        return userAddressRepository.save(address);
    }

    @Transactional
    public UserAddress updateAddress(Long addressId, User user, Map<String, Object> request) {
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (!address.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        if (request.containsKey("label")) {
            address.setLabel((String) request.get("label"));
        }
        if (request.containsKey("fullAddress")) {
            address.setFullAddress((String) request.get("fullAddress"));
        }
        if (request.containsKey("receiverName")) {
            address.setReceiverName((String) request.get("receiverName"));
        }
        if (request.containsKey("receiverPhone")) {
            address.setReceiverPhone((String) request.get("receiverPhone"));
        }
        if (request.containsKey("latitude") && request.get("latitude") != null) {
            address.setLatitude(((Number) request.get("latitude")).doubleValue());
        }
        if (request.containsKey("longitude") && request.get("longitude") != null) {
            address.setLongitude(((Number) request.get("longitude")).doubleValue());
        }
        if (request.containsKey("isDefault") && (Boolean) request.get("isDefault")) {
            userAddressRepository.clearDefaultAddress(user.getId());
            address.setIsDefault(true);
        }

        return userAddressRepository.save(address);
    }

    @Transactional
    public void setDefaultAddress(Long addressId, User user) {
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (!address.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        userAddressRepository.clearDefaultAddress(user.getId());
        address.setIsDefault(true);
        userAddressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(Long addressId, User user) {
        UserAddress address = userAddressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (!address.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        userAddressRepository.delete(address);
    }
}
