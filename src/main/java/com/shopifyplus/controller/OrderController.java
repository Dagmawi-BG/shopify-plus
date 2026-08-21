package com.shopifyplus.controller;

import com.shopifyplus.dto.CheckoutRequest;
import com.shopifyplus.dto.OrderStatusRequest;
import com.shopifyplus.dto.PagedResponse;
import com.shopifyplus.model.Order;
import com.shopifyplus.service.OrderService;
import com.shopifyplus.util.Pagination;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/checkout")
    public ResponseEntity<Order> checkout(@AuthenticationPrincipal String userId,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                          @RequestBody(required = false) CheckoutRequest req) {
        var selection = (req == null) ? null : req.toSelection();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.checkout(userId, selection, idempotencyKey));
    }

    @GetMapping
    public PagedResponse<Order> myOrders(@AuthenticationPrincipal String userId,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer limit) {
        int p = Math.max(1, page == null ? 1 : page);
        int l = Math.min(100, Math.max(1, limit == null ? 20 : limit));
        Pageable pageable = PageRequest.of(p - 1, l, Sort.by(Sort.Direction.DESC, "createdAt"));
        return Pagination.toResponse(service.myOrders(userId, pageable));
    }

    @GetMapping("/{id}")
    public Order getById(@AuthenticationPrincipal String userId, Authentication auth, @PathVariable String id) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return service.getById(userId, isAdmin, id);
    }

    @PatchMapping("/{id}/status")
    public Order updateStatus(@PathVariable String id, @Valid @RequestBody OrderStatusRequest req) {
        return service.updateStatus(id, req.status());
    }
}
