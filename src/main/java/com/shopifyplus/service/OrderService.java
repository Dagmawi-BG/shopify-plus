package com.shopifyplus.service;

import com.shopifyplus.dto.CheckoutRequest;
import com.shopifyplus.exception.BadRequestException;
import com.shopifyplus.exception.ForbiddenException;
import com.shopifyplus.exception.NotFoundException;
import com.shopifyplus.model.Cart;
import com.shopifyplus.model.Cart.CartItem;
import com.shopifyplus.model.Order;
import com.shopifyplus.model.Product;
import com.shopifyplus.repository.CartRepository;
import com.shopifyplus.repository.OrderRepository;
import com.shopifyplus.repository.ProductRepository;
import com.shopifyplus.util.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final CartRepository carts;
    private final ProductRepository products;
    private final CartService cartService;
    private final long reservationMinutes;

    public OrderService(OrderRepository orders, CartRepository carts,
                        ProductRepository products, CartService cartService,
                        @Value("${app.checkout.reservation-minutes:15}") long reservationMinutes) {
        this.orders = orders;
        this.carts = carts;
        this.products = products;
        this.cartService = cartService;
        this.reservationMinutes = reservationMinutes;
    }

    // Real ACID transaction: on any failure (e.g. insufficient stock) all stock
    // decrements roll back, so inventory is never partially applied.
    @Transactional
    public Order checkout(String userId, List<CheckoutRequest.Line> selection, String idempotencyKey) {
        // Idempotency: a repeat with the same key returns the original order —
        // no duplicate order, no second stock decrement.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Order> existing = orders.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Cart cart = cartService.getOrCreate(userId);
        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        // `selected` = the (possibly partial) lines being ordered now;
        // `remaining` = what stays in the cart afterwards (untouched lines + leftovers).
        List<CartItem> selected;
        List<CartItem> remaining;
        if (selection != null && !selection.isEmpty()) {
            selected = new ArrayList<>();
            remaining = new ArrayList<>();
            Map<String, Integer> requested = requestedQuantities(selection);

            for (CartItem cartItem : cart.getItems()) {
                Integer wantQty = requested.get(cartItem.getProduct());
                if (wantQty == null && !requested.containsKey(cartItem.getProduct())) {
                    remaining.add(cartItem); // not selected -> stays in cart
                    continue;
                }
                int cartQty = cartItem.getQuantity();
                int buyQty = (wantQty == null) ? cartQty : wantQty; // null quantity => whole line
                if (buyQty <= 0) {
                    throw new BadRequestException("Quantity must be positive for " + cartItem.getName());
                }
                if (buyQty > cartQty) {
                    throw new BadRequestException("Requested more than in cart for " + cartItem.getName());
                }
                selected.add(lineOf(cartItem, buyQty));
                if (buyQty < cartQty) {
                    remaining.add(lineOf(cartItem, cartQty - buyQty)); // keep the leftover
                }
            }
            if (selected.isEmpty()) {
                throw new BadRequestException("None of the selected items are in the cart");
            }
        } else {
            selected = new ArrayList<>(cart.getItems());
            remaining = new ArrayList<>();
        }

        // Decrement stock for the selected items (rolled back on failure).
        for (CartItem item : selected) {
            Product product = products.findById(item.getProduct())
                    .orElseThrow(() -> new BadRequestException("Insufficient stock for " + item.getName()));
            if (product.getStock() < item.getQuantity()) {
                throw new BadRequestException("Insufficient stock for " + item.getName());
            }
            product.setStock(product.getStock() - item.getQuantity());
            products.save(product);
        }

        // Totals recomputed server-side over the SELECTED items + the cart's stored coupon.
        CartService.Totals totals = cartService.computeTotals(selected, cart.getCouponCode());

        Order order = orders.save(Order.builder()
                .user(userId)
                .items(selected.stream().map(i -> Order.OrderItem.builder()
                        .product(i.getProduct()).name(i.getName())
                        .price(i.getPrice()).quantity(i.getQuantity()).build()).toList())
                .subtotal(totals.subtotal())
                .discount(totals.discount())
                .couponCode(totals.couponCode())
                .total(totals.total())
                .status("pending")
                .idempotencyKey((idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : null)
                .reservationExpiresAt(Instant.now().plus(reservationMinutes, ChronoUnit.MINUTES))
                .build());

        cart.setItems(new ArrayList<>(remaining));
        if (remaining.isEmpty()) {
            cart.setCouponCode(null);
        }
        carts.save(cart);
        return order;
    }

    // Collapse the requested lines into productId -> quantity (null = whole line).
    // Duplicate product entries are summed; any "whole line" request wins over a number.
    private Map<String, Integer> requestedQuantities(List<CheckoutRequest.Line> selection) {
        Map<String, Integer> requested = new LinkedHashMap<>();
        for (CheckoutRequest.Line line : selection) {
            String pid = line.productId();
            if (pid == null || pid.isBlank()) {
                continue;
            }
            if (!requested.containsKey(pid)) {
                requested.put(pid, line.quantity());
            } else {
                Integer prev = requested.get(pid);
                Integer q = line.quantity();
                requested.put(pid, (prev == null || q == null) ? null : prev + q);
            }
        }
        return requested;
    }

    // A copy of a cart line with a different quantity (leaves the source untouched).
    private CartItem lineOf(CartItem source, int quantity) {
        return CartItem.builder()
                .product(source.getProduct())
                .name(source.getName())
                .price(source.getPrice())
                .quantity(quantity)
                .build();
    }

    public Page<Order> myOrders(String userId, Pageable pageable) {
        return orders.findByUser(userId, pageable);
    }

    public Order getById(String userId, boolean isAdmin, String id) {
        Order order = orders.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (!order.getUser().equals(userId) && !isAdmin) {
            throw new ForbiddenException("Forbidden");
        }
        return order;
    }

    public Order updateStatus(String id, String status) {
        Order order = orders.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        order.setStatus(status);
        return orders.save(order);
    }

    // Releases held stock for pending orders whose reservation window lapsed (never
    // paid): restores inventory and cancels the order. Run periodically by a scheduler.
    @Transactional
    public int releaseExpiredReservations() {
        List<Order> expired = orders.findByStatusAndReservationExpiresAtBefore("pending", Instant.now());
        for (Order order : expired) {
            for (Order.OrderItem item : order.getItems()) {
                products.findById(item.getProduct()).ifPresent(p -> {
                    p.setStock(p.getStock() + item.getQuantity());
                    products.save(p);
                });
            }
            order.setStatus("cancelled");
            order.setReservationExpiresAt(null);
            orders.save(order);
        }
        return expired.size();
    }
}
