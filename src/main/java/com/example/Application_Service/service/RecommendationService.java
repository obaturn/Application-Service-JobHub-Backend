package com.example.Application_Service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import com.example.Application_Service.domain.entity.Job;
import com.example.Application_Service.domain.entity.JobRecommendation;
import com.example.Application_Service.domain.entity.RecommendationCache;
import com.example.Application_Service.domain.entity.RecommendationFeedback;
import com.example.Application_Service.domain.enums.JobStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
     * Aggregates data from multiple endpoints:
     * - GET /api/v1/auth/profile (basic info: location, openToRemote, yearsOfExperience)
     * - GET /api/v1/auth/profile/skills (user skills)
     * - GET /api/v1/auth/profile/experience (user experience)
     * - GET /api/v1/auth/profile/education (user education)
     * 
     * @param userId The user ID
     * @param authToken Bearer token for authentication with Auth Service (optional - can be null for public endpoints)
     */
    public UserProfileDto fetchUserProfile(String userId, String authToken) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            UserProfileDto.UserProfileDtoBuilder builder = UserProfileDto.builder();
            
            // Create headers with Bearer token if provided
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            if (authToken != null && !authToken.isEmpty()) {
                headers.set("Authorization", "Bearer " + authToken);
            }
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
            
            // Fetch basic profile (location, openToRemote, yearsOfExperience)
            try {
                String profileUrl = authServiceUrl + "/api/v1/auth/profile";
                log.info("Fetching basic profile from: {}", profileUrl);
                
                org.springframework.http.ResponseEntity<String> profileResponse = restTemplate.exchange(
                    profileUrl, 
                    org.springframework.http.HttpMethod.GET, 
                    entity, 
                    String.class
                );
                String profileJson = profileResponse.getBody();
                JsonNode profileNode = mapper.readTree(profileJson);
                
                if (profileNode.has("location")) {
                    builder.location(profileNode.get("location").asText());
                }
                if (profileNode.has("remotePreference") || profileNode.has("openToRemote")) {
                    JsonNode remoteNode = profileNode.get("remotePreference") != null ? 
                        profileNode.get("remotePreference") : profileNode.get("openToRemote");
                    builder.openToRemote(remoteNode != null && remoteNode.asText().equalsIgnoreCase("yes"));
                }
                if (profileNode.has("yearsOfExperience")) {
                    builder.yearsOfExperience(profileNode.get("yearsOfExperience").asInt());
                }
                log.info("Basic profile: location={}, openToRemote={}, yearsOfExperience={}",
                    builder.build().getLocation(), builder.build().getOpenToRemote(), builder.build().getYearsOfExperience());
            } catch (Exception e) {
                log.warn("Failed to fetch basic profile: {}", e.getMessage());
            }
            
            // Fetch skills
            try {
                String skillsUrl = authServiceUrl + "/api/v1/auth/profile/skills";
                log.info("Fetching skills from: {}", skillsUrl);
                
                org.springframework.http.ResponseEntity<String> skillsResponse = restTemplate.exchange(
                    skillsUrl, 
                    org.springframework.http.HttpMethod.GET, 
                    entity, 
                    String.class
                );
                String skillsJson = skillsResponse.getBody();
                JsonNode skillsArray = mapper.readTree(skillsJson);
                
                List<UserProfileDto.SkillDto> skills = new ArrayList<>();
                for (JsonNode skillNode : skillsArray) {
                    UserProfileDto.SkillDto skillDto = UserProfileDto.SkillDto.builder()
                        .id(skillNode.has("id") ? skillNode.get("id").asText() : null)
                        .name(skillNode.has("name") ? skillNode.get("name").asText() : null)
                        .category(skillNode.has("category") ? skillNode.get("category").asText() : null)
                        .proficiencyLevel(skillNode.has("proficiencyLevel") ? skillNode.get("proficiencyLevel").asText() : null)
                        .yearsOfExperience(skillNode.has("yearsOfExperience") ? skillNode.get("yearsOfExperience").asInt() : null)
                        .build();
                    skills.add(skillDto);
                }
                builder.skills(skills);
                log.info("Fetched {} skills", skills.size());
            } catch (Exception e) {
                log.warn("Failed to fetch skills: {}", e.getMessage());
                builder.skills(Collections.emptyList());
            }
            
            // Fetch experience
            try {
                String expUrl = authServiceUrl + "/api/v1/auth/profile/experience";
                log.info("Fetching experience from: {}", expUrl);
                
                org.springframework.http.ResponseEntity<String> expResponse = restTemplate.exchange(
                    expUrl, 
                    org.springframework.http.HttpMethod.GET, 
                    entity, 
                    String.class
                );
                String expJson = expResponse.getBody();
                JsonNode expArray = mapper.readTree(expJson);
                
                List<UserProfileDto.ExperienceDto> experienceList = new ArrayList<>();
                for (JsonNode expNode : expArray) {
                    UserProfileDto.ExperienceDto expDto = UserProfileDto.ExperienceDto.builder()
                        .id(expNode.has("id") ? expNode.get("id").asText() : null)
                        .companyName(expNode.has("companyName") ? expNode.get("companyName").asText() : null)
                        .jobTitle(expNode.has("jobTitle") ? expNode.get("jobTitle").asText() : null)
                        .location(expNode.has("location") ? expNode.get("location").asText() : null)
                        .isRemote(expNode.has("isRemote") ? expNode.get("isRemote").asBoolean() : null)
                        .isCurrentPosition(expNode.has("isCurrentPosition") ? expNode.get("isCurrentPosition").asBoolean() : null)
                        .employmentType(expNode.has("employmentType") ? expNode.get("employmentType").asText() : null)
                        .build();
                    experienceList.add(expDto);
                }
                builder.experience(experienceList);
                log.info("Fetched {} experience entries", experienceList.size());
            } catch (Exception e) {
                log.warn("Failed to fetch experience: {}", e.getMessage());
                builder.experience(Collections.emptyList());
            }
            
            // Fetch education
            try {
                String eduUrl = authServiceUrl + "/api/v1/auth/profile/education";
                log.info("Fetching education from: {}", eduUrl);
                
                org.springframework.http.ResponseEntity<String> eduResponse = restTemplate.exchange(
                    eduUrl, 
                    org.springframework.http.HttpMethod.GET, 
                    entity, 
                    String.class
                );
                String eduJson = eduResponse.getBody();
                JsonNode eduArray = mapper.readTree(eduJson);
                
                List<UserProfileDto.EducationDto> educationList = new ArrayList<>();
                for (JsonNode eduNode : eduArray) {
                    UserProfileDto.EducationDto eduDto = UserProfileDto.EducationDto.builder()
                        .id(eduNode.has("id") ? eduNode.get("id").asText() : null)
                        .institutionName(eduNode.has("institutionName") ? eduNode.get("institutionName").asText() : null)
                        .degree(eduNode.has("degree") ? eduNode.get("degree").asText() : null)
                        .fieldOfStudy(eduNode.has("fieldOfStudy") ? eduNode.get("fieldOfStudy").asText() : null)
                        .location(eduNode.has("location") ? eduNode.get("location").asText() : null)
                        .gpa(eduNode.has("gpa") && !eduNode.get("gpa").isNull() ? eduNode.get("gpa").asDouble() : null)
                        .build();
                    educationList.add(eduDto);
                }
                builder.education(educationList);
                log.info("Fetched {} education entries", educationList.size());
            } catch (Exception e) {
                log.warn("Failed to fetch education: {}", e.getMessage());
                builder.education(Collections.emptyList());
            }
            
            UserProfileDto profile = builder.build();
            
            log.info("Successfully fetched profile for user: {}, skills={}, experience={}, education={}",
                userId,
                profile.getSkills() != null ? profile.getSkills().size() : 0,
                profile.getExperience() != null ? profile.getExperience().size() : 0,
                profile.getEducation() != null ? profile.getEducation().size() : 0);
            
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
    @Transactional
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
     * 
     * @param userId The user ID
     * @param authToken Bearer token for authentication with Auth Service
     * @param limit Maximum number of recommendations to return
     * @param page Page number (1-indexed)
     * @param refresh Whether to force recalculation of recommendations
     */
    @Transactional
    public RecommendationResponse getRecommendations(String userId, String authToken, int limit, int page, boolean refresh) {
        log.info("Fetching recommendations for user {}, limit={}, page={}, refresh={}", 
            userId, limit, page, refresh);

        // If refresh requested, recalculate recommendations
        if (refresh) {
            refreshRecommendations(userId, authToken);
        }
        
        // Get recommendations from cache with pagination
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("matchScore").descending());
        Page<RecommendationCache> cachedRecs = cacheRepository.findByUserId(userId, pageable);
        
        // If no cached recommendations, calculate and cache them
        if (cachedRecs.isEmpty()) {
            log.info("No cached recommendations for user {}, calculating...", userId);
            UserProfileDto profile = fetchUserProfile(userId, authToken);
            
            if (profile == null) {
                log.warn("⚠️ PROFILE FETCH FAILED for user {} - cannot calculate recommendations", userId);
                log.warn("   Check: 1) Auth Service running on port 8081? 2) User has profile data?");
            } else {
                log.info("✅ Profile fetched successfully for user {}: skills={}, experience={}, education={}, location={}, yearsExp={}",
                    userId,
                    profile.getSkills() != null ? profile.getSkills().size() : 0,
                    profile.getExperience() != null ? profile.getExperience().size() : 0,
                    profile.getEducation() != null ? profile.getEducation().size() : 0,
                    profile.getLocation(),
                    profile.getYearsOfExperience());
                
                List<Job> activeJobs = jobRepository.findByStatus(JobStatus.Published.name());
                log.info("Found {} published jobs in database", activeJobs.size());
                
                if (activeJobs.isEmpty()) {
                    log.warn("⚠️ NO PUBLISHED JOBS in database - cannot generate recommendations");
                    log.warn("   Jobs need status='Published' to be recommended");
                } else {
                    log.info("Calculating recommendations for {} jobs...", activeJobs.size());
                    recalculateAndCacheRecommendations(userId, profile, activeJobs);
                    cachedRecs = cacheRepository.findByUserId(userId, pageable);
                    log.info("✅ Cached {} recommendations for user {} (threshold: {}%)", 
                        cachedRecs.getContent().size(), userId, MIN_MATCH_THRESHOLD);
                }
            }
        } else {
            log.info("Found {} cached recommendations for user {}", cachedRecs.getContent().size(), userId);
        }
        
        // Map cached recommendations to response
        List<RecommendationResponse.JobRecommendation> recommendations = cachedRecs.getContent().stream()
            .map(cache -> {
                Job job = jobRepository.findById(cache.getJobId()).orElse(null);
                if (job == null) return null;
                
                return RecommendationResponse.JobRecommendation.builder()
                    .id(job.getId())
                    .title(job.getTitle())
                    .company(job.getCompany())
                    .companyId(job.getCompanyId())
                    .location(job.getLocation())
                    .type(job.getType())
                    .salary(job.getSalary())
                    .description(job.getDescription())
                    .requirements(job.getResponsibilities())
                    .isRemote(job.getIsRemote())
                    .experienceLevel(job.getSeniority())
                    .matchScore(cache.getMatchScore())
                    .matchReasons(Arrays.asList(cache.getMatchReasons().split("; ")))
                    .build();
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        Instant lastUpdated = cachedRecs.getContent().isEmpty() ? null : 
            cachedRecs.getContent().get(0).getExpiresAt();
        
        return RecommendationResponse.builder()
            .recommendations(recommendations)
            .pagination(PagedResponse.PaginationInfo.builder()
                .page(page)
                .limit(limit)
                .total(cachedRecs.getTotalElements())
                .totalPages(cachedRecs.getTotalPages())
                .build())
            .lastUpdated(lastUpdated != null ? lastUpdated.toString() : Instant.now().toString())
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
     * 
     * @param userId The user ID
     * @param authToken Bearer token for authentication with Auth Service
     */
    public Map<String, Object> refreshRecommendations(String userId, String authToken) {
        log.info("Refreshing recommendations for user {}", userId);
        
        try {
            // Fetch user profile
            UserProfileDto profile = fetchUserProfile(userId, authToken);
            if (profile == null) {
                log.warn("Could not fetch profile for user {}, cannot refresh recommendations", userId);
                Map<String, Object> response = new HashMap<>();
                response.put("message", "Failed to refresh recommendations: profile not found");
                response.put("success", false);
                return response;
            }
            
            // Get all active jobs
            List<Job> activeJobs = jobRepository.findByStatus(JobStatus.Published.name());
            
            if (activeJobs.isEmpty()) {
                log.info("No active jobs found for recommendations");
                Map<String, Object> response = new HashMap<>();
                response.put("message", "No active jobs available");
                response.put("count", 0);
                return response;
            }
            
            // Recalculate and cache recommendations
            recalculateAndCacheRecommendations(userId, profile, activeJobs);
            
            // Get count of cached recommendations
            long count = cacheRepository.findByUserIdOrderByMatchScoreDesc(userId).size();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Recommendations refreshed successfully");
            response.put("count", count);
            response.put("lastUpdated", Instant.now().toString());
            response.put("success", true);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error refreshing recommendations for user {}: {}", userId, e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Failed to refresh recommendations: " + e.getMessage());
            response.put("success", false);
            return response;
        }
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
