package com.example.Application_Service.service;

import com.example.Application_Service.dto.UserProfileDto;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

@Service
public class UserProfileService {

    private static final Logger logger = LoggerFactory.getLogger(UserProfileService.class);

    @Value("${api.gateway.url:http://localhost:8084}")
    private String apiGatewayUrl;

    private final RestTemplate restTemplate;

    public UserProfileService() {
        this.restTemplate = new RestTemplate();
    }

    public UserProfileDto getUserProfile(String userId) {
        try {
            String url = apiGatewayUrl + "/api/v1/auth/profile/" + userId;
            logger.info("Fetching user profile from: {}", url);
            
            HttpHeaders headers = createAuthorizationHeader();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<UserProfileDto> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, UserProfileDto.class);
            
            return response.getBody();
        } catch (Exception e) {
            logger.error("Failed to fetch user profile for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    public String getUserName(String userId) {
        UserProfileDto profile = getUserProfile(userId);
        return (profile != null && profile.getName() != null) ? profile.getName() : "Unknown User";
    }

    public String getUserEmail(String userId) {
        UserProfileDto profile = getUserProfile(userId);
        return (profile != null && profile.getEmail() != null) ? profile.getEmail() : null;
    }

    public String getResumeUrl(String resumeId) {
        if (resumeId == null || resumeId.isEmpty()) {
            return null;
        }
        // Construct the full URL through the API Gateway
        return apiGatewayUrl + "/api/v1/resumes/" + resumeId + "/download";
    }

    public byte[] getResumeFile(String resumeId) {
        try {
            String url = getResumeUrl(resumeId);
            logger.info("Fetching resume file from: {}", url);

            HttpHeaders headers = createAuthorizationHeader();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            return null;
        } catch (Exception e) {
            logger.error("Failed to fetch resume file for resumeId {}: {}", resumeId, e.getMessage());
            return null;
        }
    }

    private HttpHeaders createAuthorizationHeader() {
        HttpHeaders headers = new HttpHeaders();
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && !authHeader.isEmpty()) {
                headers.set("Authorization", authHeader);
                logger.debug("Forwarding Authorization header");
            } else {
                logger.warn("No Authorization header found in current request to forward");
            }
        }
        return headers;
    }
}
