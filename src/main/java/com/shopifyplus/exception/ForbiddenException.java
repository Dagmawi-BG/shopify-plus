package com.shopifyplus.exception;

// -> 403 (business-level ownership check, distinct from Security's URL rules)
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
