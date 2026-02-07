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
public class SavedJobResponse {

    private Long id;
    private String title;
    private String company;
    private String companyId;
    private String logo;
    private String location;
    private String type;
    private String salary;
    private String posted;
    private String description;
    private List<String> requirements;
    private String postedDate;
    private String companyLogo;
    private Boolean isRemote;
    private String experienceLevel;
    private List<String> skills;
    private String savedDate;
}
