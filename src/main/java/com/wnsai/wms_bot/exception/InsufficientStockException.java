package com.wnsai.wms_bot.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String itemName, double available, double requested) {
        super(String.format(
            "Insufficient stock for '%s': available=%.2f, requested=%.2f",
            itemName, available, requested));
    }
    public InsufficientStockException(String message) {
        super(message);
    }
}
