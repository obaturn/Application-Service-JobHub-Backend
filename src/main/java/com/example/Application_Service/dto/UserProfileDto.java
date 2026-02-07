package com.example.Application_Service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private String id;
    private String name;
    private String email;
    private String location;
    private Boolean openToRemote;
    private Integer yearsOfExperience;
    private List<SkillDto> skills;
    private List<ExperienceDto> experience;
    private List<EducationDto> education;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillDto {
        private String id;
        private String name;
        private String category;
        private String proficiencyLevel;
        private Integer yearsOfExperience;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperienceDto {
        private String id;
        private String companyName;
        private String jobTitle;
        private String location;
        private Boolean isRemote;
        private Instant startDate;
        private Instant endDate;
        private Boolean isCurrentPosition;
        private String employmentType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EducationDto {
        private String id;
        private String institutionName;
        private String degree;
        private String fieldOfStudy;
        private String location;
        private Instant startDate;
        private Instant endDate;
        private Double gpa;
    }
}
