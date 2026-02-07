package com.example.Application_Service.service;

import com.example.Application_Service.domain.entity.RecommendationFeedback;
import com.example.Application_Service.domain.enums.RecommendationFeedbackType;
import com.example.Application_Service.dto.request.RecommendationFeedbackRequest;
import com.example.Application_Service.dto.response.PagedResponse;
import com.example.Application_Service.dto.response.RecommendationResponse;
import com.example.Application_Service.repository.RecommendationFeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final RecommendationFeedbackRepository feedbackRepository;

    // Mock job data for demonstration - in production, this would come from a job service
    private static final List<RecommendationResponse.JobRecommendation> MOCK_JOBS = Arrays.asList(
        RecommendationResponse.JobRecommendation.builder()
            .id(1L)
            .title("Senior Software Engineer")
            .company("Tech Corp")
            .companyId("company-1")
            .location("San Francisco, CA")
            .type("Full-time")
            .salary("$150,000 - $200,000")
            .description("We are looking for a senior software engineer...")
            .requirements(Arrays.asList("5+ years experience", "Java", "Spring Boot"))
            .isRemote(true)
            .experienceLevel("Senior")
            .matchScore(92)
            .matchReasons(Arrays.asList("Skills match: Java, Spring Boot", "Experience level: Senior", "Location preference: Remote"))
            .build(),
        
        RecommendationResponse.JobRecommendation.builder()
            .id(2L)
            .title("Full Stack Developer")
            .company("Startup Inc")
            .companyId("company-2")
            .location("New York, NY")
            .type("Full-time")
            .salary("$120,000 - $160,000")
            .description("Join our growing team...")
            .requirements(Arrays.asList("3+ years experience", "React", "Node.js"))
            .isRemote(true)
            .experienceLevel("Mid")
            .matchScore(87)
            .matchReasons(Arrays.asList("Skills match: React, Node.js", "Location preference: Remote"))
            .build()
    );

    public RecommendationResponse getRecommendations(String userId, int limit, int page, boolean refresh) {
        log.info("Fetching recommendations for user {}", userId);

        // In production, this would:
        // 1. Fetch user profile from user service
        // 2. Calculate match scores based on skills, experience, location
        // 3. Consider user behavior (viewed/saved jobs)
        // 4. Return paginated results

        List<RecommendationResponse.JobRecommendation> recommendations = MOCK_JOBS;

        return RecommendationResponse.builder()
            .recommendations(recommendations)
            .pagination(PagedResponse.PaginationInfo.builder()
                .page(page)
                .limit(limit)
                .total(recommendations.size())
                .totalPages(1)
                .build())
            .lastUpdated(Instant.now().toString())
            .build();
    }

    public Map<String, Object> refreshRecommendations(String userId) {
        log.info("Refreshing recommendations for user {}", userId);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Recommendations refreshed successfully");
        response.put("count", MOCK_JOBS.size());
        response.put("lastUpdated", Instant.now().toString());

        return response;
    }

    @Transactional
    public Map<String, String> recordFeedback(RecommendationFeedbackRequest request, String userId) {
        log.info("Recording feedback for job {} from user {}", request.getJobId(), userId);

        RecommendationFeedback feedback = RecommendationFeedback.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .jobId(request.getJobId())
            .feedback(RecommendationFeedbackType.fromValue(request.getFeedback()))
            .reason(request.getReason())
            .createdAt(Instant.now())
            .build();

        feedbackRepository.save(feedback);

        Map<String, String> response = new HashMap<>();
        response.put("success", "true");
        response.put("message", "Feedback recorded successfully");

        return response;
    }

    /**
     * Calculate match score based on user profile and job requirements
     * In production, this would be more sophisticated
     */
    private int calculateMatchScore(String userId, Long jobId) {
        // Mock implementation - in production:
        // 1. Get user skills from user service
        // 2. Get job requirements from job service
        // 3. Calculate overlap percentage
        // 4. Apply weights for skills (40%), experience (20%), location (15%), etc.
        return 85 + new Random().nextInt(15);
    }

    /**
     * Generate match reasons based on user profile and job
     * In production, this would analyze actual user data
     */
    private List<String> generateMatchReasons(String userId, Long jobId) {
        return Arrays.asList(
            "Skills match: Java, Spring Boot",
            "Experience level matches",
            "Location preference: Remote"
        );
    }
}
