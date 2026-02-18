package com.example.Application_Service.kafka;

import com.example.Application_Service.dto.ApplicationEventData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for publishing application-related events.
 * 
 * Events published:
 * - APPLICATION_SUBMITTED: When a user submits a job application
 * - APPLICATION_WITHDRAWN: When a user withdraws their application
 * - APPLICATION_STATUS_UPDATED: When employer updates application status
 *
 * Enhanced events include job details, applicant info, and application data
 * for notification service to send meaningful emails.
 */
@Service
public class ProfileEventProducer {

    private static final Logger logger = LoggerFactory.getLogger(ProfileEventProducer.class);
    private static final String APPLICATION_EVENTS_TOPIC = "application-events";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public ProfileEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publish an application event to Kafka (legacy method)
     * 
     * @param userId The user ID (job seeker)
     * @param jobId The job ID
     * @param eventType The type of event (APPLICATION_SUBMITTED, APPLICATION_WITHDRAWN, APPLICATION_STATUS_UPDATED)
     * @deprecated Use publishEnhancedApplicationEvent() instead for richer event data
     */
    @Deprecated
    public void publishApplicationEvent(String userId, Long jobId, String eventType) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("userId", userId);
        event.put("jobId", jobId);
        event.put("timestamp", System.currentTimeMillis());

        String eventJson = convertToJson(event);
        
        // Use userId as key for partitioning (ensures events for same user go to same partition)
        String key = userId + "-" + jobId;

        CompletableFuture<SendResult<String, String>> future = 
            kafkaTemplate.send(APPLICATION_EVENTS_TOPIC, key, eventJson);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("Published {} event for user {} job {} to partition {} offset {}", 
                    eventType, userId, jobId, 
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            } else {
                logger.error("Failed to publish {} event for user {} job {}: {}", 
                    eventType, userId, jobId, ex.getMessage());
            }
        });
    }
    
    /**
     * Publish an enhanced application event to Kafka with full details.
     * Use this method to include job title, company name, applicant details, etc.
     * 
     * @param eventData The enhanced event data containing all details
     */
    public void publishEnhancedApplicationEvent(ApplicationEventData eventData) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventData.getEventType());
        event.put("applicationId", eventData.getApplicationId());
        event.put("jobId", eventData.getJobId());
        event.put("jobTitle", eventData.getJobTitle());
        event.put("companyName", eventData.getCompanyName());
        event.put("companyId", eventData.getCompanyId());
        event.put("employerId", eventData.getEmployerId());
        event.put("userId", eventData.getUserId());
        event.put("applicantName", eventData.getApplicantName());
        event.put("applicantEmail", eventData.getApplicantEmail());
        event.put("resumeId", eventData.getResumeId());
        event.put("status", eventData.getStatus());
        event.put("appliedDate", eventData.getAppliedDate());
        event.put("timestamp", eventData.getTimestamp() != null ? 
            eventData.getTimestamp().toString() : System.currentTimeMillis());

        String eventJson = convertToJson(event);
        
        // Use userId as key for partitioning (ensures events for same user go to same partition)
        String key = eventData.getUserId() + "-" + eventData.getJobId();

        CompletableFuture<SendResult<String, String>> future = 
            kafkaTemplate.send(APPLICATION_EVENTS_TOPIC, key, eventJson);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("Published enhanced {} event for application {} to partition {} offset {}", 
                    eventData.getEventType(), eventData.getApplicationId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            } else {
                logger.error("Failed to publish enhanced {} event for application {}: {}", 
                    eventData.getEventType(), eventData.getApplicationId(), ex.getMessage());
            }
        });
    }

    /**
     * Simple JSON conversion for events
     * Note: In production, use Jackson/ObjectMapper for proper serialization
     */
    private String convertToJson(Map<String, Object> event) {
        StringBuilder json = new StringBuilder("{");
        int count = 0;
        for (Map.Entry<String, Object> entry : event.entrySet()) {
            if (count > 0) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else {
                json.append(value);
            }
            count++;
        }
        json.append("}");
        return json.toString();
    }
}
