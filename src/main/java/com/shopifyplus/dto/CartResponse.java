package com.shopifyplus.dto;

import com.shopifyplus.model.Cart;

import java.util.List;

// Cart view with live totals. `pagination` is populated only on GET /api/cart.
public record CartResponse(
        List<Cart.CartItem> items,
        String couponCode,
        double subtotal,
        double discount,
        double total,
        PagedResponse.PageMeta pagination
) {
}
