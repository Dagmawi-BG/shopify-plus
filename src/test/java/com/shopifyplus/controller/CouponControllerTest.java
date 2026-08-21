package com.shopifyplus.controller;

import com.shopifyplus.model.Coupon;
import com.shopifyplus.model.Product;
import com.shopifyplus.repository.*;
import com.shopifyplus.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CouponControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    CouponRepository couponsRepo;
    @Autowired
    CartRepository carts;
    @Autowired
    ProductRepository products;
    @Autowired
    OrderRepository orders;
    @Autowired
    JwtService jwt;

    String adminToken;
    String userToken;
    Product product;

    @BeforeEach
    void setup() {
        couponsRepo.deleteAll();
        carts.deleteAll();
        products.deleteAll();
        orders.deleteAll();
        adminToken = "Bearer " + jwt.generate("admin-1", "admin");
        userToken = "Bearer " + jwt.generate("shopper-1", "user");
        product = products.save(Product.builder().name("Mouse").price(25).category("electronics").stock(50).build());
    }

    static final String COUPON =
            "{\"code\":\"SAVE10\",\"discountType\":\"percentage\",\"amount\":10,\"expiresAt\":\"2099-01-01\"}";

    private void addToCart(int qty) throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", userToken).contentType(APPLICATION_JSON)
                .content("{\"productId\":\"" + product.getId() + "\",\"quantity\":" + qty + "}"));
    }

    private void saveCoupon(String code, String type, double amount, Instant expires) {
        couponsRepo.save(Coupon.builder().code(code).discountType(type).amount(amount).expiresAt(expires).active(true).build());
    }

    @Test
    void anonymousCreate401() throws Exception {
        mvc.perform(post("/api/coupons").contentType(APPLICATION_JSON).content(COUPON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminCreate403() throws Exception {
        mvc.perform(post("/api/coupons").header("Authorization", userToken).contentType(APPLICATION_JSON).content(COUPON))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreate201() throws Exception {
        mvc.perform(post("/api/coupons").header("Authorization", adminToken).contentType(APPLICATION_JSON).content(COUPON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SAVE10"));
    }

    @Test
    void appliesPercentageCouponToCart() throws Exception {
        addToCart(2); // subtotal 50
        saveCoupon("HALF", "percentage", 50, Instant.now().plusSeconds(86400));
        mvc.perform(post("/api/coupons/apply").header("Authorization", userToken)
                        .contentType(APPLICATION_JSON).content("{\"code\":\"HALF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discount").value(25.0))
                .andExpect(jsonPath("$.total").value(25.0));
    }

    @Test
    void expiredCoupon400() throws Exception {
        saveCoupon("OLD", "fixed", 5, Instant.now().minusSeconds(86400));
        mvc.perform(post("/api/coupons/apply").header("Authorization", userToken)
                        .contentType(APPLICATION_JSON).content("{\"code\":\"OLD\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownCoupon404() throws Exception {
        mvc.perform(post("/api/coupons/apply").header("Authorization", userToken)
                        .contentType(APPLICATION_JSON).content("{\"code\":\"NOPE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void checkoutAppliesStoredCoupon() throws Exception {
        addToCart(2);
        saveCoupon("HALF", "percentage", 50, Instant.now().plusSeconds(86400));
        mvc.perform(post("/api/coupons/apply").header("Authorization", userToken)
                .contentType(APPLICATION_JSON).content("{\"code\":\"HALF\"}"));

        mvc.perform(post("/api/orders/checkout").header("Authorization", userToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(50.0))
                .andExpect(jsonPath("$.discount").value(25.0))
                .andExpect(jsonPath("$.total").value(25.0))
                .andExpect(jsonPath("$.couponCode").value("HALF"));
    }
}
