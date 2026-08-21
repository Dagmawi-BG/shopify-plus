package com.shopifyplus.service;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class StripeService {

    private final boolean mock;
    private final String webhookSecret;

    public StripeService(
            @Value("${app.stripe.mock:true}") boolean mock,
            @Value("${app.stripe.secret-key:sk_test_dummy}") String secretKey,
            @Value("${app.stripe.webhook-secret:whsec_test}") String webhookSecret) {
        this.mock = mock;
        this.webhookSecret = webhookSecret;
        Stripe.apiKey = secretKey;
    }

    // In mock mode (no real Stripe account), return a fake intent — no network call.
    public Intent createPaymentIntent(long amountCents, Map<String, String> metadata) {
        if (mock) {
            String id = "pi_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String secret = id + "_secret_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            return new Intent(id, secret);
        }
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountCents)
                    .setCurrency("usd")
                    .putAllMetadata(metadata)
                    .build();
            PaymentIntent intent = PaymentIntent.create(params);
            return new Intent(intent.getId(), intent.getClientSecret());
        } catch (StripeException e) {
            throw new RuntimeException("Stripe error: " + e.getMessage(), e);
        }
    }

    // Verifies the signature (pure crypto — no network, no account needed).
    public Event constructWebhookEvent(String payload, String signatureHeader)
            throws SignatureVerificationException {
        return Webhook.constructEvent(payload, signatureHeader, webhookSecret);
    }

    public record Intent(String id, String clientSecret) {
    }
}
