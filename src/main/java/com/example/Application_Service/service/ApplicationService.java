package com.example.Application_Service.service;

import com.example.Application_Service.domain.entity.Application;
import com.example.Application_Service.domain.enums.ApplicationStatus;
import com.example.Application_Service.dto.request.SubmitApplicationRequest;
import com.example.Application_Service.dto.response.ApplicationDetailsResponse;
import com.example.Application_Service.dto.response.ApplicationResponse;
import com.example.Application_Service.dto.response.ApplicationStatsResponse;
import com.example.Application_Service.dto.response.PagedResponse;
import com.example.Application_Service.exception.ApplicationNotFoundException;
import com.example.Application_Service.exception.AlreadyAppliedException;
import com.example.Application_Service.exception.CannotWithdrawException;
import com.example.Application_Service.exception.JobNotFoundException;
import com.example.Application_Service.exception.UnauthorizedAccessException;
import com.example.Application_Service.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public ApplicationResponse submitApplication(SubmitApplicationRequest request, String userId) {
        log.info("Submitting application for user {} and job {}", userId, request.getJobId());

        // Check for duplicate application
        if (applicationRepository.existsByUserIdAndJobId(userId, request.getJobId())) {
            throw new AlreadyAppliedException("You have already applied to this job");
        }

        // Create application
        Application application = Application.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .jobId(request.getJobId())
            .status(ApplicationStatus.APPLIED)
            .appliedDate(LocalDate.now())
            .resumeId(request.getResumeId())
            .coverLetter(request.getCoverLetter())
            .build();

        Application saved = applicationRepository.save(application);

        // Send Kafka event
        kafkaTemplate.send("application-events", 
            "application_created", 
            saved.getId() + ":" + userId);

        log.info("Application submitted successfully: {}", saved.getId());

        return mapToResponse(saved, null);
    }

    public PagedResponse<ApplicationResponse> getUserApplications(
        String userId, String status, int page, int limit, String sortBy, String sortOrder) {
        
        log.info("Fetching applications for user {} with status filter: {}", userId, status);

        Sort sort = Sort.by(Sort.Direction.fromString(sortOrder), 
            sortBy != null ? sortBy : "appliedDate");
        Pageable pageable = PageRequest.of(page - 1, limit, sort);
        
        Page<Application> applications;
        
        if (status != null && !status.isEmpty()) {
            List<ApplicationStatus> statuses = Arrays.stream(status.split(","))
                .map(s -> {
                    try {
                        return ApplicationStatus.valueOf(s.trim().toUpperCase().replace(" ", "_"));
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            applications = applicationRepository.findByUserIdAndStatusIn(userId, statuses, pageable);
        } else {
            applications = applicationRepository.findByUserId(userId, pageable);
        }
        
        List<ApplicationResponse> responses = applications.getContent().stream()
            .map(app -> mapToResponse(app, null))
            .collect(Collectors.toList());
        
        return PagedResponse.<ApplicationResponse>builder()
            .applications(responses)
            .pagination(PagedResponse.PaginationInfo.builder()
                .page(page)
                .limit(limit)
                .total(applications.getTotalElements())
                .totalPages(applications.getTotalPages())
                .build())
            .build();
    }

    public ApplicationDetailsResponse getApplicationDetails(String applicationId, String userId) {
        log.info("Fetching application details for id: {}", applicationId);

        Application app = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ApplicationNotFoundException("Application not found"));
        
        if (!app.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("Not authorized to view this application");
        }

        return mapToDetailsResponse(app);
    }

    @Transactional
    public ApplicationResponse withdrawApplication(String applicationId, String reason, String userId) {
        log.info("Withdrawing application {} for user {}", applicationId, userId);

        Application app = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ApplicationNotFoundException("Application not found"));
        
        if (!app.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("Not authorized to withdraw this application");
        }
        
        if (app.getStatus() == ApplicationStatus.OFFERED || 
            app.getStatus() == ApplicationStatus.REJECTED ||
            app.getStatus() == ApplicationStatus.WITHDRAWN) {
            throw new CannotWithdrawException("Cannot withdraw application in current status");
        }
        
        app.setStatus(ApplicationStatus.WITHDRAWN);
        app.setWithdrawnDate(LocalDateTime.now());
        app.setWithdrawReason(reason);
        
        Application saved = applicationRepository.save(app);
        
        return mapToResponse(saved, null);
    }

    public ApplicationStatsResponse getApplicationStats(String userId) {
        log.info("Fetching application stats for user {}", userId);

        ApplicationStatsResponse stats = ApplicationStatsResponse.builder()
            .total((int) applicationRepository.countByUserId(userId))
            .build();

        // Group by status
        Map<String, Integer> byStatus = new HashMap<>();
        List<Object[]> statusGroups = applicationRepository.countByStatusGrouped(userId);
        for (Object[] group : statusGroups) {
            ApplicationStatus status = (ApplicationStatus) group[0];
            long count = ((Number) group[1]).longValue();
            byStatus.put(status.getDisplayName(), (int) count);
        }
        stats.setByStatus(byStatus);
        
        // This week
        LocalDate weekAgo = LocalDate.now().minusDays(7);
        stats.setThisWeek((int) applicationRepository.countByUserIdAndAppliedDateAfter(userId, weekAgo));
        
        // This month
        LocalDate monthAgo = LocalDate.now().minusDays(30);
        stats.setThisMonth((int) applicationRepository.countByUserIdAndAppliedDateAfter(userId, monthAgo));
        
        // Calculate rates
        int total = stats.getTotal();
        if (total > 0) {
            int interviews = byStatus.getOrDefault("Interview", 0);
            int offers = byStatus.getOrDefault("Offered", 0);
            stats.setInterviewRate((interviews * 100.0) / total);
            stats.setOfferRate((offers * 100.0) / total);
        }
        
        return stats;
    }

    private ApplicationResponse mapToResponse(Application app, ApplicationResponse.JobDto jobDto) {
        return ApplicationResponse.builder()
            .id(app.getId())
            .userId(app.getUserId())
            .jobId(app.getJobId())
            .job(jobDto)
            .status(app.getStatus().getDisplayName())
            .appliedDate(app.getAppliedDate().toString())
            .resumeId(app.getResumeId())
            .coverLetter(app.getCoverLetter())
            .createdAt(app.getCreatedAt() != null ? app.getCreatedAt().toString() : null)
            .updatedAt(app.getUpdatedAt() != null ? app.getUpdatedAt().toString() : null)
            .build();
    }

    private ApplicationDetailsResponse mapToDetailsResponse(Application app) {
        return ApplicationDetailsResponse.builder()
            .id(app.getId())
            .userId(app.getUserId())
            .status(app.getStatus().getDisplayName())
            .appliedDate(app.getAppliedDate().toString())
            .resumeId(app.getResumeId())
            .coverLetter(app.getCoverLetter())
            .createdAt(app.getCreatedAt() != null ? app.getCreatedAt().toString() : null)
            .updatedAt(app.getUpdatedAt() != null ? app.getUpdatedAt().toString() : null)
            .build();
    }
}
