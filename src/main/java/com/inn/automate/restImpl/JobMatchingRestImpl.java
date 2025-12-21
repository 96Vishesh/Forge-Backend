package com.inn.automate.restImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inn.automate.JWT.JwtFilter;
import com.inn.automate.REST.JobMatchingRest;
import com.inn.automate.Service.JobMatchingService;
import com.inn.automate.utils.AutoUtils;
import com.inn.automate.wrapper.JobMatchResult;
import com.inn.automate.wrapper.JobPosting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@Slf4j
public class JobMatchingRestImpl implements JobMatchingRest {

    @Autowired
    private JobMatchingService jobMatchingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtFilter jwtFilter;

    @Override
    public ResponseEntity<String> processJobList(MultipartFile jobsFile) {
        try {
            // Check for authenticated user
            String currentUser = jwtFilter.getCurrentUser();
            if (currentUser == null) {
                return AutoUtils.getResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
            }
            
            log.info("Processing job list from file: {} for user: {}", jobsFile.getOriginalFilename(), currentUser);
            
            if (jobsFile.isEmpty()) {
                return errorResponse("Jobs JSON file is required");
            }
            
            // Read JSON content from file
            String jobsJson = new String(jobsFile.getBytes(), StandardCharsets.UTF_8);
            
            List<JobPosting> jobs = jobMatchingService.processJobList(jobsJson);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalJobs", jobs.size());
            response.put("jobs", jobs.stream().map(this::toJobSummary).collect(Collectors.toList()));
            
            return new ResponseEntity<>(objectMapper.writeValueAsString(response), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error processing job list: {}", e.getMessage());
            return errorResponse("Failed to process job list: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<String> matchResumeToJobs(MultipartFile resume, MultipartFile jobsFile) {
        try {
            // Check for authenticated user
            String currentUser = jwtFilter.getCurrentUser();
            if (currentUser == null) {
                return AutoUtils.getResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
            }
            
            log.info("Matching resume to jobs for user: {}", currentUser);
            
            if (resume.isEmpty()) {
                return errorResponse("Resume file is required");
            }
            if (jobsFile.isEmpty()) {
                return errorResponse("Jobs JSON file is required");
            }
            
            // Read JSON content from file
            String jobsJson = new String(jobsFile.getBytes(), StandardCharsets.UTF_8);
            
            // Parse jobs from JSON
            List<JobPosting> jobs = jobMatchingService.processJobList(jobsJson);
            if (jobs.isEmpty()) {
                return errorResponse("No valid jobs found in JSON file");
            }
            
            // Match resume against jobs
            List<JobMatchResult> matchedJobs = jobMatchingService.matchResumeToJobs(resume, jobs);
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalJobsAnalyzed", jobs.size());
            response.put("matchesFound", matchedJobs.size());
            response.put("matchedJobs", matchedJobs.stream()
                .map(this::toMatchResultMap)
                .collect(Collectors.toList()));
            
            return new ResponseEntity<>(objectMapper.writeValueAsString(response), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error matching resume to jobs: {}", e.getMessage());
            return errorResponse("Failed to match resume: " + e.getMessage());
        }
    }

    private Map<String, Object> toJobSummary(JobPosting job) {
        Map<String, Object> map = new HashMap<>();
        map.put("title", job.getTitle());
        map.put("company", job.getCompany());
        map.put("location", job.getLocation());
        map.put("experienceLevel", job.getExperienceLevel());
        map.put("salary", job.getSalary());
        map.put("postedDate", job.getPostedDate());
        map.put("url", job.getUrl());
        return map;
    }

    private Map<String, Object> toMatchResultMap(JobMatchResult result) {
        Map<String, Object> map = new HashMap<>();
        map.put("compatibilityScore", result.getCompatibilityScore());
        map.put("matchingSkills", result.getMatchingSkills());
        map.put("reasoning", result.getReasoning());
        map.put("job", toJobSummary(result.getJob()));
        return map;
    }

    private ResponseEntity<String> errorResponse(String message) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", message);
            return new ResponseEntity<>(objectMapper.writeValueAsString(error), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>("{\"success\":false,\"error\":\"" + message + "\"}", HttpStatus.BAD_REQUEST);
        }
    }
}
