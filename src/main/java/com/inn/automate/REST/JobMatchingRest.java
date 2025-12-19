package com.inn.automate.REST;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST API for job matching functionality
 */
@RequestMapping("/jobs")
public interface JobMatchingRest {
    
    /**
     * Process job list JSON and return parsed jobs
     * POST /jobs/list
     */
    @PostMapping("/list")
    ResponseEntity<String> processJobList(@RequestBody String jobsJson);
    
    /**
     * Match resume against job listings
     * POST /jobs/match
     */
    @PostMapping("/match")
    ResponseEntity<String> matchResumeToJobs(
        @RequestPart("resume") MultipartFile resume,
        @RequestPart("jobs") String jobsJson
    );
}
