package com.example.Application_Service.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {
    private Long id;
    private String title;
    private String company;
    private String companyId;
    private String location;
    private String type;
    private String salary;
    private String description;
    private List<String> responsibilities;
    private List<String> skills;
    private List<String> benefits;
    private String status;
    private LocalDate postedDate;
    private Integer applicationsCount;
    private Integer viewsCount;
    private String seniority;
    private String logo;
    private Boolean isRemote;
    private String educationRequired;
}
