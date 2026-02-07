package com.example.Application_Service.domain.enums;

public enum RecommendationFeedbackType {
    RELEVANT("relevant"),
    NOT_RELEVANT("not_relevant"),
    ALREADY_APPLIED("already_applied"),
    NOT_INTERESTED("not_interested");

    private final String value;

    RecommendationFeedbackType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static RecommendationFeedbackType fromValue(String value) {
        for (RecommendationFeedbackType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown feedback type: " + value);
    }
}
