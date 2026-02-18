package com.example.Application_Service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for enhanced application events sent to Kafka.
 * Contains all details needed for notification service to send meaningful emails.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationEventData {
    
    /**
     * Type of event: APPLICATION_SUBMITTED, APPLICATION_WITHDRAWN, APPLICATION_STATUS_UPDATED
     */
    private String eventType;
    
    /**
     * The unique application ID
     */
    private String applicationId;
    
    /**
     * Job details
     */
    private Long jobId;
    private String jobTitle;
    private String companyName;
    private String companyId;
    private String employerId;
    
    /**
     * Applicant details
     */
    private String userId;
    private String applicantName;
    private String applicantEmail;
    
    /**
     * Application details
     */
    private String resumeId;
    private String coverLetter;
    private String status;
    private String appliedDate;
    
    /**
     * Additional metadata
     */
    private LocalDateTime timestamp;
    
    /**
     * Factory method for APPLICATION_SUBMITTED event
     */
    public static ApplicationEventData submittedApplication(
            String applicationId,
            Long jobId,
            String jobTitle,
            String companyName,
            String companyId,
            String employerId,
            String userId,
            String applicantName,
            String applicantEmail,
            String resumeId,
            String appliedDate) {
        
        return ApplicationEventData.builder()
                .eventType("APPLICATION_SUBMITTED")
                .applicationId(applicationId)
                .jobId(jobId)
                .jobTitle(jobTitle)
                .companyName(companyName)
                .companyId(companyId)
                .employerId(employerId)
                .userId(userId)
                .applicantName(applicantName)
                .applicantEmail(applicantEmail)
                .resumeId(resumeId)
                .appliedDate(appliedDate)
                .status("APPLIED")
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Factory method for APPLICATION_WITHDRAWN event
     */
    public static ApplicationEventData withdrawnApplication(
            String applicationId,
            Long jobId,
            String jobTitle,
            String companyName,
            String companyId,
            String employerId,
            String userId,
            String applicantName,
            String applicantEmail,
            String status) {
        
        return ApplicationEventData.builder()
                .eventType("APPLICATION_WITHDRAWN")
                .applicationId(applicationId)
                .jobId(jobId)
                .jobTitle(jobTitle)
                .companyName(companyName)
                .companyId(companyId)
                .employerId(employerId)
                .userId(userId)
                .applicantName(applicantName)
                .applicantEmail(applicantEmail)
                .status(status)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Factory method for APPLICATION_STATUS_UPDATED event
     */
    public static ApplicationEventData statusUpdatedApplication(
            String applicationId,
            Long jobId,
            String jobTitle,
            String companyName,
            String companyId,
            String employerId,
            String userId,
            String applicantName,
            String applicantEmail,
            String oldStatus,
            String newStatus) {
        
        return ApplicationEventData.builder()
                .eventType("APPLICATION_STATUS_UPDATED")
                .applicationId(applicationId)
                .jobId(jobId)
                .jobTitle(jobTitle)
                .companyName(companyName)
                .companyId(companyId)
                .employerId(employerId)
                .userId(userId)
                .applicantName(applicantName)
                .applicantEmail(applicantEmail)
                .status(newStatus)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
