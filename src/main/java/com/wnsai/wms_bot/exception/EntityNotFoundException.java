package com.wnsai.wms_bot.exception;

public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String entity, Object id) {
        super(entity + " not found with id: " + id);
    }
    public EntityNotFoundException(String message) {
        super(message);
    }
}
