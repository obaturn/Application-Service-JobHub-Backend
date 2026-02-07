package com.example.Application_Service.exception;

public class SavedJobNotFoundException extends RuntimeException {

    public SavedJobNotFoundException(String message) {
        super(message);
    }
}
