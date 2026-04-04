package com.cloudbite.service;

import com.cloudbite.model.*;
import com.cloudbite.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final MenuItemRepository menuItemRepository;
    private final KitchenRepository kitchenRepository;

    public Cart getCart(User user) {
        return cartRepository.findByUserId(user.getId()).orElse(null);
    }

    @Transactional
    public Cart addToCart(User user, Long menuItemId, int quantity, String specialInstructions) {
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new RuntimeException("Menu item not found"));

        Cart cart = cartRepository.findByUserId(user.getId()).orElse(null);

        // If cart exists with different kitchen, clear it
        if (cart != null && cart.getKitchen() != null &&
                !cart.getKitchen().getId().equals(menuItem.getKitchen().getId())) {
            cartRepository.delete(cart);
            cart = null;
        }

        if (cart == null) {
            cart = Cart.builder()
                    .user(user)
                    .kitchen(menuItem.getKitchen())
                    .items(new ArrayList<>())
                    .build();
        }

        // Check if item already in cart
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(i -> i.getMenuItem().getId().equals(menuItemId))
                .findFirst();

        if (existingItem.isPresent()) {
            existingItem.get().setQuantity(existingItem.get().getQuantity() + quantity);
        } else {
            CartItem cartItem = CartItem.builder()
                    .cart(cart)
                    .menuItem(menuItem)
                    .quantity(quantity)
                    .specialInstructions(specialInstructions)
                    .build();
            cart.getItems().add(cartItem);
        }

        return cartRepository.save(cart);
    }

    @Transactional
    public Cart updateCartItem(User user, Long cartItemId, int quantity) {
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        cart.getItems().stream()
                .filter(i -> i.getId().equals(cartItemId))
                .findFirst()
                .ifPresent(item -> {
                    if (quantity <= 0) {
                        cart.getItems().remove(item);
                    } else {
                        item.setQuantity(quantity);
                    }
                });

        if (cart.getItems().isEmpty()) {
            cartRepository.delete(cart);
            return null;
        }

        return cartRepository.save(cart);
    }

    @Transactional
    public void clearCart(User user) {
        cartRepository.findByUserId(user.getId()).ifPresent(cartRepository::delete);
    }
}
