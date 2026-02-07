package com.example.Application_Service.exception;

public class JobAlreadySavedException extends RuntimeException {

    public JobAlreadySavedException(String message) {
        super(message);
    }
}
