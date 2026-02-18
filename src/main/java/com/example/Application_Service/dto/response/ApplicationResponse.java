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
public class ApplicationResponse {

    private String id;
    private String userId;
    private Long jobId;
    private JobDto job;
    private String status;
    private String appliedDate;
    private String resumeId;
    private String resumeFileName;  // NEW: For frontend
    private String coverLetter;
    private String createdAt;
    private String updatedAt;
    
    // NEW: Applicant info for employer dashboard
    private String applicantName;
    private String applicantEmail;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobDto {
        private Long id;
        private String title;
        private String company;
        private String companyId;
        private String employerId; // Added this field
        private String logo;
        private String location;
        private String type;
        private String salary;
        private String posted;
        private String description;
        private List<String> responsibilities;
        private List<String> skills;
        private List<String> benefits;
        private String status;
        private String seniority;
    }
    
    // NEW: Applicant details for employer view
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicantDto {
        private String userId;
        private String name;
        private String email;
    }
}
