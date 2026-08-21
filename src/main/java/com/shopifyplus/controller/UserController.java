package com.shopifyplus.controller;

import com.shopifyplus.dto.AuthResponse;
import com.shopifyplus.dto.RoleRequest;
import com.shopifyplus.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    // PATCH /api/users/{id}/role  { role }  (admin) — Pattern A role management
    @PatchMapping("/{id}/role")
    public AuthResponse.PublicUser updateRole(@PathVariable String id, @Valid @RequestBody RoleRequest req) {
        return service.updateRole(id, req.role());
    }
}
