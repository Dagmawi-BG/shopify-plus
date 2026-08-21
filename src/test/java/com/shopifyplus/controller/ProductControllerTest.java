package com.shopifyplus.controller;

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
class ProductControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ProductRepository products;

    @Autowired
    JwtService jwt;

    @BeforeEach
    void clean() {
        products.deleteAll();
    }

    private String admin() {
        return "Bearer " + jwt.generate("admin-id", "admin");
    }

    private String user() {
        return "Bearer " + jwt.generate("user-id", "user");
    }

    private static final String SAMPLE = """
            {"name":"Wireless Mouse","price":29.99,"category":"electronics","stock":50}
            """;

    private String createSample() throws Exception {
        return mvc.perform(post("/api/products").header("Authorization", admin())
                        .contentType(APPLICATION_JSON).content(SAMPLE))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String idOf(String json) {
        int i = json.indexOf("\"id\":\"") + 6;
        return json.substring(i, json.indexOf('"', i));
    }

    // ---- RBAC ----

    @Test
    void adminCanCreateProduct() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", admin())
                        .contentType(APPLICATION_JSON).content(SAMPLE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Wireless Mouse"));
    }

    @Test
    void anonymousCreateIsRejected401() throws Exception {
        mvc.perform(post("/api/products").contentType(APPLICATION_JSON).content(SAMPLE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminCreateIsRejected403() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", user())
                        .contentType(APPLICATION_JSON).content(SAMPLE))
                .andExpect(status().isForbidden());
    }

    // ---- Public reads ----

    @Test
    void listsProductsWithPagination() throws Exception {
        createSample();
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));
    }

    @Test
    void filtersByCategory() throws Exception {
        createSample();
        mvc.perform(post("/api/products").header("Authorization", admin()).contentType(APPLICATION_JSON)
                .content("{\"name\":\"T-Shirt\",\"price\":19.99,\"category\":\"apparel\",\"stock\":10}"));
        mvc.perform(get("/api/products?category=apparel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].category").value("apparel"));
    }

    @Test
    void paginatesTheList() throws Exception {
        for (String n : new String[]{"A", "B", "C"}) {
            mvc.perform(post("/api/products").header("Authorization", admin()).contentType(APPLICATION_JSON)
                    .content("{\"name\":\"Item " + n + "\",\"price\":5,\"category\":\"misc\",\"stock\":1}"));
        }
        mvc.perform(get("/api/products?limit=2&page=1"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pagination.totalItems").value(3))
                .andExpect(jsonPath("$.pagination.totalPages").value(2))
                .andExpect(jsonPath("$.pagination.hasNext").value(true));

        mvc.perform(get("/api/products?limit=2&page=2"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.pagination.hasNext").value(false))
                .andExpect(jsonPath("$.pagination.hasPrev").value(true));
    }

    @Test
    void getsProductById() throws Exception {
        String id = idOf(createSample());
        mvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void returns404ForMissingProduct() throws Exception {
        mvc.perform(get("/api/products/64b7f3c2e1a2c3d4e5f6a7b8"))
                .andExpect(status().isNotFound());
    }

    // ---- Admin writes ----

    @Test
    void adminUpdatesProduct() throws Exception {
        String id = idOf(createSample());
        mvc.perform(put("/api/products/" + id).header("Authorization", admin())
                        .contentType(APPLICATION_JSON).content("{\"price\":24.99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(24.99))
                .andExpect(jsonPath("$.name").value("Wireless Mouse"));
    }

    @Test
    void adminDeletesProduct() throws Exception {
        String id = idOf(createSample());
        mvc.perform(delete("/api/products/" + id).header("Authorization", admin()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
    }

    // ---- Validation ----

    @Test
    void rejectsNegativePrice() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", admin()).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"price\":-5,\"category\":\"x\",\"stock\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingName() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", admin()).contentType(APPLICATION_JSON)
                        .content("{\"price\":5,\"category\":\"x\",\"stock\":1}"))
                .andExpect(status().isBadRequest());
    }
}
