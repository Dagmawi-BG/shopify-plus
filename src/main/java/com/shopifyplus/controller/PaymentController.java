package com.shopifyplus.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopifyplus.dto.CreateIntentRequest;
import com.shopifyplus.exception.NotFoundException;
import com.shopifyplus.model.Order;
import com.shopifyplus.repository.OrderRepository;
import com.shopifyplus.service.StripeService;
import com.stripe.model.Event;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final StripeService stripe;
    private final OrderRepository orders;
    private final ObjectMapper mapper;

    public PaymentController(StripeService stripe, OrderRepository orders, ObjectMapper mapper) {
        this.stripe = stripe;
        this.orders = orders;
        this.mapper = mapper;
    }

    // POST /api/payments/create-intent  { orderId }
    @PostMapping("/create-intent")
    public Map<String, String> createIntent(@AuthenticationPrincipal String userId,
                                            @Valid @RequestBody CreateIntentRequest req) {
        Order order = orders.findById(req.orderId())
                .filter(o -> o.getUser().equals(userId))
                .orElseThrow(() -> new NotFoundException("Order not found"));

        StripeService.Intent intent = stripe.createPaymentIntent(
                Math.round(order.getTotal() * 100),
                Map.of("orderId", order.getId()));

        order.setPaymentIntentId(intent.id());
        orders.save(order);
        return Map.of("clientSecret", intent.clientSecret());
    }

    // POST /api/payments/webhook  (raw body + signature — public but verified)
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestBody(required = false) String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        String body = payload == null ? "" : payload;
        Event event;
        try {
            event = stripe.constructWebhookEvent(body, signature);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Webhook signature verification failed"));
        }

        if ("payment_intent.succeeded".equals(event.getType())) {
            try {
                JsonNode root = mapper.readTree(body);
                String piId = root.path("data").path("object").path("id").asText(null);
                if (piId != null) {
                    orders.findByPaymentIntentId(piId).ifPresent(o -> {
                        o.setStatus("paid");
                        orders.save(o);
                    });
                }
            } catch (Exception ignored) {
                // malformed payload after a valid signature — acknowledge anyway
            }
        }

        return ResponseEntity.ok(Map.of("received", true));
    }
}
