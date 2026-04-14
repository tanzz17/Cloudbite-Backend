package com.cloudbite.service;

import com.cloudbite.enums.OrderStatus;
import com.cloudbite.enums.PaymentMethod;
import com.cloudbite.enums.PaymentStatus;
import com.cloudbite.model.*;
import com.cloudbite.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MenuItemRepository menuItemRepository;
    private final KitchenRepository kitchenRepository;
    private final CartRepository cartRepository;
    private final PaymentRepository paymentRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Order placeOrder(User customer, Map<String, Object> request) {
        Long kitchenId = ((Number) request.get("kitchenId")).longValue();
        Kitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new RuntimeException("Kitchen not found"));

        String paymentMethodStr = (String) request.get("paymentMethod");
        PaymentMethod paymentMethod = PaymentMethod.valueOf(paymentMethodStr.toUpperCase());

        List<Map<String, Object>> itemRequests = (List<Map<String, Object>>) request.get("items");
        List<OrderItem> orderItems = new ArrayList<>();
        double subtotal = 0;

        Order order = Order.builder()
                .orderNumber("CB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .customer(customer)
                .kitchen(kitchen)
                .status(OrderStatus.PENDING)
                .paymentMethod(paymentMethod)
                .paymentStatus(paymentMethod == PaymentMethod.COD ? PaymentStatus.PENDING : PaymentStatus.PENDING)
                .deliveryAddress((String) request.get("deliveryAddress"))
                .deliveryLatitude(request.get("deliveryLatitude") != null ? ((Number) request.get("deliveryLatitude")).doubleValue() : null)
                .deliveryLongitude(request.get("deliveryLongitude") != null ? ((Number) request.get("deliveryLongitude")).doubleValue() : null)
                .deliveryInstructions((String) request.get("deliveryInstructions"))
                .estimatedDeliveryTime(kitchen.getEstimatedDeliveryTime())
                .deliveryFee(kitchen.getDeliveryFee())
                .build();

        order = orderRepository.save(order);

        for (Map<String, Object> itemReq : itemRequests) {
            Long menuItemId = ((Number) itemReq.get("menuItemId")).longValue();
            int quantity = ((Number) itemReq.get("quantity")).intValue();
            MenuItem menuItem = menuItemRepository.findById(menuItemId)
                    .orElseThrow(() -> new RuntimeException("Menu item not found: " + menuItemId));
            double totalPrice = menuItem.getPrice() * quantity;
            subtotal += totalPrice;

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .menuItem(menuItem)
                    .itemName(menuItem.getName())
                    .itemPrice(menuItem.getPrice())
                    .quantity(quantity)
                    .totalPrice(totalPrice)
                    .specialInstructions((String) itemReq.get("specialInstructions"))
                    .build();
            orderItems.add(orderItem);
        }

        order.setItems(orderItems);
        order.setSubtotal(subtotal);
        double tax = subtotal * 0.05;
        order.setTax(tax);
        order.setTotalAmount(subtotal + kitchen.getDeliveryFee() + tax);

        Order savedOrder = orderRepository.save(order);

        // Clear cart
        cartRepository.findByUserId(customer.getId()).ifPresent(cartRepository::delete);

        // For COD orders, notify kitchen immediately
        // For RAZORPAY orders, kitchen will be notified only after successful payment (in PaymentService)
        if (paymentMethod == PaymentMethod.COD) {
            messagingTemplate.convertAndSend("/topic/kitchen/" + kitchenId + "/orders", savedOrder.getId());
        }

        return savedOrder;
    }

    // ======== Kitchen Owner Actions ========
    public Order confirmOrder(Long orderId, User kitchenOwner) {
        Order order = getOrderForKitchen(orderId, kitchenOwner);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setConfirmedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);
        notifyOrderUpdate(saved);
        return saved;
    }

    public Order markPreparing(Long orderId, User kitchenOwner) {
        Order order = getOrderForKitchen(orderId, kitchenOwner);
        order.setStatus(OrderStatus.PREPARING);
        order.setPreparingAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);
        notifyOrderUpdate(saved);
        return saved;
    }

    public Order markReadyForPickup(Long orderId, User kitchenOwner) {
        Order order = getOrderForKitchen(orderId, kitchenOwner);
        order.setStatus(OrderStatus.WAITING_FOR_PARTNER);
        order.setReadyAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);
        notifyOrderUpdate(saved);
        // Notify available delivery partners
        messagingTemplate.convertAndSend("/topic/delivery/available-orders", saved.getId());
        return saved;
    }

    public Order markHandover(Long orderId, User kitchenOwner) {
        Order order = getOrderForKitchen(orderId, kitchenOwner);
        order.setStatus(OrderStatus.HANDOVER);
        Order saved = orderRepository.save(order);
        notifyOrderUpdate(saved);
        return saved;
    }

    public Order markOutForDelivery(Long orderId, User kitchenOwner) {
        Order order = getOrderForKitchen(orderId, kitchenOwner);
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        Order saved = orderRepository.save(order);
        notifyOrderUpdate(saved);
        return saved;
    }

    // ======== Delivery Partner Actions ========
    public Order acceptDelivery(Long orderId, User deliveryPartner) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (order.getStatus() != OrderStatus.WAITING_FOR_PARTNER) {
            throw new RuntimeException("Order is not available for pickup");
        }
        order.setDeliveryPartner(deliveryPartner);
        order.setStatus(OrderStatus.ACCEPTED);
        Order saved = orderRepository.save(order);
        notifyOrderUpdate(saved);
        return saved;
    }

    public Order markDelivered(Long orderId, User deliveryPartner) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (!order.getDeliveryPartner().getId().equals(deliveryPartner.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            order.setPaymentStatus(PaymentStatus.COMPLETED);
        }
        Order saved = orderRepository.save(order);
        notifyOrderUpdate(saved);
        return saved;
    }

    // ======== GPS Delivery Steps ========
    public Order startTrip(Long orderId, User deliveryPartner) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (!order.getDeliveryPartner().getId().equals(deliveryPartner.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        if (order.getStatus() != OrderStatus.PARTNER_ASSIGNED && order.getStatus() != OrderStatus.ACCEPTED) {
            throw new RuntimeException("Order must be assigned before starting trip");
        }
        order.setStatus(OrderStatus.HEADING_TO_RESTAURANT);
        Order saved = orderRepository.save(order);
        notifyOrderUpdate(saved);
        return saved;
    }

    public Order arrivedAtRestaurant(Long orderId, User deliveryPartner) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (!order.getDeliveryPartner().getId().equals(deliveryPartner.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        if (order.getStatus() != OrderStatus.HEADING_TO_RESTAURANT) {
            throw new RuntimeException("Must start trip before arriving");
        }
        order.setStatus(OrderStatus.ARRIVED_AT_RESTAURANT);
        Order saved = orderRepository.save(order);
        notifyOrderUpdate(saved);
        return saved;
    }

    public Order pickUp(Long orderId, User deliveryPartner) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (!order.getDeliveryPartner().getId().equals(deliveryPartner.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        if (order.getStatus() != OrderStatus.ARRIVED_AT_RESTAURANT) {
            throw new RuntimeException("Must arrive at restaurant before picking up");
        }
        order.setStatus(OrderStatus.PICKED_UP);
        order.setPickedUpAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);
        notifyOrderUpdate(saved);
        return saved;
    }

    public Order headingToCustomer(Long orderId, User deliveryPartner) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (!order.getDeliveryPartner().getId().equals(deliveryPartner.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        if (order.getStatus() != OrderStatus.PICKED_UP) {
            throw new RuntimeException("Must pick up order before heading to customer");
        }
        order.setStatus(OrderStatus.HEADING_TO_CUSTOMER);
        Order saved = orderRepository.save(order);
        notifyOrderUpdate(saved);
        return saved;
    }

    public Order cancelOrderByCustomer(Long orderId, User customer, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        return cancelOrderInternal(order, reason);
    }

    public Order cancelOrderByKitchenOwner(Long orderId, User kitchenOwner, String reason) {
        Order order = getOrderForKitchen(orderId, kitchenOwner);
        return cancelOrderInternal(order, reason);
    }

    // ======== Getters ========
    public List<Order> getOrdersForKitchen(User kitchenOwner) {
        Kitchen kitchen = kitchenRepository.findByOwner(kitchenOwner)
                .orElseThrow(() -> new RuntimeException("Kitchen not found"));
        return orderRepository.findActiveOrdersByKitchenId(kitchen.getId());
    }

    public List<Order> getOrdersByStatus(User kitchenOwner, OrderStatus status) {
        Kitchen kitchen = kitchenRepository.findByOwner(kitchenOwner)
                .orElseThrow(() -> new RuntimeException("Kitchen not found"));
        return orderRepository.findByKitchenIdAndStatusOrderByCreatedAtDesc(kitchen.getId(), status);
    }

    public List<Order> getAvailableDeliveryOrders() {
        return orderRepository.findAvailableOrdersForDelivery();
    }

    public List<Order> getDeliveryPartnerOrders(User deliveryPartner) {
        return orderRepository.findByDeliveryPartnerIdOrderByCreatedAtDesc(deliveryPartner.getId());
    }

    public List<Order> getCustomerOrders(User customer) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId());
    }

    public Order getOrderById(Long orderId, User customer) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        return order;
    }

    private Order getOrderForKitchen(Long orderId, User kitchenOwner) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        Kitchen kitchen = kitchenRepository.findByOwner(kitchenOwner)
                .orElseThrow(() -> new RuntimeException("Kitchen not found"));
        if (!order.getKitchen().getId().equals(kitchen.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        return order;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll(
            org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
    }

    public long clearAllOrders() {
        long count = orderRepository.count();
        orderRepository.deleteAll();
        return count;
    }

    public Double getKitchenRevenue(Long kitchenId) {
        Double revenue = orderRepository.getKitchenRevenue(kitchenId);
        return revenue != null ? revenue : 0.0;
    }

    private Order cancelOrderInternal(Order order, String reason) {
        if (order.getStatus() == OrderStatus.OUT_FOR_DELIVERY || order.getStatus() == OrderStatus.DELIVERED) {
            throw new RuntimeException("Cannot cancel order at this stage");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancellationReason(reason);

        if (order.getPaymentMethod() == PaymentMethod.RAZORPAY && order.getPaymentStatus() != PaymentStatus.COMPLETED) {
            order.setPaymentStatus(PaymentStatus.FAILED);
        }

        Order saved = orderRepository.save(order);
        notifyOrderUpdate(saved);
        return saved;
    }

    private void notifyOrderUpdate(Order order) {
        // Notify customer
        messagingTemplate.convertAndSend(
                "/topic/order/" + order.getId() + "/status",
                Map.of("orderId", order.getId(), "status", order.getStatus().name())
        );
        // Notify kitchen
        messagingTemplate.convertAndSend(
                "/topic/kitchen/" + order.getKitchen().getId() + "/order-update",
                Map.of("orderId", order.getId(), "status", order.getStatus().name())
        );
        // Notify delivery partner if assigned
        if (order.getDeliveryPartner() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/delivery/" + order.getDeliveryPartner().getId() + "/order-update",
                    Map.of("orderId", order.getId(), "status", order.getStatus().name())
            );
        }
    }
}
