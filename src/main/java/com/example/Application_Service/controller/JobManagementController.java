package com.example.Application_Service.controller;

import com.example.Application_Service.dto.request.RecommendationFeedbackRequest;
import com.example.Application_Service.dto.response.RecommendationResponse;
import com.example.Application_Service.dto.response.SavedJobsResponse;
import com.example.Application_Service.service.RecommendationService;
import com.example.Application_Service.service.SavedJobService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.Map;




@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobManagementController {

    private final SavedJobService savedJobService;
    private final RecommendationService recommendationService;

    // ============== SAVED JOBS ==============

    @PostMapping("/{jobId}/save")
    public ResponseEntity<Map<String, Object>> saveJob(
        @PathVariable Long jobId,
        HttpServletRequest request) {
        
        String userId = extractUserId(request);
        log.info("Saving job: {} for user: {}", jobId, userId);
        
        return ResponseEntity.ok(savedJobService.saveJob(jobId, userId));
    }

    @DeleteMapping("/{jobId}/unsave")
    public ResponseEntity<Map<String, String>> unsaveJob(
        @PathVariable Long jobId,
        HttpServletRequest request) {
        
        String userId = extractUserId(request);
        log.info("Unsaving job: {} for user: {}", jobId, userId);
        
        return ResponseEntity.ok(savedJobService.unsaveJob(jobId, userId));
    }

    @GetMapping("/saved")
    public ResponseEntity<SavedJobsResponse> getSavedJobs(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(defaultValue = "savedDate") String sortBy,
        @RequestParam(defaultValue = "desc") String sortOrder,
        HttpServletRequest request) {
        
        String userId = extractUserId(request);
        log.info("Fetching saved jobs for user: {}", userId);
        
        return ResponseEntity.ok(savedJobService.getSavedJobs(userId, page, limit, sortBy, sortOrder));
    }

    @GetMapping("/saved/count")
    public ResponseEntity<Map<String, Long>> getSavedJobsCount(HttpServletRequest request) {
        String userId = extractUserId(request);
        log.info("Fetching saved jobs count for user: {}", userId);
        
        return ResponseEntity.ok(savedJobService.getSavedJobsCount(userId));
    }

    // ============== RECOMMENDATIONS ==============

    @GetMapping("/recommendations")
    public ResponseEntity<RecommendationResponse> getRecommendations(
        @RequestParam(defaultValue = "10") int limit,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "false") boolean refresh,
        HttpServletRequest request) {
        
        String userId = extractUserId(request);
        log.info("Fetching recommendations for user: {}", userId);
        
        return ResponseEntity.ok(recommendationService.getRecommendations(userId, limit, page, refresh));
    }

    @GetMapping("/recommendations/refresh")
    public ResponseEntity<Map<String, Object>> refreshRecommendations(HttpServletRequest request) {
        String userId = extractUserId(request);
        log.info("Refreshing recommendations for user: {}", userId);
        
        return ResponseEntity.ok(recommendationService.refreshRecommendations(userId));
    }

    @PostMapping("/recommendations/feedback")
    public ResponseEntity<Map<String, String>> provideFeedback(
        @Valid @RequestBody RecommendationFeedbackRequest request,
        HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        log.info("Recording feedback for job: {} from user: {}", request.getJobId(), userId);
        
        return ResponseEntity.ok(recommendationService.recordFeedback(request, userId));
    }

    private String extractUserId(HttpServletRequest request) {
        // First, try to get userId from API Gateway headers (X-User-Id)
        String userIdFromGateway = request.getHeader("X-User-Id");
        if (userIdFromGateway != null && !userIdFromGateway.isEmpty()) {
            log.debug("Extracted userId from API Gateway header: {}", userIdFromGateway);
            return userIdFromGateway;
        }

        // Fallback: try to extract from Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return extractUserIdFromToken(token);
        }

        throw new UnauthorizedException("Invalid or missing Authorization header and X-User-Id header");
    }

    private String extractUserIdFromToken(String token) {
        try {
            // Decode JWT without signature verification (API Gateway already validated)
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> claims = mapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
                Object userId = claims.get("sub");
                if (userId != null) {
                    return userId.toString();
                }
            }
            throw new UnauthorizedException("Could not extract userId from token");
        } catch (Exception e) {
            log.error("Error extracting user ID from token: {}", e.getMessage());
            throw new UnauthorizedException("Invalid token: " + e.getMessage());
        }
    }


    private static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}
