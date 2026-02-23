package com.example.Application_Service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitApplicationRequest {

    @NotNull(message = "Job ID is required")
    private Long jobId;

    /**
     * Resume file upload (PDF, DOC, DOCX) - for multipart/form-data
     */
    private MultipartFile resume;

    /**
     * Resume ID (if using external resume service)
     * Use either resume or resumeId
     */
    private String resumeId;

    private String coverLetter;

    private List<QuestionAnswer> answers;

    /**
     * Applicant details for notification events
     */
    private String applicantName;
    private String applicantEmail;

    /**
     * Resume data as base64 encoded string (for JSON requests)
     */
    private String resumeData;

    /**
     * Original filename of the resume (for JSON requests)
     */
    private String resumeFileName;

    /**
     * Content type of the resume (for JSON requests)
     * e.g., application/pdf, application/docx
     */
    private String resumeContentType;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionAnswer {
        private String questionId;
        private String answer;
    }
}
