package com.itx.similarproducts.domain.exception;

public class ExistingApiException extends RuntimeException {

    public ExistingApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
