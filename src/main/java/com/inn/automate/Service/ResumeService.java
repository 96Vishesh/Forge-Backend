package com.inn.automate.Service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface ResumeService {

    ResponseEntity<String> uploadResume(MultipartFile file, String jobDescription);

    /**
     * NEW: Combined service - Upload, Extract, Transform, and Generate PDF
     * Returns the final resume ID with download-ready PDF
     */
    ResponseEntity<String> createAndTransformResume(MultipartFile file, String jobDescription, String templateId);

    ResponseEntity<String> getAllTemplates();

    ResponseEntity<String> getTemplatesByCategory(String category);

    ResponseEntity<String> transformResume(Map<String, String> requestMap);

    ResponseEntity<byte[]> downloadResume(String resumeId);

    ResponseEntity<String> getUserResumes();

    ResponseEntity<String> getResumeStatus(String resumeId);

    ResponseEntity<String> deleteResume(String resumeId);
}