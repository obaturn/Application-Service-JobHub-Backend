package com.example.Application_Service.domain.enums;

public enum ApplicationStatus {
    APPLIED("Applied"),
    RESUME_VIEWED("Resume Viewed"),
    IN_REVIEW("In Review"),
    SHORTLISTED("Shortlisted"),
    INTERVIEW("Interview"),
    OFFERED("Offered"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn");

    private final String displayName;

    ApplicationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
