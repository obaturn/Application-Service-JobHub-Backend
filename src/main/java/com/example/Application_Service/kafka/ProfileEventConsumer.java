package com.example.Application_Service.kafka;

import com.example.Application_Service.domain.entity.Job;
import com.example.Application_Service.domain.enums.JobStatus;
import com.example.Application_Service.dto.UserProfileDto;
import com.example.Application_Service.repository.JobRepository;
import com.example.Application_Service.service.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Kafka consumer that listens to profile change events from Auth Service
 * and recalculates job recommendations accordingly.
 * 
 * Events consumed:
 * - SkillAddedEvent, SkillUpdatedEvent, SkillDeletedEvent
 * - ExperienceAddedEvent, ExperienceUpdatedEvent, ExperienceDeletedEvent
 * - EducationAddedEvent, EducationUpdatedEvent, EducationDeletedEvent
 */
@Service
public class ProfileEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ProfileEventConsumer.class);
    private static final String PROFILE_CHANGES_TOPIC = "profile-changes";

    private final RecommendationService recommendationService;
    private final JobRepository jobRepository;

    public ProfileEventConsumer(RecommendationService recommendationService, JobRepository jobRepository) {
        this.recommendationService = recommendationService;
        this.jobRepository = jobRepository;
    }

    /**
     * Listen to profile change events from Auth Service
     * This consumer handles all profile-related events (Skills, Experience, Education)
     */
    @KafkaListener(
        topics = PROFILE_CHANGES_TOPIC,
        groupId = "recommendation-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeProfileChangeEvent(Map<String, Object> event) {
        try {
            String eventType = (String) event.get("eventType");
            String entityType = (String) event.get("entityType");
            String userId = event.get("userId").toString();

            logger.info("Received profile change event: type={}, entity={}, userId={}", 
                eventType, entityType, userId);

            // Validate event type
            if (eventType == null || entityType == null) {
                logger.warn("Invalid event received: missing eventType or entityType");
                return;
            }

            // Only process CREATE, UPDATE, DELETE events (skip READ events)
            if (!eventType.endsWith("_ADDED") && 
                !eventType.endsWith("_UPDATED") && 
                !eventType.endsWith("_DELETED")) {
                logger.debug("Skipping non-modification event: {}", eventType);
                return;
            }

            // Process based on entity type
            switch (entityType) {
                case "SKILL" -> processSkillEvent(event, eventType, userId);
                case "EXPERIENCE" -> processExperienceEvent(event, eventType, userId);
                case "EDUCATION" -> processEducationEvent(event, eventType, userId);
                default -> logger.warn("Unknown entity type: {}", entityType);
            }

        } catch (Exception e) {
            logger.error("Error processing profile change event: {}", e.getMessage(), e);
        }
    }

    /**
     * Process skill-related events
     * Fields from SkillAddedEvent, SkillUpdatedEvent, SkillDeletedEvent:
     * - eventType: SKILL_ADDED, SKILL_UPDATED, SKILL_DELETED
     * - entityType: SKILL
     * - entityId: UUID
     * - entityName: String (skill name)
     * - category: String
     * - proficiencyLevel: String
     * - yearsOfExperience: Integer
     */
    private void processSkillEvent(Map<String, Object> event, String eventType, String userId) {
        String entityName = (String) event.get("entityName");
        String category = (String) event.get("category");
        
        logger.info("Processing {} event for user {}: skill={}, category={}", 
            eventType, userId, entityName, category);
        
        triggerRecommendationRecalculation(userId);
    }

    /**
     * Process experience-related events
     * Fields from ExperienceAddedEvent, ExperienceUpdatedEvent, ExperienceDeletedEvent:
     * - eventType: EXPERIENCE_ADDED, EXPERIENCE_UPDATED, EXPERIENCE_DELETED
     * - entityType: EXPERIENCE
     * - entityId: UUID
     * - entityName: String (job title)
     * - companyName: String
     * - jobTitle: String
     * - location: String
     * - isRemote: boolean
     * - startDate: Instant
     * - endDate: Instant
     * - isCurrentPosition: boolean
     * - employmentType: String
     */
    private void processExperienceEvent(Map<String, Object> event, String eventType, String userId) {
        String entityName = (String) event.get("entityName");
        String companyName = (String) event.get("companyName");
        String jobTitle = (String) event.get("jobTitle");
        String location = (String) event.get("location");
        Boolean isRemote = (Boolean) event.get("isRemote");
        
        logger.info("Processing {} event for user {}: job={} at {}", 
            eventType, userId, jobTitle, companyName);
        
        triggerRecommendationRecalculation(userId);
    }

    /**
     * Process education-related events
     * Fields from EducationAddedEvent, EducationUpdatedEvent, EducationDeletedEvent:
     * - eventType: EDUCATION_ADDED, EDUCATION_UPDATED, EDUCATION_DELETED
     * - entityType: EDUCATION
     * - entityId: UUID
     * - entityName: String (degree + field of study)
     * - institutionName: String
     * - degree: String
     * - fieldOfStudy: String
     * - location: String
     * - gpa: Double
     * - isCurrent: boolean
     */
    private void processEducationEvent(Map<String, Object> event, String eventType, String userId) {
        String entityName = (String) event.get("entityName");
        String institutionName = (String) event.get("institutionName");
        String degree = (String) event.get("degree");
        String fieldOfStudy = (String) event.get("fieldOfStudy");
        
        logger.info("Processing {} event for user {}: {} at {}", 
            eventType, userId, entityName, institutionName);
        
        triggerRecommendationRecalculation(userId);
    }

    /**
     * Trigger recommendation recalculation for a user
     */
    private void triggerRecommendationRecalculation(String userId) {
        try {
            // Fetch user's complete profile from Auth Service
            UserProfileDto userProfile = recommendationService.fetchUserProfile(userId);

            if (userProfile == null) {
                logger.warn("Could not fetch profile for user: {}", userId);
                return;
            }

            // Get all active jobs
            List<Job> activeJobs = jobRepository.findByStatus(JobStatus.Published);

            if (activeJobs.isEmpty()) {
                logger.info("No active jobs found for recommendations");
                return;
            }

            // Recalculate and cache recommendations
            recommendationService.recalculateAndCacheRecommendations(
                userId, 
                userProfile, 
                activeJobs
            );

            logger.info("Recommendations recalculated for user: {} after profile change", userId);

        } catch (Exception e) {
            logger.error("Failed to recalculate recommendations for user {}: {}", 
                userId, e.getMessage(), e);
        }
    }
}
