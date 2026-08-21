package com.shopifyplus.controller;

import com.shopifyplus.model.Product;
import com.shopifyplus.repository.CartRepository;
import com.shopifyplus.repository.ProductRepository;
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
class CartControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    CartRepository carts;
    @Autowired
    ProductRepository products;
    @Autowired
    JwtService jwt;

    String token;
    Product product;

    @BeforeEach
    void setup() {
        carts.deleteAll();
        products.deleteAll();
        token = "Bearer " + jwt.generate("shopper-1", "user");
        product = products.save(Product.builder()
                .name("Mouse").price(25).category("electronics").stock(50).build());
    }

    private String addBody(String productId, int qty) {
        return "{\"productId\":\"" + productId + "\",\"quantity\":" + qty + "}";
    }

    @Test
    void anonymousGetsAGuestCart() throws Exception {
        // Guests are now first-class: an anonymous cart request succeeds and mints a guest cookie.
        mvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(cookie().exists("guestCartId"));
    }

    @Test
    void startsEmpty() throws Exception {
        mvc.perform(get("/api/cart").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0.0));
    }

    @Test
    void addsItemAndComputesTotal() throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", token)
                        .contentType(APPLICATION_JSON).content(addBody(product.getId(), 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.total").value(50.0));
    }

    @Test
    void incrementsWhenAddingSameProduct() throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", token)
                .contentType(APPLICATION_JSON).content(addBody(product.getId(), 1)));
        mvc.perform(post("/api/cart/items").header("Authorization", token)
                        .contentType(APPLICATION_JSON).content(addBody(product.getId(), 3)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(4))
                .andExpect(jsonPath("$.total").value(100.0));
    }

    @Test
    void updatesQuantity() throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", token)
                .contentType(APPLICATION_JSON).content(addBody(product.getId(), 2)));
        mvc.perform(put("/api/cart/items/" + product.getId()).header("Authorization", token)
                        .contentType(APPLICATION_JSON).content("{\"quantity\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(5))
                .andExpect(jsonPath("$.total").value(125.0));
    }

    @Test
    void removesItem() throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", token)
                .contentType(APPLICATION_JSON).content(addBody(product.getId(), 2)));
        mvc.perform(delete("/api/cart/items/" + product.getId()).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0.0));
    }

    @Test
    void addingNonExistentProductReturns404() throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", token)
                        .contentType(APPLICATION_JSON).content(addBody("64b7f3c2e1a2c3d4e5f6a7b8", 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void removingItemNotInCartReturns404() throws Exception {
        mvc.perform(delete("/api/cart/items/" + product.getId()).header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void paginatesItemsButKeepsWholeCartTotal() throws Exception {
        Product p2 = products.save(Product.builder().name("Keyboard").price(40).category("electronics").stock(10).build());
        Product p3 = products.save(Product.builder().name("Monitor").price(100).category("electronics").stock(10).build());
        mvc.perform(post("/api/cart/items").header("Authorization", token).contentType(APPLICATION_JSON).content(addBody(product.getId(), 1)));
        mvc.perform(post("/api/cart/items").header("Authorization", token).contentType(APPLICATION_JSON).content(addBody(p2.getId(), 1)));
        mvc.perform(post("/api/cart/items").header("Authorization", token).contentType(APPLICATION_JSON).content(addBody(p3.getId(), 1)));

        mvc.perform(get("/api/cart?limit=2&page=1").header("Authorization", token))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.pagination.totalItems").value(3))
                .andExpect(jsonPath("$.total").value(165.0));
    }
}
