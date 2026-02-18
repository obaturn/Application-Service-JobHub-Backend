package com.example.Application_Service.service;

import com.example.Application_Service.domain.entity.Application;
import com.example.Application_Service.domain.entity.Job;
import com.example.Application_Service.domain.enums.ApplicationStatus;
import com.example.Application_Service.domain.enums.JobStatus;
import com.example.Application_Service.dto.ApplicationEventData;
import com.example.Application_Service.dto.request.SubmitApplicationRequest;
import com.example.Application_Service.dto.response.ApplicationDetailsResponse;
import com.example.Application_Service.dto.response.ApplicationResponse;
import com.example.Application_Service.dto.response.ApplicationStatsResponse;
import com.example.Application_Service.dto.response.PagedResponse;
import com.example.Application_Service.exception.AlreadyAppliedException;
import com.example.Application_Service.exception.ApplicationNotFoundException;
import com.example.Application_Service.exception.CannotWithdrawException;
import com.example.Application_Service.exception.JobNotFoundException;
import com.example.Application_Service.exception.UnauthorizedAccessException;
import com.example.Application_Service.kafka.ProfileEventProducer;
import com.example.Application_Service.repository.ApplicationRepository;
import com.example.Application_Service.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationService.class);
    
    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final ProfileEventProducer profileEventProducer;
    private final UserProfileService userProfileService;

    public ApplicationService(ApplicationRepository applicationRepository, 
                              JobRepository jobRepository,
                              ProfileEventProducer profileEventProducer,
                              UserProfileService userProfileService) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.profileEventProducer = profileEventProducer;
        this.userProfileService = userProfileService;
    }

    /**
     * Get resume file content by resume ID
     * 
     * @param resumeId The resume ID (assumed to be the filename)
     * @return byte[] of the resume file, or null on failure
     */
    public byte[] getResumeFile(String resumeId) {
        try {
            // First try to get from database by resumeId
            Application application = applicationRepository.findByResumeId(resumeId);
            if (application != null && application.getResumeData() != null) {
                logger.info("Resume found in database for resumeId: {}", resumeId);
                return application.getResumeData();
            }
            
            // Fallback to file system (for backward compatibility)
            // Assuming resumes are stored in /tmp/resumes/
            // You can change this path to your actual resume storage location
            Path resumePath = Paths.get("/tmp/resumes/", resumeId);
            if (Files.exists(resumePath)) {
                return Files.readAllBytes(resumePath);
            } else {
                logger.error("Resume file not found at path: {}", resumePath);
                return null;
            }
        } catch (IOException e) {
            logger.error("Failed to read resume file for resumeId {}: {}", resumeId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Get resume data by application ID
     * 
     * @param applicationId The application ID
     * @return byte[] of the resume file with metadata, or null
     */
    public ResumeData getResumeByApplicationId(String applicationId) {
        Application application = applicationRepository.findById(applicationId).orElse(null);
        if (application == null) {
            logger.error("Application not found: {}", applicationId);
            return null;
        }
        
        if (application.getResumeData() == null) {
            logger.error("No resume data found for application: {}", applicationId);
            return null;
        }
        
        return new ResumeData(
            application.getResumeData(),
            application.getResumeFileName(),
            application.getResumeContentType()
        );
    }
    
    /**
     * Record class for resume data with metadata
     */
    public record ResumeData(byte[] data, String fileName, String contentType) {}

    /**
     * Get resume URL by resume ID
     * 
     * @param resumeId The resume ID
     * @return Resume URL for downloading
     */
    public String getResumeUrl(String resumeId) {
        // This method is likely no longer needed if we're serving the file directly,
        // but we'll keep it for now to avoid breaking other parts of the code.
        return "/api/v1/applications/resumes/" + resumeId;
    }

    /**
     * Submit a new job application
     */
    @Transactional
    public ApplicationDetailsResponse submitApplication(SubmitApplicationRequest request, String userId) {
        // Check if job exists
        Job job = jobRepository.findById(request.getJobId())
            .orElseThrow(() -> new JobNotFoundException("Job not found with ID: " + request.getJobId()));
        
        // Check if job is active
        if (!"Published".equals(job.getStatus())) {
            throw new IllegalArgumentException("Cannot apply to job that is not active");
        }
        
        // Check if user already applied
        if (applicationRepository.existsByUserIdAndJobId(userId, request.getJobId())) {
            throw new AlreadyAppliedException("User has already applied for this job");
        }
        
        // Get applicant details from User Profile service
        String applicantName = userProfileService.getUserName(userId);
        String applicantEmail = userProfileService.getUserEmail(userId);
        
        // Fallback to request values if profile service fails or returns unknown
        if ((applicantName == null || applicantName.equals("Unknown User")) && request.getApplicantName() != null) {
            applicantName = request.getApplicantName();
        }
        if (applicantEmail == null && request.getApplicantEmail() != null) {
            applicantEmail = request.getApplicantEmail();
        }

        // Create new application
        Application application = new Application();
        application.setId(UUID.randomUUID().toString());
        application.setJobId(request.getJobId());
        application.setUserId(userId);
        application.setCoverLetter(request.getCoverLetter());
        application.setResumeId(request.getResumeId());
        application.setStatus(ApplicationStatus.APPLIED);
        application.setAppliedDate(LocalDate.now());
        application.setApplicantName(applicantName);
        application.setApplicantEmail(applicantEmail);
        
        // Handle resume file upload - store in database
        if (request.getResume() != null && !request.getResume().isEmpty()) {
            try {
                application.setResumeData(request.getResume().getBytes());
                application.setResumeFileName(request.getResume().getOriginalFilename());
                application.setResumeContentType(request.getResume().getContentType());
                // Generate a unique resume ID if not provided
                if (application.getResumeId() == null || application.getResumeId().isEmpty()) {
                    application.setResumeId(UUID.randomUUID().toString());
                }
                logger.info("Resume file uploaded: {}, size: {} bytes", 
                    application.getResumeFileName(), application.getResumeData().length);
            } catch (IOException e) {
                logger.error("Failed to process resume file: {}", e.getMessage());
                throw new RuntimeException("Failed to process resume file", e);
            }
        }
        
        Application savedApplication = applicationRepository.save(application);
        logger.info("Application submitted successfully: {}", savedApplication.getId());
        
        // Prepare event data (but don't publish yet)
        final ApplicationEventData eventData = ApplicationEventData.submittedApplication(
            savedApplication.getId(),
            job.getId(),
            job.getTitle(),
            job.getCompany(),
            job.getCompanyId(),
            job.getEmployerId(),
            userId,
            applicantName,
            applicantEmail,
            request.getResumeId(),
            savedApplication.getAppliedDate().toString()
        );
        
        // Publish to Kafka ONLY after transaction commits successfully
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    profileEventProducer.publishEnhancedApplicationEvent(eventData);
                    logger.info("Kafka event published after successful commit for application: {}", savedApplication.getId());
                } catch (Exception e) {
                    logger.error("Failed to publish Kafka event after commit: {}", e.getMessage());
                }
            }
        });
        
        return mapToDetailsResponse(savedApplication);
    }

    /**
     * Get all applications for a user with pagination
     */
    @Transactional(readOnly = true)
    public PagedResponse<ApplicationResponse> getUserApplications(String userId, int page, int limit, String sortBy, String sortOrder) {
        logger.info("🔍 [DEBUG] Searching for userId: '{}'", userId);
        logger.info("🔍 [DEBUG] userId length: {}", userId.length());

        Sort sort = Sort.by(Sort.Direction.fromString(sortOrder), sortBy);
        // Fix: Spring Data JPA pages are 0-indexed, but frontend sends 1-indexed pages
        int pageNumber = page > 0 ? page - 1 : 0;
        Pageable pageable = PageRequest.of(pageNumber, limit, sort);
        
        Page<Application> applications = applicationRepository.findByUserId(userId, pageable);
        
        logger.info("🔍 [DEBUG] Found {} applications", applications.getTotalElements());

        List<ApplicationResponse> applicationResponses = applications.getContent().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
        
        PagedResponse.PaginationInfo paginationInfo = PagedResponse.PaginationInfo.builder()
            .page(applications.getNumber() + 1) // Return 1-indexed page to frontend
            .limit(applications.getSize())
            .total(applications.getTotalElements())
            .totalPages(applications.getTotalPages())
            .build();
        
        return PagedResponse.<ApplicationResponse>builder()
            .applications(applicationResponses)
            .pagination(paginationInfo)
            .build();
    }

    /**
     * Get all applications for a specific job (for employer)
     */
    @Transactional(readOnly = true)
    public PagedResponse<ApplicationResponse> getApplicationsByJobId(Long jobId, String status, int page, int limit, String sortBy, String sortOrder) {
        Sort sort = Sort.by(Sort.Direction.fromString(sortOrder), sortBy);
        // Fix: Spring Data JPA pages are 0-indexed, but frontend sends 1-indexed pages
        int pageNumber = page > 0 ? page - 1 : 0;
        Pageable pageable = PageRequest.of(pageNumber, limit, sort);
        
        Page<Application> applications;
        if (status != null && !status.isEmpty()) {
            ApplicationStatus statusEnum = ApplicationStatus.valueOf(status.toUpperCase());
            applications = applicationRepository.findByJobIdAndStatus(jobId, statusEnum, pageable);
        } else {
            applications = applicationRepository.findByJobId(jobId, pageable);
        }
        
        List<ApplicationResponse> applicationResponses = applications.getContent().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
        
        PagedResponse.PaginationInfo paginationInfo = PagedResponse.PaginationInfo.builder()
            .page(applications.getNumber() + 1) // Return 1-indexed page to frontend
            .limit(applications.getSize())
            .total(applications.getTotalElements())
            .totalPages(applications.getTotalPages())
            .build();
        
        return PagedResponse.<ApplicationResponse>builder()
            .applications(applicationResponses)
            .pagination(paginationInfo)
            .build();
    }

    /**
     * Get application details by ID
     */
    @Transactional(readOnly = true)
    public ApplicationDetailsResponse getApplicationById(String applicationId, String userId, String userRole) {
        Application application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ApplicationNotFoundException("Application not found with ID: " + applicationId));
        
        // Check if user is the applicant
        boolean isApplicant = application.getUserId().equals(userId);
        
        // Check if user is the employer who owns the job
        boolean isEmployer = false;
        if (!isApplicant) {
            try {
                Job job = jobRepository.findById(application.getJobId())
                    .orElseThrow(() -> new JobNotFoundException("Job not found"));
                isEmployer = job.getEmployerId().equals(userId);
            } catch (Exception e) {
                logger.error("Error verifying employer access: {}", e.getMessage());
            }
        }
        
        // Verify user has access
        if (!isApplicant && !isEmployer) {
            throw new UnauthorizedAccessException("User does not have access to this application");
        }
        
        return mapToDetailsResponse(application);
    }

    /**
     * Withdraw application (only job seeker can withdraw)
     */
    @Transactional
    public ApplicationDetailsResponse withdrawApplication(String applicationId, String userId) {
        Application application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ApplicationNotFoundException("Application not found with ID: " + applicationId));
        
        // Verify ownership
        if (!application.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("User does not have permission to withdraw this application");
        }
        
        // Cannot withdraw if already in terminal state
        if (application.getStatus() == ApplicationStatus.REJECTED || 
            application.getStatus() == ApplicationStatus.WITHDRAWN ||
            application.getStatus() == ApplicationStatus.OFFERED) {
            throw new CannotWithdrawException("Cannot withdraw application in status: " + application.getStatus());
        }
        
        application.setStatus(ApplicationStatus.WITHDRAWN);
        application.setUpdatedAt(Instant.now());
        
        // Get job details for the event
        Job job = jobRepository.findById(application.getJobId())
            .orElseThrow(() -> new JobNotFoundException("Job not found"));
        
        Application updatedApplication = applicationRepository.save(application);
        logger.info("Application withdrawn: {}", applicationId);
        
        // Publish enhanced withdrawal event
        try {
            // Fetch applicant details from User Profile service
            String applicantName = userProfileService.getUserName(application.getUserId());
            String applicantEmail = userProfileService.getUserEmail(application.getUserId());
            
            ApplicationEventData eventData = ApplicationEventData.withdrawnApplication(
                application.getId(),
                job.getId(),
                job.getTitle(),
                job.getCompany(),
                job.getCompanyId(),
                job.getEmployerId(),
                application.getUserId(),
                applicantName,
                applicantEmail,
                ApplicationStatus.WITHDRAWN.name()
            );
            
            profileEventProducer.publishEnhancedApplicationEvent(eventData);
        } catch (Exception e) {
            logger.error("Failed to publish enhanced withdrawal event: {}", e.getMessage());
        }
        
        return mapToDetailsResponse(updatedApplication);
    }

    /**
     * Update application status (employer only)
     */
    @Transactional
    public ApplicationDetailsResponse updateStatus(String applicationId, String status, String reason, String employerId) {
        Application application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ApplicationNotFoundException("Application not found with ID: " + applicationId));
        
        // Get the job to verify employer ownership
        Job job = jobRepository.findById(application.getJobId())
            .orElseThrow(() -> new JobNotFoundException("Job not found"));
        
        // Verify employer owns this job
        if (!job.getEmployerId().equals(employerId)) {
            throw new UnauthorizedAccessException("Employer does not have permission to update applications for this job");
        }
        
        ApplicationStatus currentStatus = application.getStatus();
        ApplicationStatus newStatus = ApplicationStatus.valueOf(status.toUpperCase());
        
        // Validate status transition
        validateStatusTransition(currentStatus, newStatus);
        
        application.setStatus(newStatus);
        application.setUpdatedAt(Instant.now());
        
        // Store rejection reason if provided
        if (newStatus == ApplicationStatus.REJECTED && reason != null) {
            application.setRejectionReason(reason);
        }
        
        Application updatedApplication = applicationRepository.save(application);
        logger.info("Application status updated: {} -> {}", applicationId, newStatus);
        
        // Publish enhanced status update event
        try {
            // Fetch applicant details from User Profile service
            String applicantName = userProfileService.getUserName(application.getUserId());
            String applicantEmail = userProfileService.getUserEmail(application.getUserId());
            
            ApplicationEventData eventData = ApplicationEventData.statusUpdatedApplication(
                application.getId(),
                job.getId(),
                job.getTitle(),
                job.getCompany(),
                job.getCompanyId(),
                job.getEmployerId(),
                application.getUserId(),
                applicantName,
                applicantEmail,
                currentStatus.name(),
                newStatus.name()
            );
            
            profileEventProducer.publishEnhancedApplicationEvent(eventData);
        } catch (Exception e) {
            logger.error("Failed to publish enhanced status update event: {}", e.getMessage());
        }
        
        return mapToDetailsResponse(updatedApplication);
    }

    /**
     * Get application statistics for a user
     */
    @Transactional(readOnly = true)
    public ApplicationStatsResponse getApplicationStats(String userId) {
        long totalApplications = applicationRepository.countByUserId(userId);
        
        // Count by status
        List<Object[]> statusCounts = applicationRepository.countByStatusGrouped(userId);
        Map<String, Integer> byStatus = new HashMap<>();
        for (Object[] row : statusCounts) {
            ApplicationStatus status = (ApplicationStatus) row[0];
            Long count = (Long) row[1];
            byStatus.put(status.name(), count.intValue());
        }
        
        // Calculate this week and this month applications
        LocalDate oneWeekAgo = LocalDate.now().minusWeeks(1);
        LocalDate oneMonthAgo = LocalDate.now().minusMonths(1);
        
        long thisWeek = applicationRepository.countByUserIdAndAppliedDateAfter(userId, oneWeekAgo);
        long thisMonth = applicationRepository.countByUserIdAndAppliedDateAfter(userId, oneMonthAgo);
        
        // Calculate interview rate
        Double interviewRate = null;
        if (totalApplications > 0) {
            long interviewCount = byStatus.getOrDefault("INTERVIEW", 0);
            interviewRate = (double) interviewCount / totalApplications * 100;
        }
        
        return ApplicationStatsResponse.builder()
            .total((int) totalApplications)
            .byStatus(byStatus)
            .thisWeek((int) thisWeek)
            .thisMonth((int) thisMonth)
            .interviewRate(interviewRate)
            .build();
    }

    /**
     * Validate status transition
     */
    private void validateStatusTransition(ApplicationStatus current, ApplicationStatus next) {
        // Define valid transitions
        // APPLIED -> IN_REVIEW -> SHORTLISTED -> INTERVIEW -> OFFERED
        // Any status -> REJECTED
        // Cannot transition to WITHDRAWN (only job seeker can withdraw)
        
        if (next == ApplicationStatus.WITHDRAWN) {
            throw new IllegalArgumentException("Cannot set status to WITHDRAWN. Use the withdraw endpoint.");
        }
        
        // Define allowed transitions
        boolean isValid = switch (current) {
            case APPLIED, RESUME_VIEWED -> next == ApplicationStatus.IN_REVIEW || 
                                     next == ApplicationStatus.REJECTED;
            case IN_REVIEW -> next == ApplicationStatus.SHORTLISTED || 
                              next == ApplicationStatus.REJECTED;
            case SHORTLISTED -> next == ApplicationStatus.INTERVIEW || 
                                next == ApplicationStatus.REJECTED;
            case INTERVIEW -> next == ApplicationStatus.OFFERED || 
                              next == ApplicationStatus.REJECTED;
            case OFFERED, REJECTED, WITHDRAWN -> false; // Terminal states
        };
        
        if (!isValid) {
            throw new IllegalArgumentException("Invalid status transition from " + current + " to " + next);
        }
    }

    private ApplicationDetailsResponse mapToDetailsResponse(Application application) {
        // Get job details
        Job job = null;
        try {
            job = jobRepository.findById(application.getJobId()).orElse(null);
        } catch (Exception e) {
            logger.warn("Could not fetch job for application {}: {}", application.getId(), e.getMessage());
        }
        
        return ApplicationDetailsResponse.builder()
            .id(application.getId())
            .userId(application.getUserId())
            .job(job != null ? ApplicationResponse.JobDto.builder()
                .id(job.getId())
                .title(job.getTitle())
                .company(job.getCompany())
                .companyId(job.getCompanyId())
                .employerId(job.getEmployerId()) // Added this line
                .logo(job.getLogo())
                .location(job.getLocation())
                .type(job.getType())
                .salary(job.getSalary())
                .posted(job.getPostedDate() != null ? job.getPostedDate().toString() : null)
                .description(job.getDescription())
                .status(job.getStatus())
                .seniority(job.getSeniority())
                .build() : null)
            .status(application.getStatus() != null ? application.getStatus().name() : null)
            .appliedDate(application.getAppliedDate() != null ? application.getAppliedDate().toString() : null)
            .resumeId(application.getResumeId())
            .resumeFileName(application.getResumeId() != null ? 
                "resume_" + application.getResumeId() + ".pdf" : null)
            .coverLetter(application.getCoverLetter())
            .rejectionReason(application.getRejectionReason())
            .createdAt(application.getCreatedAt() != null ? application.getCreatedAt().toString() : null)
            .updatedAt(application.getUpdatedAt() != null ? application.getUpdatedAt().toString() : null)
            .applicantName(application.getApplicantName() != null ? application.getApplicantName() : "Unknown User")
            .applicantEmail(application.getApplicantEmail())
            .build();
    }

    private ApplicationResponse mapToResponse(Application application) {
        // Get job details
        Job job = null;
        String jobTitle = null;
        String companyName = null;
        try {
            job = jobRepository.findById(application.getJobId()).orElse(null);
            if (job != null) {
                jobTitle = job.getTitle();
                companyName = job.getCompany();
            }
        } catch (Exception e) {
            logger.warn("Could not fetch job for application {}: {}", application.getId(), e.getMessage());
        }
        
        return ApplicationResponse.builder()
            .id(application.getId())
            .userId(application.getUserId())
            .jobId(application.getJobId())
            .job(job != null ? ApplicationResponse.JobDto.builder()
                .id(job.getId())
                .title(job.getTitle())
                .company(job.getCompany())
                .companyId(job.getCompanyId())
                .employerId(job.getEmployerId()) // Added this line
                .logo(job.getLogo())
                .location(job.getLocation())
                .type(job.getType())
                .salary(job.getSalary())
                .posted(job.getPostedDate() != null ? job.getPostedDate().toString() : null)
                .description(job.getDescription())
                .status(job.getStatus())
                .seniority(job.getSeniority())
                .build() : null)
            .status(application.getStatus() != null ? application.getStatus().name() : null)
            .appliedDate(application.getAppliedDate() != null ? application.getAppliedDate().toString() : null)
            .resumeId(application.getResumeId())
            .resumeFileName(application.getResumeId() != null ? 
                "resume_" + application.getResumeId() + ".pdf" : null)
            .coverLetter(application.getCoverLetter())
            .createdAt(application.getCreatedAt() != null ? application.getCreatedAt().toString() : null)
            .updatedAt(application.getUpdatedAt() != null ? application.getUpdatedAt().toString() : null)
            .applicantName(application.getApplicantName() != null ? application.getApplicantName() : "Unknown User")
            .applicantEmail(application.getApplicantEmail())
            .build();
    }
}
