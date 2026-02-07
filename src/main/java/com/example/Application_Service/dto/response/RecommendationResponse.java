package com.example.Application_Service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {

    private List<JobRecommendation> recommendations;
    private PagedResponse.PaginationInfo pagination;
    private String lastUpdated;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobRecommendation {
        private Long id;
        private String title;
        private String company;
        private String companyId;
        private String location;
        private String type;
        private String salary;
        private String description;
        private List<String> requirements;
        private String postedDate;
        private String companyLogo;
        private Boolean isRemote;
        private String experienceLevel;
        private Integer matchScore;
        private List<String> matchReasons;
    }
}
