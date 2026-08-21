package com.shopifyplus.controller;

import com.shopifyplus.dto.PagedResponse;
import com.shopifyplus.dto.ProductRequest;
import com.shopifyplus.dto.ProductUpdateRequest;
import com.shopifyplus.model.Product;
import com.shopifyplus.service.ProductService;
import com.shopifyplus.util.Pagination;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    // POST /api/products  (will be locked to admin once security is added)
    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody ProductRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    // GET /api/products?category=&search=&page=&limit=
    @GetMapping
    public PagedResponse<Product> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit) {
        return Pagination.toResponse(service.list(category, search, Pagination.of(page, limit)));
    }

    // GET /api/products/{id}
    @GetMapping("/{id}")
    public Product getById(@PathVariable String id) {
        return service.getById(id);
    }

    // PUT /api/products/{id}
    @PutMapping("/{id}")
    public Product update(@PathVariable String id, @Valid @RequestBody ProductUpdateRequest req) {
        return service.update(id, req);
    }

    // DELETE /api/products/{id}
    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable String id) {
        service.delete(id);
        return Map.of("message", "Product deleted", "id", id);
    }
}
