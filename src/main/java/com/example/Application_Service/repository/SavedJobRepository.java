package com.example.Application_Service.repository;

import com.example.Application_Service.domain.entity.SavedJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SavedJobRepository extends org.springframework.data.jpa.repository.JpaRepository<SavedJob, String> {

    Optional<SavedJob> findByUserIdAndJobId(String userId, Long jobId);

    boolean existsByUserIdAndJobId(String userId, Long jobId);

    Page<SavedJob> findByUserId(String userId, Pageable pageable);

    long countByUserId(String userId);

    void deleteByUserIdAndJobId(String userId, Long jobId);
}
