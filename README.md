# Shopify Plus — E-Commerce Backend API

A test-driven, backend-only e-commerce REST API on a document database. Own JWT identity, Stripe payments, and a stock-reservation checkout.

**Stack:** Java 21 · Spring Boot 3.3 · MongoDB (Spring Data) · Spring Security + JWT (jjwt) · Stripe · springdoc / Swagger UI · JUnit 5 + MockMvc

## Features

- **Products** — CRUD, text **search**, and **pagination**.
- **Carts** — per-user and **guest carts** (cookie), with **guest → user merge** on login.
- **Coupons** — percentage / fixed discounts applied to cart totals.
- **Checkout & orders** — single-transaction ordering with a **stock-reservation** model (hold on checkout, auto-release on expiry via a scheduler), **idempotency key** support, and partial-line selection.
- **Payments** — Stripe (create-intent + webhook) with a **mock mode** for local dev.
- **Auth** — register / login / **refresh tokens**, **rate limiting**, and role-based access (`USER` / `ADMIN`).
- **Money** — amounts kept to 2 decimals via a `Money` rounding helper; the server is the single source of truth for every amount (the client can never dictate a price or discount).

## Architecture highlights

- **Stateless JWT** filter chain; roles carried in the token, resolved to Spring authorities.
- **Optimistic locking** (`@Version`) on products guards concurrent stock updates.
- **Reservation scheduler** returns stock from pending orders that were never paid.
- Centralized `GlobalExceptionHandler` for consistent error responses.

## Getting started

**Prerequisites:** JDK 21, MongoDB, Maven.

1. Start MongoDB (default `mongodb://localhost:27017/shopify-plus-java`; override with `MONGO_URI`).
2. Run the API:
   ```bash
   mvn spring-boot:run
   ```
   Serves at **http://localhost:5000** · API docs at **http://localhost:5000/swagger-ui.html**.

Optional environment overrides: `JWT_SECRET`, `STRIPE_MOCK` (default `true`), `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `RATELIMIT_ENABLED`.

## Tests

```bash
mvn clean test
```
Tests run against a real local MongoDB (`shopify-plus-java-test`), with Stripe in mock mode and rate limiting disabled.

## Configuration secrets

Only `${ENV_VAR}` placeholders with dev-only defaults are committed. Provide real values via environment variables (or a git-ignored `.env`).
