package com.shopifyplus.exception;

// Thrown by services when a resource does not exist -> mapped to 404 by the advice.
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
