package com.shopifyplus.controller;

import com.shopifyplus.model.Product;
import com.shopifyplus.repository.*;
import com.shopifyplus.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ProductRepository products;
    @Autowired
    CartRepository carts;
    @Autowired
    OrderRepository orders;
    @Autowired
    JwtService jwt;

    String userToken;
    String adminToken;
    Product product;

    @BeforeEach
    void setup() {
        orders.deleteAll();
        carts.deleteAll();
        products.deleteAll();
        userToken = "Bearer " + jwt.generate("buyer-1", "user");
        adminToken = "Bearer " + jwt.generate("admin-1", "admin");
        product = products.save(Product.builder().name("Mouse").price(25).category("electronics").stock(5).build());
    }

    private void addToCart(String productId, int qty) throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", userToken)
                .contentType(APPLICATION_JSON)
                .content("{\"productId\":\"" + productId + "\",\"quantity\":" + qty + "}"));
    }

    private String checkout() throws Exception {
        return mvc.perform(post("/api/orders/checkout").header("Authorization", userToken))
                .andReturn().getResponse().getContentAsString();
    }

    private String idOf(String json) {
        int i = json.indexOf("\"id\":\"") + 6;
        return json.substring(i, json.indexOf('"', i));
    }

    @Test
    void anonymousCheckout401() throws Exception {
        mvc.perform(post("/api/orders/checkout")).andExpect(status().isUnauthorized());
    }

    @Test
    void emptyCartCheckout400() throws Exception {
        mvc.perform(post("/api/orders/checkout").header("Authorization", userToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsOrderReducesStockEmptiesCart() throws Exception {
        addToCart(product.getId(), 2);
        mvc.perform(post("/api/orders/checkout").header("Authorization", userToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(50.0))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        // stock 5 -> 3
        org.junit.jupiter.api.Assertions.assertEquals(3, products.findById(product.getId()).orElseThrow().getStock());
        // cart emptied
        mvc.perform(get("/api/cart").header("Authorization", userToken))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void insufficientStock400LeavesInventoryUnchanged() throws Exception {
        addToCart(product.getId(), 2);
        product.setStock(1);
        products.save(product);
        mvc.perform(post("/api/orders/checkout").header("Authorization", userToken))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals(1, products.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void listsOrdersPaginated() throws Exception {
        addToCart(product.getId(), 1);
        checkout();
        addToCart(product.getId(), 1);
        checkout();
        mvc.perform(get("/api/orders?limit=1&page=1").header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.pagination.totalItems").value(2))
                .andExpect(jsonPath("$.pagination.hasNext").value(true));
    }

    @Test
    void selectiveCheckoutLeavesTheRest() throws Exception {
        Product p2 = products.save(Product.builder().name("Keyboard").price(40).category("electronics").stock(10).build());
        addToCart(product.getId(), 2);
        addToCart(p2.getId(), 1);

        mvc.perform(post("/api/orders/checkout").header("Authorization", userToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"productIds\":[\"" + product.getId() + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").value(50.0));

        // p2 stays in the cart
        mvc.perform(get("/api/cart").header("Authorization", userToken))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").value(40.0));
        org.junit.jupiter.api.Assertions.assertEquals(3, products.findById(product.getId()).orElseThrow().getStock());
        org.junit.jupiter.api.Assertions.assertEquals(10, products.findById(p2.getId()).orElseThrow().getStock());
    }

    @Test
    void partialQuantityCheckoutLeavesRemainderInCart() throws Exception {
        addToCart(product.getId(), 3); // stock 5

        // Buy 2 of the 3 in the cart.
        mvc.perform(post("/api/orders/checkout").header("Authorization", userToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + product.getId() + "\",\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.total").value(50.0));

        // 1 unit stays in the cart; stock dropped by only 2 (5 -> 3).
        mvc.perform(get("/api/cart").header("Authorization", userToken))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.total").value(25.0));
        org.junit.jupiter.api.Assertions.assertEquals(3, products.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void itemsWithoutQuantityBuysTheWholeLine() throws Exception {
        addToCart(product.getId(), 3);

        // quantity omitted -> whole line (3), cart emptied.
        mvc.perform(post("/api/orders/checkout").header("Authorization", userToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + product.getId() + "\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].quantity").value(3));

        mvc.perform(get("/api/cart").header("Authorization", userToken))
                .andExpect(jsonPath("$.items.length()").value(0));
        org.junit.jupiter.api.Assertions.assertEquals(2, products.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void rejectsRequestingMoreThanInCart400() throws Exception {
        addToCart(product.getId(), 2); // stock 5, so this fails the cart cap, not stock

        mvc.perform(post("/api/orders/checkout").header("Authorization", userToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + product.getId() + "\",\"quantity\":5}]}"))
                .andExpect(status().isBadRequest());

        // Nothing charged: cart and stock unchanged.
        org.junit.jupiter.api.Assertions.assertEquals(5, products.findById(product.getId()).orElseThrow().getStock());
        mvc.perform(get("/api/cart").header("Authorization", userToken))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void rejectsPartialCheckoutWithNoMatch400() throws Exception {
        addToCart(product.getId(), 1);
        mvc.perform(post("/api/orders/checkout").header("Authorization", userToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"productIds\":[\"64b7f3c2e1a2c3d4e5f6a7b8\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminUpdatesStatus() throws Exception {
        addToCart(product.getId(), 1);
        String orderId = idOf(checkout());
        mvc.perform(patch("/api/orders/" + orderId + "/status").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"status\":\"shipped\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("shipped"));
    }

    @Test
    void nonAdminStatusUpdate403() throws Exception {
        addToCart(product.getId(), 1);
        String orderId = idOf(checkout());
        mvc.perform(patch("/api/orders/" + orderId + "/status").header("Authorization", userToken)
                        .contentType(APPLICATION_JSON).content("{\"status\":\"shipped\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidStatus400() throws Exception {
        addToCart(product.getId(), 1);
        String orderId = idOf(checkout());
        mvc.perform(patch("/api/orders/" + orderId + "/status").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"status\":\"bogus\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerFetchesOrder200_otherIsForbidden403() throws Exception {
        addToCart(product.getId(), 1);
        String orderId = idOf(checkout());

        mvc.perform(get("/api/orders/" + orderId).header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));

        String otherToken = "Bearer " + jwt.generate("other-1", "user");
        mvc.perform(get("/api/orders/" + orderId).header("Authorization", otherToken))
                .andExpect(status().isForbidden());
    }
}
