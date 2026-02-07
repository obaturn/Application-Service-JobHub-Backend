package com.example.Application_Service.domain.entity;

import java.util.List;

public record JobRecommendation(
    Job job,
    int matchScore,
    List<String> matchReasons
) {}
