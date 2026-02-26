package com.example.Application_Service.service;

import com.example.Application_Service.domain.entity.Job;
import com.example.Application_Service.domain.enums.JobStatus;
import com.example.Application_Service.dto.request.CreateJobRequest;
import com.example.Application_Service.dto.response.JobResponse;
import com.example.Application_Service.dto.response.PagedJobsResponse;
import com.example.Application_Service.dto.response.PagedResponse;
import com.example.Application_Service.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository jobRepository;

    @Transactional
    public JobResponse createJob(CreateJobRequest request, String employerId) {
        log.info("Creating job with request: {}", request);

        Job job = Job.builder()
                .title(request.getTitle())
                .company(request.getCompany())
                .companyId(request.getCompanyId())
                .location(request.getLocation())
                .type(request.getType())
                .salary(request.getSalary())
                .description(request.getDescription())
                .responsibilities(request.getResponsibilities())
                .skills(request.getSkills())
                .benefits(request.getBenefits())
                .status(request.getStatus() != null ? request.getStatus() : JobStatus.Published.name())
                .postedDate(LocalDate.now())
                .applicationsCount(0)
                .viewsCount(0)
                .seniority(request.getSeniority())
                .isRemote(request.getIsRemote())
                .educationRequired(request.getEducationRequired())
                .employerId(employerId)
                .build();

        Job saved = jobRepository.save(job);
        log.info("Job created successfully with ID: {}", saved.getId());

        return mapToResponse(saved);
    }

    public PagedJobsResponse getJobsByEmployer(String employerId, String status, int page, int limit, String sortBy, String sortOrder) {
        log.info("Fetching jobs for employer: {} with status: {}", employerId, status);

        Sort sort = Sort.by(Sort.Direction.fromString(sortOrder),
                sortBy != null ? sortBy : "createdAt");
        Pageable pageable = PageRequest.of(page - 1, limit, sort);

        Page<Job> jobs;
        if (status != null && !status.isEmpty()) {
            try {
                JobStatus jobStatus = JobStatus.valueOf(status.toUpperCase());
                jobs = jobRepository.findByEmployerIdAndStatus(employerId, jobStatus.name(), pageable);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status filter: {}", status);
                jobs = jobRepository.findByEmployerId(employerId, pageable);
            }
        } else {
            jobs = jobRepository.findByEmployerId(employerId, pageable);
        }

        List<JobResponse> jobResponses = jobs.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PagedJobsResponse.builder()
                .jobs(jobResponses)
                .pagination(PagedResponse.PaginationInfo.builder()
                        .page(page)
                        .limit(limit)
                        .total(jobs.getTotalElements())
                        .totalPages(jobs.getTotalPages())
                        .build())
                .build();
    }

    public JobResponse getJobById(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + jobId));
        return mapToResponse(job);
    }

    @Transactional
    public JobResponse updateJobStatus(Long jobId, String status, String employerId) {
        log.info("Updating job {} status to {} for employer: {}", jobId, status, employerId);

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + jobId));

        if (!job.getEmployerId().equals(employerId)) {
            throw new RuntimeException("Not authorized to update this job");
        }

        job.setStatus(status);
        Job updated = jobRepository.save(job);

        return mapToResponse(updated);
    }

    public long countJobsByEmployer(String employerId) {
        return jobRepository.countByEmployerId(employerId);
    }

    private JobResponse mapToResponse(Job job) {
        return JobResponse.builder()
                .id(job.getId())
                .title(job.getTitle())
                .company(job.getCompany())
                .companyId(job.getCompanyId())
                .location(job.getLocation())
                .type(job.getType())
                .salary(job.getSalary())
                .description(job.getDescription())
                .responsibilities(job.getResponsibilities())
                .skills(job.getSkills())
                .benefits(job.getBenefits())
                .status(job.getStatus())
                .postedDate(job.getPostedDate())
                .applicationsCount(job.getApplicationsCount())
                .viewsCount(job.getViewsCount())
                .seniority(job.getSeniority())
                .isRemote(job.getIsRemote())
                .educationRequired(job.getEducationRequired())
                .employerId(job.getEmployerId())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
