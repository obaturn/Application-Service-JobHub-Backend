package com.example.Application_Service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationFeedbackRequest {

    @NotNull(message = "Job ID is required")
    private Long jobId;

    @NotNull(message = "Feedback type is required")
    private String feedback;

    private String reason;
}
