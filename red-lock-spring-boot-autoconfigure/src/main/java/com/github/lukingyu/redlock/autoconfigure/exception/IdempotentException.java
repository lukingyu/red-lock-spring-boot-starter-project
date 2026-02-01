package com.github.lukingyu.redlock.autoconfigure.exception;

public class IdempotentException extends RuntimeException {
    public IdempotentException(String message) {
        super(message);
    }
}
