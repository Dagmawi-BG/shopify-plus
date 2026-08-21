package com.shopifyplus.controller;

import com.shopifyplus.dto.ApplyCouponRequest;
import com.shopifyplus.dto.CartResponse;
import com.shopifyplus.dto.CouponRequest;
import com.shopifyplus.model.Coupon;
import com.shopifyplus.service.CouponService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService service;

    public CouponController(CouponService service) {
        this.service = service;
    }

    // POST /api/coupons  (admin)
    @PostMapping
    public ResponseEntity<Coupon> create(@Valid @RequestBody CouponRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    // POST /api/coupons/apply  (any authenticated user)
    @PostMapping("/apply")
    public CartResponse apply(@AuthenticationPrincipal String userId, @Valid @RequestBody ApplyCouponRequest req) {
        return service.apply(userId, req.code());
    }
}
