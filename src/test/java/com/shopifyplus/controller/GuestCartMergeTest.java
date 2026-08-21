package com.shopifyplus.controller;

import com.shopifyplus.model.Product;
import com.shopifyplus.repository.CartRepository;
import com.shopifyplus.repository.ProductRepository;
import com.shopifyplus.repository.RefreshTokenRepository;
import com.shopifyplus.repository.UserRepository;
import com.shopifyplus.service.CartService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GuestCartMergeTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ProductRepository products;
    @Autowired
    CartRepository carts;
    @Autowired
    UserRepository users;
    @Autowired
    RefreshTokenRepository refreshTokens;

    Product product;

    @BeforeEach
    void setup() {
        carts.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
        products.deleteAll();
        product = products.save(Product.builder().name("Widget").price(15).category("misc").stock(10).build());
    }

    private String field(String json, String name) {
        String needle = "\"" + name + "\":\"";
        int i = json.indexOf(needle) + needle.length();
        return json.substring(i, json.indexOf('"', i));
    }

    // Adds qty to a guest cart and returns the minted guest cookie value.
    private String guestAdd(int qty) throws Exception {
        MvcResult r = mvc.perform(post("/api/cart/items").contentType(APPLICATION_JSON)
                        .content("{\"productId\":\"" + product.getId() + "\",\"quantity\":" + qty + "}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = r.getResponse().getCookie(CartService.GUEST_COOKIE_NAME);
        Assertions.assertNotNull(cookie, "guest cart cookie must be set");
        return cookie.getValue();
    }

    private String register(String email) throws Exception {
        String json = mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Sam\",\"username\":\"" + email.split("@")[0]
                                + "\",\"email\":\"" + email + "\",\"password\":\"secret12\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return field(json, "token");
    }

    @Test
    void guestCartPersistsAcrossRequestsViaCookie() throws Exception {
        String guestId = guestAdd(2);

        mvc.perform(get("/api/cart").cookie(new Cookie(CartService.GUEST_COOKIE_NAME, guestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void guestCartMergesIntoNewAccountOnRegister() throws Exception {
        String guestId = guestAdd(2);

        String token = mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .cookie(new Cookie(CartService.GUEST_COOKIE_NAME, guestId))
                        .content("{\"name\":\"Sam\",\"username\":\"sam\",\"email\":\"sam@x.com\",\"password\":\"secret12\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = field(token, "token");

        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        Assertions.assertTrue(carts.findByUser(guestId).isEmpty(), "guest cart is removed after merge");
    }

    @Test
    void mergeAddsQuantitiesWhenUserAlreadyHadItem() throws Exception {
        // Existing user already has 1 of the product in their cart.
        String token = register("dana@x.com");
        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"productId\":\"" + product.getId() + "\",\"quantity\":1}"))
                .andExpect(status().isOk());

        // Later, as a guest (e.g. different device), they add 2 more.
        String guestId = guestAdd(2);

        // Logging in with the guest cookie merges: 1 + 2 = 3.
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .cookie(new Cookie(CartService.GUEST_COOKIE_NAME, guestId))
                        .content("{\"email\":\"dana@x.com\",\"password\":\"secret12\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
    }
}
