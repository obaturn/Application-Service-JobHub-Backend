package com.example.Application_Service.controller;

import com.example.Application_Service.dto.request.SubmitApplicationRequest;
import com.example.Application_Service.dto.request.UpdateStatusRequest;
import com.example.Application_Service.dto.response.ApplicationDetailsResponse;
import com.example.Application_Service.dto.response.ApplicationResponse;
import com.example.Application_Service.dto.response.ApplicationStatsResponse;
import com.example.Application_Service.dto.response.PagedResponse;
import com.example.Application_Service.exception.UnauthorizedAccessException;
import com.example.Application_Service.service.ApplicationService;
import com.example.Application_Service.service.JobService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@Slf4j
public class ApplicationController {

    private final ApplicationService applicationService;
    private final JobService jobService;

    @PostMapping
    public ResponseEntity<ApplicationDetailsResponse> submitApplication(
        @Valid @RequestBody SubmitApplicationRequest request,
        HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        log.info("Received application submission request from user: {}");
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(applicationService.submitApplication(request, userId));
    }
    
    /**
     * Submit application with resume as base64 encoded string in JSON.
     * This endpoint accepts JSON with the resume file encoded as base64.
     * 
     * Request body should be:
     * {
     *   "jobId": 123,
     *   "coverLetter": "...",
     *   "applicantName": "John Doe",
     *   "applicantEmail": "john@example.com",
     *   "resumeFileName": "resume.pdf",
     *   "resumeContentType": "application/pdf",
     *   "resumeData": "base64encodedstring..."
     * }
     */
    @PostMapping("/json")
    public ResponseEntity<ApplicationDetailsResponse> submitApplicationWithJson(
        @Valid @RequestBody SubmitApplicationRequest request,
        HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        log.info("Received application submission (JSON with resume) from user: {}", userId);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(applicationService.submitApplication(request, userId));
    }
    
    /**
     * Submit application with resume file upload.
     * This endpoint accepts multipart/form-data with the resume file.
     * 
     * Frontend should send:
     * - Content-Type: multipart/form-data
     * - Part "request": JSON object with application details (Content-Type: application/json)
     * - Part "resume": The resume file (optional)
     */
    @PostMapping(value = "/with-resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApplicationDetailsResponse> submitApplicationWithResume(
        @Valid @RequestPart("request") SubmitApplicationRequest request,
        @RequestPart(value = "resume", required = false) org.springframework.web.multipart.MultipartFile resume,
        HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        log.info("Received application submission with resume from user: {}", userId);
        
        // Handle resume file upload
        if (resume != null && !resume.isEmpty()) {
            request.setResume(resume);
            log.info("Resume file received: {}, size: {} bytes, content-type: {}", 
                resume.getOriginalFilename(), resume.getSize(), resume.getContentType());
        } else {
            log.info("No resume file included in the request");
        }
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(applicationService.submitApplication(request, userId));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ApplicationResponse>> getApplications(
        @RequestParam(required = false) Long jobId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String sortOrder,
        HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        log.info("Fetching applications for user: {}, jobId: {}", userId, jobId);
        
        if (jobId != null) {
            // Employer viewing applications for their job
            // Verify employer owns this job
            var job = jobService.getJobById(jobId);
            if (!job.getEmployerId().equals(userId)) {
                throw new UnauthorizedAccessException("Not authorized to view applications for this job");
            }
            
            return ResponseEntity.ok(applicationService.getApplicationsByJobId(
                jobId, status, page, limit, sortBy, sortOrder));
        }
        
        // Job seeker viewing their own applications (status filter not applicable for job seeker)
        return ResponseEntity.ok(applicationService.getUserApplications(
            userId, page, limit, sortBy, sortOrder));
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<ApplicationDetailsResponse> getApplicationDetails(
        @PathVariable String applicationId,
        HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        String userRole = extractUserRole(httpRequest);
        log.info("Fetching application details: {} for user: {}", applicationId, userId);
        
        return ResponseEntity.ok(applicationService.getApplicationById(applicationId, userId, userRole));
    }

    @PutMapping("/{applicationId}/withdraw")
    public ResponseEntity<ApplicationDetailsResponse> withdrawApplication(
        @PathVariable String applicationId,
        @RequestBody(required = false) Map<String, String> body,
        HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        log.info("Withdrawing application: {} for user: {}", applicationId, userId);
        
        return ResponseEntity.ok(applicationService.withdrawApplication(applicationId, userId));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApplicationStatsResponse> getApplicationStats(HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        log.info("Fetching application stats for user: {}", userId);
        
        return ResponseEntity.ok(applicationService.getApplicationStats(userId));
    }

    @PutMapping("/{applicationId}/status")
    public ResponseEntity<ApplicationDetailsResponse> updateApplicationStatus(
        @PathVariable String applicationId,
        @Valid @RequestBody UpdateStatusRequest request,
        HttpServletRequest httpRequest) {
        
        String employerId = extractUserId(httpRequest);
        log.info("Updating application {} status to {} by employer: {}", 
            applicationId, request.getStatus(), employerId);
        
        return ResponseEntity.ok(applicationService.updateStatus(
            applicationId, request.getStatus(), request.getReason(), employerId));
    }
    
    /**
     * View resume for an application.
     * Only the employer who owns the job can view resumes.
     * When resume is viewed, the application status is updated to RESUME_VIEWED
     * and a Kafka event is published to notify the job seeker.
     * 
     * @param applicationId The application ID
     * @param httpRequest HTTP request with user context
     * @return Resume file content for inline viewing
     */
    @GetMapping("/{applicationId}/resume")
    public ResponseEntity<byte[]> viewResume(
        @PathVariable String applicationId,
        HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        log.info("Resume view requested for application: {} by user: {}", applicationId, userId);
        
        // Use the new viewResume method which:
        // 1. Verifies employer owns the job
        // 2. Updates status to RESUME_VIEWED if currently APPLIED
        // 3. Publishes Kafka event for notification
        // 4. Returns the resume data
        ApplicationService.ResumeData resumeData = applicationService.viewResume(applicationId, userId);
        
        if (resumeData != null && resumeData.data() != null) {
            HttpHeaders headers = new HttpHeaders();
            // Use the actual content type from the uploaded file
            if (resumeData.contentType() != null) {
                headers.setContentType(MediaType.parseMediaType(resumeData.contentType()));
            } else {
                headers.setContentType(MediaType.APPLICATION_PDF);
            }
            // Use actual filename or default
            String fileName = resumeData.fileName() != null ? resumeData.fileName() : "resume.pdf";
            headers.setContentDispositionFormData("inline", fileName);
            
            return new ResponseEntity<>(resumeData.data(), headers, HttpStatus.OK);
        }
        
        // If no resume content, return 404
        log.warn("Resume not found for application: {}", applicationId);
        return ResponseEntity.notFound().build();
    }
    
    /**
     * Extracts userId from API Gateway headers.
     * The API Gateway validates JWT tokens and forwards user info via headers.
     * <p>
     * Expected headers from API Gateway:
     * - X-User-Id: The user's UUID
     * - X-User-Email: The user's email (optional)
     * - X-User-Type: The user's type/role (optional)
     */
    private String extractUserId(HttpServletRequest request) {
        // First try the API Gateway header
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }
        
        // Fallback: Check for user ID in request attribute (set by gateway filter)
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr != null) {
            return userIdAttr.toString();
        }
        
        throw new UnauthorizedAccessException("Missing user context. Ensure API Gateway forwards X-User-Id header.");
    }
    
    /**
     * Extracts user role from API Gateway headers.
     */
    private String extractUserRole(HttpServletRequest request) {
        String userRole = request.getHeader("X-User-Type");
        if (userRole != null && !userRole.isEmpty()) {
            return userRole;
        }
        return request.getHeader("X-User-Role");
    }
}
