package com.example.Application_Service.service;

import com.example.Application_Service.domain.entity.SavedJob;
import com.example.Application_Service.dto.response.PagedResponse;
import com.example.Application_Service.dto.response.SavedJobResponse;
import com.example.Application_Service.dto.response.SavedJobsResponse;
import com.example.Application_Service.exception.JobAlreadySavedException;
import com.example.Application_Service.exception.JobNotFoundException;
import com.example.Application_Service.exception.SavedJobNotFoundException;
import com.example.Application_Service.repository.SavedJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SavedJobService {

    private final SavedJobRepository savedJobRepository;

    @Transactional
    public Map<String, Object> saveJob(Long jobId, String userId) {
        log.info("Saving job {} for user {}", jobId, userId);

        if (savedJobRepository.existsByUserIdAndJobId(userId, jobId)) {
            throw new JobAlreadySavedException("Job is already saved");
        }

        SavedJob savedJob = SavedJob.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .jobId(jobId)
            .savedDate(Instant.now())
            .build();

        SavedJob saved = savedJobRepository.save(savedJob);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Job saved successfully");
        response.put("savedJob", Map.of(
            "id", saved.getId(),
            "userId", saved.getUserId(),
            "jobId", saved.getJobId(),
            "savedDate", saved.getSavedDate().toString()
        ));

        return response;
    }

    @Transactional
    public Map<String, String> unsaveJob(Long jobId, String userId) {
        log.info("Unsaving job {} for user {}", jobId, userId);

        Optional<SavedJob> savedJob = savedJobRepository.findByUserIdAndJobId(userId, jobId);
        
        if (savedJob.isEmpty()) {
            throw new SavedJobNotFoundException("Job is not in saved list");
        }

        savedJobRepository.delete(savedJob.get());

        Map<String, String> response = new HashMap<>();
        response.put("success", "true");
        response.put("message", "Job removed from saved list");

        return response;
    }

    public SavedJobsResponse getSavedJobs(String userId, int page, int limit, String sortBy, String sortOrder) {
        log.info("Fetching saved jobs for user {}", userId);

        Sort sort = Sort.by(Sort.Direction.fromString(sortOrder), 
            sortBy != null ? sortBy : "savedDate");
        Pageable pageable = PageRequest.of(page - 1, limit, sort);
        
        Page<SavedJob> savedJobs = savedJobRepository.findByUserId(userId, pageable);
        
        List<SavedJobResponse> responses = savedJobs.getContent().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());

        return SavedJobsResponse.builder()
            .savedJobs(responses)
            .pagination(PagedResponse.PaginationInfo.builder()
                .page(page)
                .limit(limit)
                .total(savedJobs.getTotalElements())
                .totalPages(savedJobs.getTotalPages())
                .build())
            .build();
    }

    public Map<String, Long> getSavedJobsCount(String userId) {
        log.info("Fetching saved jobs count for user {}", userId);
        
        Map<String, Long> response = new HashMap<>();
        response.put("count", savedJobRepository.countByUserId(userId));
        
        return response;
    }

    private SavedJobResponse mapToResponse(SavedJob savedJob) {
        return SavedJobResponse.builder()
            .id(savedJob.getJobId())
            .savedDate(savedJob.getSavedDate().toString())
            .build();
    }
}
