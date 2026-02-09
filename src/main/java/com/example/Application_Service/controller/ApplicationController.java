package com.example.Application_Service.controller;

import com.example.Application_Service.dto.request.SubmitApplicationRequest;
import com.example.Application_Service.dto.response.ApplicationDetailsResponse;
import com.example.Application_Service.dto.response.ApplicationResponse;
import com.example.Application_Service.dto.response.ApplicationStatsResponse;
import com.example.Application_Service.dto.response.PagedResponse;
import com.example.Application_Service.exception.UnauthorizedAccessException;
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

    /**
     * Extracts userId from API Gateway headers.
     * The API Gateway validates JWT tokens and forwards user info via headers.
     * 
     * Expected headers from API Gateway:
     * - X-User-Id: The user's UUID
     * - X-User-Email: The user's email (optional)
     * - X-User-Type: The user's type/role (optional)
     */
    private String extractUserId(HttpServletRequest request) {
        // First try the API Gateway header
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }
        
        // Fallback: Check for user ID in request attribute (set by gateway filter)
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr != null) {
            return userIdAttr.toString();
        }
        
        throw new UnauthorizedAccessException("Missing user context. Ensure API Gateway forwards X-User-Id header.");
    }
}
