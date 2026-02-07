package com.example.Application_Service.domain.entity;

import com.example.Application_Service.domain.enums.RecommendationFeedbackType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "recommendation_feedback")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationFeedback {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback", nullable = false)
    private RecommendationFeedbackType feedback;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}
