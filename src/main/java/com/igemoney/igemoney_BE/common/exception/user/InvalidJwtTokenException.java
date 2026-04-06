package com.igemoney.igemoney_BE.common.exception.user;

public class InvalidJwtTokenException extends RuntimeException {

    public InvalidJwtTokenException(String message, Exception cause) {
        super(message, cause);
    }
}
