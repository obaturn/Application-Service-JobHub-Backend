package com.example.Application_Service.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "recommendation_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationCache {
    @Id
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "job_id", nullable = false)
    private Long jobId;
    
    @Column(name = "match_score", nullable = false)
    private Integer matchScore;
    
    @Column(name = "match_reasons", columnDefinition = "TEXT")
    private String matchReasons;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
        createdAt = Instant.now();
    }
}
