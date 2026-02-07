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
public class SavedJobsResponse {

    private List<SavedJobResponse> savedJobs;
    private PagedResponse.PaginationInfo pagination;
}
