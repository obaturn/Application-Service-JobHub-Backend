package com.example.Application_Service.repository;

import com.example.Application_Service.domain.entity.Application;
import com.example.Application_Service.domain.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, String> {

    Optional<Application> findByUserIdAndJobId(String userId, Long jobId);

    boolean existsByUserIdAndJobId(String userId, Long jobId);

    Page<Application> findByUserId(String userId, Pageable pageable);

    Page<Application> findByUserIdAndStatusIn(String userId, List<ApplicationStatus> statuses, Pageable pageable);

    long countByUserId(String userId);

    long countByUserIdAndStatusIn(String userId, List<ApplicationStatus> statuses);

    long countByUserIdAndAppliedDateAfter(String userId, LocalDate date);

    @Query("SELECT a.status, COUNT(a) FROM Application a WHERE a.userId = :userId GROUP BY a.status")
    List<Object[]> countByStatusGrouped(@Param("userId") String userId);
}
