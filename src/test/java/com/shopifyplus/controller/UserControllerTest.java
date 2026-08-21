package com.shopifyplus.controller;

import com.shopifyplus.model.User;
import com.shopifyplus.repository.UserRepository;
import com.shopifyplus.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    JwtService jwt;

    String adminToken;

    @BeforeEach
    void clean() {
        users.deleteAll();
        adminToken = "Bearer " + jwt.generate("admin-1", "admin");
    }

    private String targetId() {
        return users.save(User.builder()
                .name("Target").username("target").email("target@test.com").password("x").role("user").build()).getId();
    }

    @Test
    void adminPromotesUser() throws Exception {
        String id = targetId();
        mvc.perform(patch("/api/users/" + id + "/role").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"role\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("admin"));
    }

    @Test
    void nonAdminIsForbidden403() throws Exception {
        String id = targetId();
        String userToken = "Bearer " + jwt.generate("u1", "user");
        mvc.perform(patch("/api/users/" + id + "/role").header("Authorization", userToken)
                        .contentType(APPLICATION_JSON).content("{\"role\":\"admin\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRejected401() throws Exception {
        String id = targetId();
        mvc.perform(patch("/api/users/" + id + "/role")
                        .contentType(APPLICATION_JSON).content("{\"role\":\"admin\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRole400() throws Exception {
        String id = targetId();
        mvc.perform(patch("/api/users/" + id + "/role").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"role\":\"superuser\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notFound404() throws Exception {
        mvc.perform(patch("/api/users/64b7f3c2e1a2c3d4e5f6a7b8/role").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"role\":\"admin\"}"))
                .andExpect(status().isNotFound());
    }
}
