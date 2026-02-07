package com.example.Application_Service.service;

import com.example.Application_Service.domain.entity.Job;
import com.example.Application_Service.domain.entity.JobRecommendation;
import com.example.Application_Service.domain.entity.RecommendationCache;
import com.example.Application_Service.domain.entity.RecommendationFeedback;
import com.example.Application_Service.domain.enums.RecommendationFeedbackType;
import com.example.Application_Service.dto.UserProfileDto;
import com.example.Application_Service.dto.request.RecommendationFeedbackRequest;
import com.example.Application_Service.dto.response.PagedResponse;
import com.example.Application_Service.dto.response.RecommendationResponse;
import com.example.Application_Service.repository.JobRepository;
import com.example.Application_Service.repository.RecommendationCacheRepository;
import com.example.Application_Service.repository.RecommendationFeedbackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RecommendationService {

    private final JobRepository jobRepository;
    private final RecommendationCacheRepository cacheRepository;
    private final RecommendationFeedbackRepository feedbackRepository;
    private final RestTemplate restTemplate;

    @Value("${auth.service.url:http://localhost:8083}")
    private String authServiceUrl;

    private static final int MAX_CACHE_DURATION_HOURS = 1;
    private static final int MIN_MATCH_THRESHOLD = 30;

    public RecommendationService(JobRepository jobRepository,
                                RecommendationCacheRepository cacheRepository,
                                RecommendationFeedbackRepository feedbackRepository) {
        this.jobRepository = jobRepository;
        this.cacheRepository = cacheRepository;
        this.feedbackRepository = feedbackRepository;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Fetch user profile from Auth Service via REST API
     * Calls: GET {authServiceUrl}/api/v1/auth/profile/full/{userId}
     */
    public UserProfileDto fetchUserProfile(String userId) {
        try {
            String url = authServiceUrl + "/api/v1/auth/profile/full/" + userId;
            log.info("Fetching user profile from: {}", url);
            
            UserProfileDto profile = restTemplate.getForObject(url, UserProfileDto.class);
            
            if (profile != null) {
                log.info("Successfully fetched profile for user: {}, skills={}, experience={}, education={}",
                    userId,
                    profile.getSkills() != null ? profile.getSkills().size() : 0,
                    profile.getExperience() != null ? profile.getExperience().size() : 0,
                    profile.getEducation() != null ? profile.getEducation().size() : 0);
            }
            
            return profile;
        } catch (Exception e) {
            log.error("Failed to fetch user profile from Auth Service: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Recalculate and cache recommendations for a user after profile change
     * This is called by ProfileEventConsumer when user updates skills/experience/education
     */
    public void recalculateAndCacheRecommendations(String userId, UserProfileDto profile, List<Job> jobs) {
        log.info("Recalculating recommendations for user: {}, jobs={}", userId, jobs.size());

        // Calculate recommendations using PRD algorithm
        List<JobRecommendation> recommendations = calculateRecommendations(profile, jobs);

        log.info("Calculated {} recommendations for user {} (above {}% threshold)", 
            recommendations.size(), userId, MIN_MATCH_THRESHOLD);

        // Clear old cache and save new recommendations
        cacheRepository.deleteByUserId(userId);
        
        for (JobRecommendation rec : recommendations) {
            saveRecommendationToCache(userId, rec);
        }

        log.info("Cached {} recommendations for user {}", recommendations.size(), userId);
    }

    /**
     * Main recommendation calculation method - PRD-aligned algorithm
     * 
     * Algorithm weights (from PRD specification):
     * - User Skills Matching: 40%
     * - Experience Level: 20%
     * - Education: 20%
     * - Location Preference: 15%
     * - Recency: 10%
     */
    public List<JobRecommendation> calculateRecommendations(UserProfileDto profile, List<Job> jobs) {
        if (profile == null || jobs == null || jobs.isEmpty()) {
            return Collections.emptyList();
        }

        return jobs.stream()
            .map(job -> calculateMatchScore(profile, job))
            .filter(rec -> rec.matchScore() >= MIN_MATCH_THRESHOLD)
            .sorted(Comparator.comparing(JobRecommendation::matchScore).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Calculate match score between user profile and job
     */
    private JobRecommendation calculateMatchScore(UserProfileDto profile, Job job) {
        List<String> matchReasons = new ArrayList<>();
        int totalScore = 0;

        // 1. SKILLS MATCHING (40% weight) - PRD requirement
        int skillScore = calculateSkillMatch(profile.getSkills(), job.getSkills(), matchReasons);
        totalScore += skillScore;

        // 2. EXPERIENCE LEVEL (20% weight) - PRD requirement
        int experienceScore = calculateExperienceMatch(profile.getYearsOfExperience(), job.getSeniority(), matchReasons);
        totalScore += experienceScore;

        // 3. EDUCATION (20% weight) - PRD requirement
        int educationScore = calculateEducationMatch(profile.getEducation(), job.getEducationRequired(), matchReasons);
        totalScore += educationScore;

        // 4. LOCATION PREFERENCE (15% weight) - PRD requirement
        int locationScore = calculateLocationMatch(profile.getLocation(), job.getIsRemote(), profile.getOpenToRemote(), job.getLocation(), matchReasons);
        totalScore += locationScore;

        // 5. RECENCY (10% weight) - Bonus for recent jobs
        int recencyScore = calculateRecencyScore(job.getPostedDate(), matchReasons);
        totalScore += Math.min(recencyScore, 10);

        return new JobRecommendation(job, Math.min(totalScore, 100), matchReasons);
    }

    /**
     * Calculate skill matching score (40% of total)
     * Matches user's skills against job's required skills
     */
    private int calculateSkillMatch(List<UserProfileDto.SkillDto> userSkills, List<String> requiredSkills, List<String> reasons) {
        if (requiredSkills == null || requiredSkills.isEmpty()) {
            reasons.add("No specific skills required");
            return 40;
        }

        if (userSkills == null || userSkills.isEmpty()) {
            reasons.add("No skills on profile");
            return 0;
        }

        // Get user skill names in lowercase
        Set<String> userSkillNames = userSkills.stream()
            .map(skill -> skill.getName() != null ? skill.getName().toLowerCase() : "")
            .filter(name -> !name.isEmpty())
            .collect(Collectors.toSet());

        // Count matched skills
        long matchedSkills = requiredSkills.stream()
            .filter(req -> req != null && userSkillNames.contains(req.toLowerCase()))
            .count();

        double matchRatio = (double) matchedSkills / requiredSkills.size();
        int score = (int) (matchRatio * 40);

        reasons.add(String.format("Skills match: %d/%d required (%.0f%%)", 
            matchedSkills, requiredSkills.size(), matchRatio * 100));

        return score;
    }

    /**
     * Calculate experience matching score (20% of total)
     * PRD definition:
     * - Entry: 0-2 years
     * - Mid: 2-5 years
     * - Senior: 5-10 years
     * - Lead: 10+ years
     */
    private int calculateExperienceMatch(Integer userYearsOfExperience, String jobSeniority, List<String> reasons) {
        if (jobSeniority == null || jobSeniority.isEmpty()) {
            reasons.add("No experience level specified");
            return 20;
        }

        int years = userYearsOfExperience != null ? userYearsOfExperience : 0;
        String seniority = jobSeniority.toLowerCase();

        int score = switch (seniority) {
            case "entry" -> years <= 2 ? 20 : years <= 5 ? 15 : years <= 8 ? 10 : 5;
            case "mid" -> years >= 2 && years <= 5 ? 20 : 
                          years > 5 && years <= 10 ? 15 : 
                          years > 10 ? 12 : 8;
            case "senior" -> years >= 5 && years <= 10 ? 20 : 
                            years > 10 ? 18 : 6;
            case "lead", "executive" -> years >= 10 ? 20 : 
                                       years >= 5 ? 12 : 5;
            default -> 10;
        };

        reasons.add(String.format("Experience level: %s (%d years)", jobSeniority, years));
        return score;
    }

    /**
     * Calculate education matching score (20% of total)
     */
    private int calculateEducationMatch(List<UserProfileDto.EducationDto> userEducation, String educationRequired, List<String> reasons) {
        if (educationRequired == null || educationRequired.isEmpty()) {
            reasons.add("No education requirements");
            return 20;
        }

        if (userEducation == null || userEducation.isEmpty()) {
            reasons.add("No education on profile");
            return 0;
        }

        boolean hasDegree = userEducation.stream()
            .anyMatch(edu -> edu.getDegree() != null && !edu.getDegree().isEmpty());

        if (hasDegree) {
            reasons.add("Education requirements met");
            return 20;
        } else {
            reasons.add("Missing education requirements");
            return 0;
        }
    }

    /**
     * Calculate location matching score (15% of total)
     */
    private int calculateLocationMatch(String userLocation, Boolean jobIsRemote, Boolean userOpenToRemote, String jobLocation, List<String> reasons) {
        if (Boolean.TRUE.equals(jobIsRemote) && Boolean.TRUE.equals(userOpenToRemote)) {
            reasons.add("Remote position (matches your preference)");
            return 15;
        }

        if (Boolean.TRUE.equals(jobIsRemote) && !Boolean.TRUE.equals(userOpenToRemote)) {
            reasons.add("Remote position");
            return 8;
        }

        if (userLocation != null && jobLocation != null && 
            userLocation.equalsIgnoreCase(jobLocation)) {
            reasons.add(String.format("Location match: %s", userLocation));
            return 15;
        }

        if (userLocation != null && jobLocation != null && 
            userLocation.toLowerCase().contains(jobLocation.toLowerCase())) {
            reasons.add(String.format("Location partial match: %s", userLocation));
            return 10;
        }

        reasons.add("Location mismatch");
        return 5;
    }

    /**
     * Calculate recency score (10% of total)
     */
    private int calculateRecencyScore(LocalDate jobPostedDate, List<String> reasons) {
        if (jobPostedDate == null) return 0;
        
        long daysBetween = ChronoUnit.DAYS.between(jobPostedDate, LocalDate.now());
        
        if (daysBetween <= 1) {
            reasons.add("Posted today");
            return 10;
        } else if (daysBetween <= 7) {
            reasons.add("Posted this week");
            return 8;
        } else if (daysBetween <= 14) {
            reasons.add("Posted recently");
            return 5;
        } else {
            return 2;
        }
    }

    /**
     * Save recommendation to cache database
     */
    private void saveRecommendationToCache(String userId, JobRecommendation recommendation) {
        Instant expiresAt = Instant.now().plus(MAX_CACHE_DURATION_HOURS, ChronoUnit.HOURS);
        
        RecommendationCache cache = RecommendationCache.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .jobId(recommendation.job().getId())
            .matchScore(recommendation.matchScore())
            .matchReasons(String.join("; ", recommendation.matchReasons()))
            .expiresAt(expiresAt)
            .build();
        
        cacheRepository.save(cache);
    }

    /**
     * Get recommendations for user (from cache or calculate on-demand)
     */
    public RecommendationResponse getRecommendations(String userId, int limit, int page, boolean refresh) {
        log.info("Fetching recommendations for user {}, limit={}, page={}, refresh={}", 
            userId, limit, page, refresh);

        // For now, use mock data (in production, fetch from cache)
        List<RecommendationResponse.JobRecommendation> recommendations = getMockRecommendations();

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

    private List<RecommendationResponse.JobRecommendation> getMockRecommendations() {
        return Arrays.asList(
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
    }

    /**
     * Refresh recommendations for user
     */
    public Map<String, Object> refreshRecommendations(String userId) {
        log.info("Refreshing recommendations for user {}", userId);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Recommendations refreshed successfully");
        response.put("count", 2);
        response.put("lastUpdated", Instant.now().toString());

        return response;
    }

    /**
     * Record user feedback on recommendations
     */
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
}
