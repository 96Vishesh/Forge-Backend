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
     * Process job list from JSON file and return parsed jobs
     * POST /jobs/list
     */
    @PostMapping("/list")
    ResponseEntity<String> processJobList(@RequestParam("file") MultipartFile jobsFile);
    
    /**
     * Match resume against job listings from JSON file
     * POST /jobs/match
     */
    @PostMapping("/match")
    ResponseEntity<String> matchResumeToJobs(
        @RequestParam("resume") MultipartFile resume,
        @RequestParam("jobs") MultipartFile jobsFile
    );
}
