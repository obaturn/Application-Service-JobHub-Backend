package com.example.Application_Service.controller;

import com.example.Application_Service.dto.request.SubmitApplicationRequest;
import com.example.Application_Service.dto.response.ApplicationDetailsResponse;
import com.example.Application_Service.dto.response.ApplicationResponse;
import com.example.Application_Service.dto.response.ApplicationStatsResponse;
import com.example.Application_Service.dto.response.PagedResponse;
import com.example.Application_Service.service.ApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@Slf4j
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    public ResponseEntity<ApplicationResponse> submitApplication(
        @Valid @RequestBody SubmitApplicationRequest request,
        HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        log.info("Received application submission request from user: {}", userId);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(applicationService.submitApplication(request, userId));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ApplicationResponse>> getApplications(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(defaultValue = "appliedDate") String sortBy,
        @RequestParam(defaultValue = "desc") String sortOrder,
        HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        log.info("Fetching applications for user: {}", userId);
        
        return ResponseEntity.ok(applicationService.getUserApplications(
            userId, status, page, limit, sortBy, sortOrder));
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<ApplicationDetailsResponse> getApplicationDetails(
        @PathVariable String applicationId,
        HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        log.info("Fetching application details: {} for user: {}", applicationId, userId);
        
        return ResponseEntity.ok(applicationService.getApplicationDetails(applicationId, userId));
    }

    @PutMapping("/{applicationId}/withdraw")
    public ResponseEntity<ApplicationResponse> withdrawApplication(
        @PathVariable String applicationId,
        @RequestBody(required = false) Map<String, String> body,
        HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        String reason = body != null ? body.get("reason") : null;
        log.info("Withdrawing application: {} for user: {}", applicationId, userId);
        
        return ResponseEntity.ok(applicationService.withdrawApplication(applicationId, reason, userId));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApplicationStatsResponse> getApplicationStats(HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        log.info("Fetching application stats for user: {}", userId);
        
        return ResponseEntity.ok(applicationService.getApplicationStats(userId));
    }

    private String extractUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return extractUserIdFromToken(token);
        }
        throw new UnauthorizedException("Invalid or missing Authorization header");
    }

    private String extractUserIdFromToken(String token) {
        // In production, this would use JwtUtils from your auth service
        // For now, we'll return a placeholder
        try {
            // This is a placeholder - in production, use your JwtUtils class
            // String userId = jwtUtils.getUserIdFromToken(token);
            // return userId;
            
            // For testing purposes, return a mock user ID
            return "test-user-123";
        } catch (Exception e) {
            log.error("Error extracting user ID from token: {}", e.getMessage());
            throw new UnauthorizedException("Invalid token");
        }
    }

    private static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}
