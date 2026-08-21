package com.shopifyplus.repository;

import com.shopifyplus.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {

    Page<Order> findByUser(String user, Pageable pageable);

    Optional<Order> findByPaymentIntentId(String paymentIntentId);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    // Pending orders whose reservation window has lapsed -> stock should be released.
    List<Order> findByStatusAndReservationExpiresAtBefore(String status, Instant time);
}
