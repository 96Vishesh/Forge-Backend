package com.inn.automate.Service;

import com.inn.automate.wrapper.JobMatchResult;
import com.inn.automate.wrapper.JobPosting;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Service for processing job listings and matching against resumes
 */
public interface JobMatchingService {
    
    /**
     * Process JSON job list from Flask scraper and return parsed jobs
     * @param jobsJson JSON string containing jobs array
     * @return List of parsed job postings
     */
    List<JobPosting> processJobList(String jobsJson);
    
    /**
     * Match a resume against job listings using Gemini AI
     * @param resumeFile User's resume PDF
     * @param jobs List of job postings to match against
     * @return List of matched jobs with compatibility scores, sorted by score descending
     */
    List<JobMatchResult> matchResumeToJobs(MultipartFile resumeFile, List<JobPosting> jobs);
}
