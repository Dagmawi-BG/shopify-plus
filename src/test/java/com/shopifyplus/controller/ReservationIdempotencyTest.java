package com.shopifyplus.controller;

import com.shopifyplus.model.Order;
import com.shopifyplus.model.Product;
import com.shopifyplus.repository.CartRepository;
import com.shopifyplus.repository.OrderRepository;
import com.shopifyplus.repository.ProductRepository;
import com.shopifyplus.security.JwtService;
import com.shopifyplus.service.OrderService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationIdempotencyTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ProductRepository products;
    @Autowired
    CartRepository carts;
    @Autowired
    OrderRepository orders;
    @Autowired
    OrderService orderService;
    @Autowired
    JwtService jwt;

    String token;
    Product product;

    @BeforeEach
    void setup() {
        orders.deleteAll();
        carts.deleteAll();
        products.deleteAll();
        token = "Bearer " + jwt.generate("buyer-r", "user");
        product = products.save(Product.builder().name("Mouse").price(25).category("electronics").stock(5).build());
    }

    private void addToCart(int qty) throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", token).contentType(APPLICATION_JSON)
                .content("{\"productId\":\"" + product.getId() + "\",\"quantity\":" + qty + "}"));
    }

    private String idOf(String json) {
        int i = json.indexOf("\"id\":\"") + 6;
        return json.substring(i, json.indexOf('"', i));
    }

    @Test
    void idempotentCheckoutReturnsSameOrderAndDecrementsOnce() throws Exception {
        addToCart(2);

        String first = mvc.perform(post("/api/orders/checkout").header("Authorization", token)
                        .header("Idempotency-Key", "key-123"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = idOf(first);

        // Same key again -> same order, no second decrement (cart is empty but we short-circuit).
        String second = mvc.perform(post("/api/orders/checkout").header("Authorization", token)
                        .header("Idempotency-Key", "key-123"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Assertions.assertEquals(orderId, idOf(second), "same key must return the same order");
        Assertions.assertEquals(3, products.findById(product.getId()).orElseThrow().getStock(),
                "stock decremented only once (5 - 2)");
        Assertions.assertEquals(1, orders.count(), "only one order created");
    }

    @Test
    void releasesExpiredReservationAndRestoresStock() throws Exception {
        addToCart(2);
        String json = mvc.perform(post("/api/orders/checkout").header("Authorization", token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String orderId = idOf(json);
        Assertions.assertEquals(3, products.findById(product.getId()).orElseThrow().getStock());

        // Force the reservation window into the past.
        Order order = orders.findById(orderId).orElseThrow();
        order.setReservationExpiresAt(Instant.now().minusSeconds(60));
        orders.save(order);

        int released = orderService.releaseExpiredReservations();

        Assertions.assertEquals(1, released);
        Assertions.assertEquals(5, products.findById(product.getId()).orElseThrow().getStock(), "stock restored");
        Assertions.assertEquals("cancelled", orders.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void paidOrderIsNotReleased() throws Exception {
        addToCart(1);
        String json = mvc.perform(post("/api/orders/checkout").header("Authorization", token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String orderId = idOf(json);

        Order order = orders.findById(orderId).orElseThrow();
        order.setStatus("paid");
        order.setReservationExpiresAt(Instant.now().minusSeconds(60));
        orders.save(order);

        orderService.releaseExpiredReservations();

        Assertions.assertEquals("paid", orders.findById(orderId).orElseThrow().getStatus(), "paid orders are untouched");
        Assertions.assertEquals(4, products.findById(product.getId()).orElseThrow().getStock(), "stock stays decremented");
    }
}
