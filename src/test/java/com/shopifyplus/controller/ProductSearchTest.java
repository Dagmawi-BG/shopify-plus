package com.shopifyplus.controller;

import com.shopifyplus.model.Product;
import com.shopifyplus.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductSearchTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ProductRepository products;

    @BeforeEach
    void setup() {
        products.deleteAll();
        products.save(Product.builder().name("Wireless Mouse").price(20).category("electronics")
                .stock(5).description("ergonomic bluetooth mouse").build());
        products.save(Product.builder().name("Mechanical Keyboard").price(80).category("electronics")
                .stock(3).description("rgb gaming keyboard with blue switches").build());
        products.save(Product.builder().name("Coffee Mug").price(8).category("kitchen")
                .stock(50).description("ceramic mug").build());
    }

    @Test
    void searchMatchesProductName() throws Exception {
        mvc.perform(get("/api/products").param("search", "mouse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Wireless Mouse"));
    }

    @Test
    void searchMatchesDescriptionTerm() throws Exception {
        mvc.perform(get("/api/products").param("search", "gaming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Mechanical Keyboard"));
    }

    @Test
    void searchCanBeNarrowedByCategory() throws Exception {
        // "mug" matches the Coffee Mug, but restricting to electronics yields nothing.
        mvc.perform(get("/api/products").param("search", "mug").param("category", "electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void searchWithNoMatchReturnsEmpty() throws Exception {
        mvc.perform(get("/api/products").param("search", "nonexistentxyzzy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
