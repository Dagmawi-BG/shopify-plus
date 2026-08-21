package com.shopifyplus.service;

import com.shopifyplus.dto.CartResponse;
import com.shopifyplus.exception.NotFoundException;
import com.shopifyplus.model.Cart;
import com.shopifyplus.model.Cart.CartItem;
import com.shopifyplus.model.Coupon;
import com.shopifyplus.model.Product;
import com.shopifyplus.repository.CartRepository;
import com.shopifyplus.repository.CouponRepository;
import com.shopifyplus.repository.ProductRepository;
import com.shopifyplus.util.Money;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    // Cookie that identifies a guest's cart until they sign in.
    public static final String GUEST_COOKIE_NAME = "guestCartId";

    private final CartRepository carts;
    private final ProductRepository products;
    private final CouponRepository coupons;

    public CartService(CartRepository carts, ProductRepository products, CouponRepository coupons) {
        this.carts = carts;
        this.products = products;
        this.coupons = coupons;
    }

    public Cart getOrCreate(String userId) {
        return carts.findByUser(userId)
                .orElseGet(() -> carts.save(Cart.builder().user(userId).items(new ArrayList<>()).build()));
    }

    public Cart addItem(String userId, String productId, Integer quantity) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        int qty = quantity == null ? 1 : quantity;

        Cart cart = getOrCreate(userId);
        Optional<CartItem> existing = find(cart, productId);
        if (existing.isPresent()) {
            existing.get().setQuantity(existing.get().getQuantity() + qty);
        } else {
            cart.getItems().add(CartItem.builder()
                    .product(product.getId()).name(product.getName())
                    .price(product.getPrice()).quantity(qty).build());
        }
        return carts.save(cart);
    }

    public Cart updateItem(String userId, String productId, int quantity) {
        Cart cart = getOrCreate(userId);
        CartItem item = find(cart, productId)
                .orElseThrow(() -> new NotFoundException("Item not in cart"));
        if (quantity <= 0) {
            cart.getItems().removeIf(i -> i.getProduct().equals(productId));
        } else {
            item.setQuantity(quantity);
        }
        return carts.save(cart);
    }

    public Cart removeItem(String userId, String productId) {
        Cart cart = getOrCreate(userId);
        boolean removed = cart.getItems().removeIf(i -> i.getProduct().equals(productId));
        if (!removed) {
            throw new NotFoundException("Item not in cart");
        }
        return carts.save(cart);
    }

    // Fold a guest's cookie-cart into the user's cart on sign-in, then discard the guest cart.
    // Quantities for the same product add up; a guest coupon fills in only if the user has none.
    public void mergeGuestCart(String guestId, String userId) {
        if (guestId == null || guestId.isBlank() || guestId.equals(userId)) {
            return;
        }
        Optional<Cart> guestOpt = carts.findByUser(guestId);
        if (guestOpt.isEmpty()) {
            return;
        }
        Cart guest = guestOpt.get();

        if (!guest.getItems().isEmpty()) {
            Cart userCart = getOrCreate(userId);
            for (CartItem gi : guest.getItems()) {
                Optional<CartItem> existing = find(userCart, gi.getProduct());
                if (existing.isPresent()) {
                    existing.get().setQuantity(existing.get().getQuantity() + gi.getQuantity());
                } else {
                    userCart.getItems().add(gi);
                }
            }
            if (userCart.getCouponCode() == null && guest.getCouponCode() != null) {
                userCart.setCouponCode(guest.getCouponCode());
            }
            carts.save(userCart);
        }
        carts.delete(guest);
    }

    public Cart applyCoupon(String userId, String code) {
        Cart cart = getOrCreate(userId);
        cart.setCouponCode(code);
        return carts.save(cart);
    }

    // ---- Money (single source of truth; client can never dictate the amount) ----

    public Totals computeTotals(Cart cart) {
        return computeTotals(cart.getItems(), cart.getCouponCode());
    }

    // Recompute over the given items + stored coupon (re-validated live).
    public Totals computeTotals(List<CartItem> items, String couponCode) {
        double subtotal = Money.round2(items.stream().mapToDouble(i -> i.getPrice() * i.getQuantity()).sum());
        double discount = 0.0;
        String code = couponCode;

        if (code != null) {
            Coupon c = coupons.findByCode(code).orElse(null);
            if (c != null && c.isActive() && c.getExpiresAt().isAfter(Instant.now())) {
                double raw = "percentage".equals(c.getDiscountType())
                        ? subtotal * c.getAmount() / 100.0
                        : c.getAmount();
                discount = Money.round2(Math.min(raw, subtotal)); // never exceed the subtotal
            } else {
                code = null; // expired/removed -> drop it
            }
        }

        double total = Money.round2(Math.max(0, subtotal - discount));
        return new Totals(subtotal, discount, total, code);
    }

    public CartResponse toResponse(Cart cart) {
        Totals t = computeTotals(cart);
        return new CartResponse(cart.getItems(), t.couponCode(), t.subtotal(), t.discount(), t.total(), null);
    }

    private Optional<CartItem> find(Cart cart, String productId) {
        return cart.getItems().stream().filter(i -> i.getProduct().equals(productId)).findFirst();
    }

    public record Totals(double subtotal, double discount, double total, String couponCode) {
    }
}
