package com.shopifyplus.controller;

import com.shopifyplus.dto.AddItemRequest;
import com.shopifyplus.dto.CartResponse;
import com.shopifyplus.dto.PagedResponse;
import com.shopifyplus.dto.UpdateItemRequest;
import com.shopifyplus.model.Cart;
import com.shopifyplus.model.Cart.CartItem;
import com.shopifyplus.service.CartService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService service;

    public CartController(CartService service) {
        this.service = service;
    }

    // GET /api/cart?page=&limit=  — items paginated, totals over the whole cart.
    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal String userId,
                                @CookieValue(value = CartService.GUEST_COOKIE_NAME, required = false) String guestCookie,
                                HttpServletResponse response,
                                @RequestParam(required = false) Integer page,
                                @RequestParam(required = false) Integer limit) {
        String owner = resolveOwner(userId, guestCookie, response);
        Cart cart = service.getOrCreate(owner);
        CartService.Totals t = service.computeTotals(cart);

        List<CartItem> all = cart.getItems();
        int p = Math.max(1, page == null ? 1 : page);
        int l = Math.min(100, Math.max(1, limit == null ? 20 : limit));
        int from = Math.min((p - 1) * l, all.size());
        int to = Math.min(from + l, all.size());
        List<CartItem> pageItems = new ArrayList<>(all.subList(from, to));

        PagedResponse.PageMeta meta = new PagedResponse.PageMeta(
                p, l, all.size(),
                (int) Math.ceil((double) all.size() / l),
                (long) p * l < all.size(), p > 1);

        return new CartResponse(pageItems, t.couponCode(), t.subtotal(), t.discount(), t.total(), meta);
    }

    @PostMapping("/items")
    public CartResponse addItem(@AuthenticationPrincipal String userId,
                                @CookieValue(value = CartService.GUEST_COOKIE_NAME, required = false) String guestCookie,
                                HttpServletResponse response,
                                @Valid @RequestBody AddItemRequest req) {
        String owner = resolveOwner(userId, guestCookie, response);
        return full(service.addItem(owner, req.productId(), req.quantity()));
    }

    @PutMapping("/items/{productId}")
    public CartResponse updateItem(@AuthenticationPrincipal String userId,
                                   @CookieValue(value = CartService.GUEST_COOKIE_NAME, required = false) String guestCookie,
                                   HttpServletResponse response,
                                   @PathVariable String productId,
                                   @Valid @RequestBody UpdateItemRequest req) {
        String owner = resolveOwner(userId, guestCookie, response);
        return full(service.updateItem(owner, productId, req.quantity()));
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@AuthenticationPrincipal String userId,
                                   @CookieValue(value = CartService.GUEST_COOKIE_NAME, required = false) String guestCookie,
                                   HttpServletResponse response,
                                   @PathVariable String productId) {
        String owner = resolveOwner(userId, guestCookie, response);
        return full(service.removeItem(owner, productId));
    }

    // Authenticated users own their cart by user id. Anonymous callers get a stable
    // guest id in an HttpOnly cookie (minted on first touch) so their cart survives requests.
    private String resolveOwner(String userId, String guestCookie, HttpServletResponse response) {
        // On permitAll routes, Spring Security supplies the "anonymousUser" principal
        // rather than null — treat that as a guest, not a real account.
        if (userId != null && !"anonymousUser".equals(userId)) {
            return userId;
        }
        if (guestCookie != null && !guestCookie.isBlank()) {
            return guestCookie;
        }
        String guestId = "guest_" + UUID.randomUUID();
        Cookie cookie = new Cookie(CartService.GUEST_COOKIE_NAME, guestId);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge((int) Duration.ofDays(30).toSeconds());
        response.addCookie(cookie);
        return guestId;
    }

    private CartResponse full(Cart cart) {
        CartService.Totals t = service.computeTotals(cart);
        return new CartResponse(cart.getItems(), t.couponCode(), t.subtotal(), t.discount(), t.total(), null);
    }
}
