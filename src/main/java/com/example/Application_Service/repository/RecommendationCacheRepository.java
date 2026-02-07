package com.example.Application_Service.repository;

import com.example.Application_Service.domain.entity.RecommendationCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RecommendationCacheRepository extends JpaRepository<RecommendationCache, String> {
    
    List<RecommendationCache> findByUserIdOrderByMatchScoreDesc(String userId);
    
    @Query("SELECT rc FROM RecommendationCache rc WHERE rc.userId = :userId AND rc.expiresAt > :now ORDER BY rc.matchScore DESC")
    List<RecommendationCache> findValidRecommendations(@Param("userId") String userId, @Param("now") Instant now);
    
    @Modifying
    @Query("DELETE FROM RecommendationCache rc WHERE rc.userId = :userId")
    void deleteByUserId(@Param("userId") String userId);
    
    @Modifying
    @Query("DELETE FROM RecommendationCache rc WHERE rc.expiresAt < :now")
    void deleteExpiredRecommendations(@Param("now") Instant now);
}
