package com.shopifyplus.service;

import com.shopifyplus.dto.CartResponse;
import com.shopifyplus.dto.CouponRequest;
import com.shopifyplus.exception.BadRequestException;
import com.shopifyplus.exception.NotFoundException;
import com.shopifyplus.model.Cart;
import com.shopifyplus.model.Coupon;
import com.shopifyplus.repository.CouponRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;

@Service
public class CouponService {

    private final CouponRepository coupons;
    private final CartService cartService;

    public CouponService(CouponRepository coupons, CartService cartService) {
        this.coupons = coupons;
        this.cartService = cartService;
    }

    public Coupon create(CouponRequest req) {
        Coupon coupon = Coupon.builder()
                .code(req.code().toUpperCase())
                .discountType(req.discountType())
                .amount(req.amount())
                .expiresAt(req.expiresAt().atStartOfDay(ZoneOffset.UTC).toInstant())
                .active(req.active() == null ? true : req.active())
                .build();
        return coupons.save(coupon);
    }

    // Validates the coupon, stores its CODE on the user's cart, returns the live cart.
    public CartResponse apply(String userId, String code) {
        Coupon coupon = coupons.findByCode(code.toUpperCase())
                .orElseThrow(() -> new NotFoundException("Coupon not found"));

        if (!coupon.isActive() || coupon.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Coupon is expired or inactive");
        }

        Cart cart = cartService.applyCoupon(userId, coupon.getCode());
        return cartService.toResponse(cart);
    }
}
