package com.example.Application_Service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDetailsResponse {

    private String id;
    private String userId;
    private ApplicationResponse.JobDto job;
    private String status;
    private String appliedDate;
    private String resumeId;
    private String resumeFileName;  // NEW: For frontend
    private String coverLetter;
    private String rejectionReason;
    private List<TimelineItem> timeline;
    private String employerNotes;
    private InterviewDetails interviewDetails;
    private String createdAt;
    private String updatedAt;
    
    // NEW: Applicant info for employer dashboard
    private String applicantName;
    private String applicantEmail;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineItem {
        private String status;
        private String timestamp;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterviewDetails {
        private String scheduledDate;
        private String location;
        private String type;
        private String meetingLink;
    }
}
