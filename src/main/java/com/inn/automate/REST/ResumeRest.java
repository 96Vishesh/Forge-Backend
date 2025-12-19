package com.inn.automate.REST;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RequestMapping(path="/resume")
public interface ResumeRest {

    @PostMapping(path = "/upload")
    ResponseEntity<String> uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam("jobDescription") String jobDescription
    );

    /**
     * NEW: Combined API - Upload, Transform, and Generate PDF in one call
     * This is the recommended endpoint for end-users
     */
    @PostMapping(path = "/create")
    ResponseEntity<String> createAndTransformResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam("jobDescription") String jobDescription,
            @RequestParam("templateId") String templateId
    );

    @GetMapping(path = "/templates")
    ResponseEntity<String> getAllTemplates();

    @GetMapping(path = "/templates/category/{category}")
    ResponseEntity<String> getTemplatesByCategory(@PathVariable String category);

    @PostMapping(path = "/transform")
    ResponseEntity<String> transformResume(@RequestBody Map<String, String> requestMap);

    @GetMapping(path = "/download/{resumeId}")
    ResponseEntity<byte[]> downloadResume(@PathVariable String resumeId);

    @GetMapping(path = "/user/resumes")
    ResponseEntity<String> getUserResumes();

    @GetMapping(path = "/status/{resumeId}")
    ResponseEntity<String> getResumeStatus(@PathVariable String resumeId);

    @DeleteMapping(path = "/delete/{resumeId}")
    ResponseEntity<String> deleteResume(@PathVariable String resumeId);
}