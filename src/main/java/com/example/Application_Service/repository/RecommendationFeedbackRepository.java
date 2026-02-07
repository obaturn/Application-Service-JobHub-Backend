package com.example.Application_Service.repository;

import com.example.Application_Service.domain.entity.RecommendationFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecommendationFeedbackRepository extends JpaRepository<RecommendationFeedback, String> {

    List<RecommendationFeedback> findByUserId(String userId);

    List<RecommendationFeedback> findByUserIdAndJobId(String userId, Long jobId);
}
