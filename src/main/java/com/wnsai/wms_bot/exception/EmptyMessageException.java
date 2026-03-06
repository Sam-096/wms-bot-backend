package com.wnsai.wms_bot.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class EmptyMessageException extends RuntimeException {
    public EmptyMessageException() {
        super("Message cannot be empty");
    }
}
