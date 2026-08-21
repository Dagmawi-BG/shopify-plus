package com.shopifyplus.controller;

import com.shopifyplus.repository.UserRepository;
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
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @BeforeEach
    void clean() {
        users.deleteAll();
    }

    private static final String USER = """
            {"name":"Jane","username":"jane","email":"jane@example.com","password":"secret123"}
            """;

    @Test
    void registersAndReturnsToken() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(USER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.user.role").value("user"))
                .andExpect(jsonPath("$.user.username").value("jane"));
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(USER));
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(USER))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(USER));
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"J2\",\"username\":\"jane\",\"email\":\"jane2@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginsWithValidCredentials() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(USER));
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void rejectsInvalidPassword() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(USER));
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"wrongpass\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnknownEmail() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isUnauthorized());
    }
}
