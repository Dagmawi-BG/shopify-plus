package com.shopifyplus.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    private String id;

    private String user;
    private List<OrderItem> items;

    private double subtotal;
    private double discount;
    private String couponCode;
    private double total;

    @Builder.Default
    private String status = "pending"; // pending | paid | shipped | cancelled

    private String paymentIntentId;

    // Same key -> same order (dedupes double-clicks / retries). Sparse: nulls allowed.
    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;

    // While pending, stock is held until this instant; a scheduler releases it if unpaid.
    private Instant reservationExpiresAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItem {
        private String product;
        private String name;
        private double price;
        private int quantity;
    }
}
