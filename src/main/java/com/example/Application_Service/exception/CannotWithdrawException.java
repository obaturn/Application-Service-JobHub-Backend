package com.example.Application_Service.exception;

public class CannotWithdrawException extends RuntimeException {

    public CannotWithdrawException(String message) {
        super(message);
    }
}
