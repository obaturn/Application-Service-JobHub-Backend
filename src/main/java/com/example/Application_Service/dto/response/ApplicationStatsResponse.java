package com.example.Application_Service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationStatsResponse {

    private int total;
    private Map<String, Integer> byStatus;
    private int thisWeek;
    private int thisMonth;
    private String averageResponseTime;
    private Double interviewRate;
    private Double offerRate;
}
