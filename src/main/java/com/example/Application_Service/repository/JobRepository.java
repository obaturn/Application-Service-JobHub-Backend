package com.example.Application_Service.repository;

import com.example.Application_Service.domain.entity.Job;
import com.example.Application_Service.domain.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByStatus(String status);
    List<Job> findByStatusAndSeniority(String status, String seniority);
    List<Job> findByStatusAndLocationContainingIgnoreCase(String status, String location);
    
    // Employer queries
    Page<Job> findByEmployerId(String employerId, Pageable pageable);
    Page<Job> findByEmployerIdAndStatus(String employerId, String status, Pageable pageable);
    List<Job> findByEmployerId(String employerId);
    long countByEmployerId(String employerId);
}
