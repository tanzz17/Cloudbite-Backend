package com.cloudbite.service;

import com.cloudbite.model.Kitchen;
import com.cloudbite.model.MenuItem;
import com.cloudbite.model.User;
import com.cloudbite.repository.KitchenRepository;
import com.cloudbite.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KitchenService {

    private final KitchenRepository kitchenRepository;
    private final MenuItemRepository menuItemRepository;

    private Double toNullableDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.doubleValue();
        String text = value.toString().trim();
        return text.isEmpty() ? null : Double.parseDouble(text);
    }

    public Kitchen getKitchenByOwner(User owner) {
        return kitchenRepository.findByOwner(owner)
                .orElseThrow(() -> new RuntimeException("Kitchen not found. Contact admin to set up your kitchen."));
    }

    public Kitchen createOrUpdateKitchen(User owner, Map<String, Object> request) {
        Kitchen kitchen = kitchenRepository.findByOwner(owner).orElse(Kitchen.builder().owner(owner).build());
        if (request.containsKey("name")) kitchen.setName((String) request.get("name"));
        if (request.containsKey("description")) kitchen.setDescription((String) request.get("description"));
        if (request.containsKey("cuisineType")) kitchen.setCuisineType((String) request.get("cuisineType"));
        if (request.containsKey("address")) kitchen.setAddress((String) request.get("address"));
        if (request.containsKey("city")) kitchen.setCity((String) request.get("city"));
        if (request.containsKey("pincode")) kitchen.setPincode((String) request.get("pincode"));
        if (request.containsKey("phone")) kitchen.setPhone((String) request.get("phone"));
        if (request.containsKey("logoImage")) kitchen.setLogoImage((String) request.get("logoImage"));
        if (request.containsKey("coverImage")) kitchen.setCoverImage((String) request.get("coverImage"));
        if (request.containsKey("minOrderAmount"))
            kitchen.setMinOrderAmount(((Number) request.get("minOrderAmount")).intValue());
        if (request.containsKey("estimatedDeliveryTime"))
            kitchen.setEstimatedDeliveryTime(((Number) request.get("estimatedDeliveryTime")).intValue());
        if (request.containsKey("deliveryFee"))
            kitchen.setDeliveryFee(((Number) request.get("deliveryFee")).doubleValue());
        if (request.containsKey("deliveryRadius"))
            kitchen.setDeliveryRadius(((Number) request.get("deliveryRadius")).doubleValue());
        if (request.containsKey("latitude"))
            kitchen.setLatitude(toNullableDouble(request.get("latitude")));
        if (request.containsKey("longitude"))
            kitchen.setLongitude(toNullableDouble(request.get("longitude")));
        if (request.containsKey("openingTime"))
            kitchen.setOpeningTime(LocalTime.parse((String) request.get("openingTime")));
        if (request.containsKey("closingTime"))
            kitchen.setClosingTime(LocalTime.parse((String) request.get("closingTime")));
        return kitchenRepository.save(kitchen);
    }

    public Kitchen toggleKitchenOpen(User owner) {
        Kitchen kitchen = getKitchenByOwner(owner);
        kitchen.setIsOpen(!kitchen.getIsOpen());
        return kitchenRepository.save(kitchen);
    }

    // ======== Menu Management ========
    public MenuItem addMenuItem(User owner, Map<String, Object> request) {
        Kitchen kitchen = getKitchenByOwner(owner);
        String category = (String) request.get("category");
        String subCategory = (String) request.get("subCategory");
        
        MenuItem item = MenuItem.builder()
                .name((String) request.get("name"))
                .description((String) request.get("description"))
                .price(((Number) request.get("price")).doubleValue())
                .category(category != null && !category.isEmpty() ? category : "Main Course")
                .subCategory(subCategory != null && !subCategory.isEmpty() ? subCategory : "General")
                .imageUrl((String) request.get("imageUrl"))
                .isVeg(request.containsKey("isVeg") ? (Boolean) request.get("isVeg") : true)
                .isAvailable(true)
                .preparationTime(request.containsKey("preparationTime") ? ((Number) request.get("preparationTime")).intValue() : 20)
                .kitchen(kitchen)
                .build();
        return menuItemRepository.save(item);
    }

    public MenuItem updateMenuItem(Long itemId, User owner, Map<String, Object> request) {
        MenuItem item = menuItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Menu item not found"));
        Kitchen kitchen = getKitchenByOwner(owner);
        if (!item.getKitchen().getId().equals(kitchen.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        if (request.containsKey("name")) item.setName((String) request.get("name"));
        if (request.containsKey("description")) item.setDescription((String) request.get("description"));
        if (request.containsKey("price")) item.setPrice(((Number) request.get("price")).doubleValue());
        if (request.containsKey("category")) {
            String cat = (String) request.get("category");
            if (cat != null && !cat.trim().isEmpty()) item.setCategory(cat);
        }
        if (request.containsKey("subCategory")) {
            String subCat = (String) request.get("subCategory");
            if (subCat != null && !subCat.trim().isEmpty()) item.setSubCategory(subCat);
        }
        if (request.containsKey("imageUrl")) item.setImageUrl((String) request.get("imageUrl"));
        if (request.containsKey("isVeg") && request.get("isVeg") != null) {
            item.setIsVeg((Boolean) request.get("isVeg"));
        }
        if (request.containsKey("isAvailable") && request.get("isAvailable") != null) {
            item.setIsAvailable((Boolean) request.get("isAvailable"));
        }
        if (request.containsKey("isBestSeller") && request.get("isBestSeller") != null) {
            item.setIsBestSeller((Boolean) request.get("isBestSeller"));
        }
        if (request.containsKey("preparationTime") && request.get("preparationTime") != null) {
            item.setPreparationTime(((Number) request.get("preparationTime")).intValue());
        }
        return menuItemRepository.save(item);
    }

    public void deleteMenuItem(Long itemId, User owner) {
        MenuItem item = menuItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Menu item not found"));
        Kitchen kitchen = getKitchenByOwner(owner);
        if (!item.getKitchen().getId().equals(kitchen.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        menuItemRepository.delete(item);
    }

    public List<MenuItem> getMenuItems(Long kitchenId) {
        return menuItemRepository.findByKitchenId(kitchenId);
    }

    public List<String> getMenuCategories(Long kitchenId) {
        return menuItemRepository.findDistinctCategoriesByKitchenId(kitchenId);
    }

    public List<String> getMenuSubCategories(Long kitchenId, String category) {
        return menuItemRepository.findDistinctSubCategoriesByKitchenIdAndCategory(kitchenId, category);
    }

    public MenuItem toggleItemAvailability(Long itemId, User owner) {
        MenuItem item = menuItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        item.setIsAvailable(!item.getIsAvailable());
        return menuItemRepository.save(item);
    }
}
