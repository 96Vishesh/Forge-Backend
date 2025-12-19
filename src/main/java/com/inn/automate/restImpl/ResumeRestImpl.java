package com.inn.automate.restImpl;

import com.inn.automate.REST.ResumeRest;
import com.inn.automate.Service.ResumeService;
import com.inn.automate.constants.AutoConstants;
import com.inn.automate.utils.AutoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/resume")
public class ResumeRestImpl implements ResumeRest {

    @Autowired
    private ResumeService resumeService;

    @Override
    public ResponseEntity<String> uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam("jobDescription") String jobDescription) {
        try {
            return resumeService.uploadResume(file, jobDescription);
        } catch (Exception ex) {
            ex.printStackTrace();
            return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> createAndTransformResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam("jobDescription") String jobDescription,
            @RequestParam("templateId") String templateId) {
        try {
            return resumeService.createAndTransformResume(file, jobDescription, templateId);
        } catch (Exception ex) {
            ex.printStackTrace();
            return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> getAllTemplates() {
        try {
            return resumeService.getAllTemplates();
        } catch (Exception ex) {
            ex.printStackTrace();
            return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> getTemplatesByCategory(String category) {
        try {
            return resumeService.getTemplatesByCategory(category);
        } catch (Exception ex) {
            ex.printStackTrace();
            return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> transformResume(Map<String, String> requestMap) {
        try {
            return resumeService.transformResume(requestMap);
        } catch (Exception ex) {
            ex.printStackTrace();
            return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<byte[]> downloadResume(String resumeId) {
        try {
            return resumeService.downloadResume(resumeId);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> getUserResumes() {
        try {
            return resumeService.getUserResumes();
        } catch (Exception ex) {
            ex.printStackTrace();
            return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> getResumeStatus(String resumeId) {
        try {
            return resumeService.getResumeStatus(resumeId);
        } catch (Exception ex) {
            ex.printStackTrace();
            return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> deleteResume(String resumeId) {
        try {
            return resumeService.deleteResume(resumeId);
        } catch (Exception ex) {
            ex.printStackTrace();
            return AutoUtils.getResponseEntity(AutoConstants.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}