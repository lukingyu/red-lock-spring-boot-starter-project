package com.github.lukingyu.redlock.autoconfigure.exception;

public class IdempotentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdempotentException(String message) {
        super(message);
    }
}
