package com.example.Application_Service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateJobRequest {

    @NotNull(message = "Job title is required")
    private String title;

    private String company;

    private String companyId;

    @NotNull(message = "Location is required")
    private String location;

    private String type;

    private String salary;

    @NotNull(message = "Description is required")
    private String description;

    private List<String> responsibilities;

    private List<String> skills;

    private List<String> benefits;

    private String status;

    private String seniority;

    private Boolean isRemote;

    private String educationRequired;
}
