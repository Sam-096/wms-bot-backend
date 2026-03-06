package com.wnsai.wms_bot.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
public class SarvamTimeoutException extends RuntimeException {
    public SarvamTimeoutException(String message) {
        super(message);
    }
}
