package com.shopifyplus.service;

import com.shopifyplus.dto.AuthResponse;
import com.shopifyplus.exception.NotFoundException;
import com.shopifyplus.model.User;
import com.shopifyplus.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    public AuthResponse.PublicUser updateRole(String id, String role) {
        User user = users.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setRole(role);
        users.save(user);
        return AuthResponse.PublicUser.from(user);
    }
}
