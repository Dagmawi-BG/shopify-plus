package com.shopifyplus.controller;

import com.shopifyplus.model.Order;
import com.shopifyplus.repository.OrderRepository;
import com.shopifyplus.security.JwtService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    OrderRepository orders;
    @Autowired
    JwtService jwt;

    static final String WEBHOOK_SECRET = "whsec_test";
    static final String PAYLOAD =
            "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_test_123\"}}}";

    @BeforeEach
    void clean() {
        orders.deleteAll();
    }

    // Build a valid Stripe-Signature header: t=<ts>,v1=<hmacSha256(ts.payload)>
    private static String sign(String payload) throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal((ts + "." + payload).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : raw) hex.append(String.format("%02x", b));
        return "t=" + ts + ",v1=" + hex;
    }

    @Test
    void createIntentReturnsClientSecret() throws Exception {
        String token = "Bearer " + jwt.generate("buyer-1", "user");
        Order order = orders.save(Order.builder()
                .user("buyer-1").items(List.of()).subtotal(20).total(20).status("pending").build());

        mvc.perform(post("/api/payments/create-intent").header("Authorization", token)
                        .contentType(APPLICATION_JSON).content("{\"orderId\":\"" + order.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").exists());
    }

    @Test
    void webhookWithoutSignatureIsRejected400() throws Exception {
        mvc.perform(post("/api/payments/webhook").contentType(APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webhookWithInvalidSignatureIsRejected400() throws Exception {
        mvc.perform(post("/api/payments/webhook").header("Stripe-Signature", "t=123,v1=deadbeef")
                        .contentType(APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webhookWithValidSignatureIsAccepted200() throws Exception {
        mvc.perform(post("/api/payments/webhook").header("Stripe-Signature", sign(PAYLOAD))
                        .contentType(APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true));
    }

    @Test
    void webhookMarksMatchingOrderPaid() throws Exception {
        Order order = orders.save(Order.builder()
                .user("u").items(List.of()).total(10).status("pending").paymentIntentId("pi_test_123").build());

        mvc.perform(post("/api/payments/webhook").header("Stripe-Signature", sign(PAYLOAD))
                        .contentType(APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk());

        Assertions.assertEquals("paid", orders.findById(order.getId()).orElseThrow().getStatus());
    }
}
