package com.example.Application_Service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitApplicationRequest {

    @NotNull(message = "Job ID is required")
    private Long jobId;

    private String resumeId;

    private String coverLetter;

    private List<QuestionAnswer> answers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionAnswer {
        private String questionId;
        private String answer;
    }
}
