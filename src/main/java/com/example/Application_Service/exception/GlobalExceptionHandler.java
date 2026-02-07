package com.example.Application_Service.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AlreadyAppliedException.class)
    public ResponseEntity<Map<String, Object>> handleAlreadyApplied(AlreadyAppliedException ex) {
        log.warn("Already applied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(createErrorResponse("ALREADY_APPLIED", ex.getMessage()));
    }

    @ExceptionHandler(ApplicationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleApplicationNotFound(ApplicationNotFoundException ex) {
        log.warn("Application not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(createErrorResponse("APPLICATION_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleJobNotFound(JobNotFoundException ex) {
        log.warn("Job not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(createErrorResponse("JOB_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(JobAlreadySavedException.class)
    public ResponseEntity<Map<String, Object>> handleJobAlreadySaved(JobAlreadySavedException ex) {
        log.warn("Job already saved: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(createErrorResponse("JOB_ALREADY_SAVED", ex.getMessage()));
    }

    @ExceptionHandler(SavedJobNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSavedJobNotFound(SavedJobNotFoundException ex) {
        log.warn("Saved job not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(createErrorResponse("SAVED_JOB_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(CannotWithdrawException.class)
    public ResponseEntity<Map<String, Object>> handleCannotWithdraw(CannotWithdrawException ex) {
        log.warn("Cannot withdraw: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(createErrorResponse("CANNOT_WITHDRAW", ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedAccessException ex) {
        log.warn("Unauthorized access: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(createErrorResponse("FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        Map<String, Object> error = createErrorResponse("VALIDATION_ERROR", "Invalid request parameters");
        error.put("details", ex.getBindingResult().getFieldErrors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(createErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    private Map<String, Object> createErrorResponse(String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("timestamp", Instant.now().toString());
        return error;
    }
}
