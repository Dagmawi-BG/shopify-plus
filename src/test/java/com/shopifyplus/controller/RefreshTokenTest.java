package com.shopifyplus.controller;

import com.shopifyplus.repository.RefreshTokenRepository;
import com.shopifyplus.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    RefreshTokenRepository refreshTokens;

    @BeforeEach
    void setup() {
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private String field(String json, String name) {
        String needle = "\"" + name + "\":\"";
        int i = json.indexOf(needle) + needle.length();
        return json.substring(i, json.indexOf('"', i));
    }

    private String register() throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"username\":\"ana\",\"email\":\"ana@x.com\",\"password\":\"secret12\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void refreshRotatesTokenAndOldOneStopsWorking() throws Exception {
        String refresh = field(register(), "refreshToken");

        String refreshed = mvc.perform(post("/api/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn().getResponse().getContentAsString();

        String rotated = field(refreshed, "refreshToken");
        Assertions.assertNotEquals(refresh, rotated, "refresh token must rotate on use");

        // Reusing the now-revoked original refresh token is rejected.
        mvc.perform(post("/api/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());

        // The rotated one still works.
        mvc.perform(post("/api/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotated + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String refresh = field(register(), "refreshToken");

        mvc.perform(post("/api/auth/logout").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRefreshTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"not-a-real-token\"}"))
                .andExpect(status().isUnauthorized());
    }
}
