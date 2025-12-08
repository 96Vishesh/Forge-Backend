package com.inn.automate.Service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface ResumeService {

    ResponseEntity<String> uploadResume(MultipartFile file, String jobDescription);

    ResponseEntity<String> getAllTemplates();

    ResponseEntity<String> getTemplatesByCategory(String category);

    ResponseEntity<String> transformResume(Map<String, String> requestMap);

    ResponseEntity<byte[]> downloadResume(String resumeId);

    ResponseEntity<String> getUserResumes();

    ResponseEntity<String> getResumeStatus(String resumeId);

    ResponseEntity<String> deleteResume(String resumeId);
}