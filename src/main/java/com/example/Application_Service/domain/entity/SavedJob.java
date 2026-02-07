package com.example.Application_Service.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "saved_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedJob {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @CreationTimestamp
    @Column(name = "saved_date")
    private Instant savedDate;
}
