package com.shopifyplus.dto;

import java.util.List;

// Checkout selection. Three accepted shapes (most specific wins):
//   1. {"items":[{"productId":"x","quantity":2}]}  -> buy a specific quantity of each line
//                                                      (quantity optional; omitted = whole line)
//   2. {"productIds":["x","y"]}                     -> buy those whole lines (legacy)
//   3. {} or no body                               -> buy the entire cart
public record CheckoutRequest(List<String> productIds, List<Line> items) {

    // quantity is nullable: null means "the whole line's quantity".
    public record Line(String productId, Integer quantity) {
    }

    // Normalize whatever the client sent into a single selection list.
    // Returns null to mean "the whole cart".
    public List<Line> toSelection() {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        if (productIds != null && !productIds.isEmpty()) {
            return productIds.stream().map(id -> new Line(id, null)).toList();
        }
        return null;
    }
}
