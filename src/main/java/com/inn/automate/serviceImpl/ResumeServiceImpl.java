package com.inn.automate.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inn.automate.JWT.JwtFilter;
import com.inn.automate.POJO.Resume;
import com.inn.automate.POJO.ResumeTemplate;
import com.inn.automate.DAO.ResumeDAO;
import com.inn.automate.DAO.ResumeTemplateDAO;
import com.inn.automate.Service.LatexToPdfService;
import com.inn.automate.Service.ResumeService;
import com.inn.automate.constants.AutoConstants;
import com.inn.automate.utils.AutoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
@Slf4j
public class ResumeServiceImpl implements ResumeService {

    @Autowired
    private ResumeDAO resumeDAO;

    @Autowired
    private ResumeTemplateDAO templateDAO;

    @Autowired
    private JwtFilter jwtFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LatexToPdfService latexToPdfService;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public ResponseEntity<String> uploadResume(MultipartFile file, String jobDescription) {
        try {
            String currentUser = jwtFilter.getCurrentUser();
            if (currentUser == null) {
                return AutoUtils.getResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
            }

            // Validate file
            if (file.isEmpty()) {
                return AutoUtils.getResponseEntity("File is empty", HttpStatus.BAD_REQUEST);
            }

            // Create resume record
            Resume resume = new Resume();
            resume.setResumeId(generateResumeId());
            resume.setUserId(currentUser);
            resume.setOriginalResume(file.getBytes());
            resume.setOriginalFilename(file.getOriginalFilename());
            resume.setJobDescription(jobDescription);
            resume.setStatus("UPLOADED");

            // Extract LaTeX from resume (placeholder - implement actual extraction)
            String extractedLatex = extractLatexFromResume(file);
            resume.setExtractedLatex(extractedLatex);
            resume.setStatus("EXTRACTED");

            resumeDAO.save(resume);

            Map<String, String> response = new HashMap<>();
            response.put("resumeId", resume.getResumeId());
            response.put("status", resume.getStatus());
            response.put("message", "Resume uploaded and processed successfully");

            return new ResponseEntity<>(objectMapper.writeValueAsString(response), HttpStatus.OK);

        } catch (IOException e) {
            log.error("Error uploading resume", e);
            return AutoUtils.getResponseEntity("Failed to upload resume", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> getAllTemplates() {
        try {
            List<ResumeTemplate> templates = templateDAO.findByIsActiveTrue();

            List<Map<String, Object>> templateList = new ArrayList<>();
            for (ResumeTemplate template : templates) {
                Map<String, Object> templateMap = new HashMap<>();
                templateMap.put("templateId", template.getTemplateId());
                templateMap.put("templateName", template.getTemplateName());
                templateMap.put("templateDescription", template.getTemplateDescription());
                templateMap.put("category", template.getCategory());

                // Convert preview image to Base64 if available
                if (template.getPreviewImage() != null) {
                    templateMap.put("previewImage", Base64.getEncoder().encodeToString(template.getPreviewImage()));
                }

                templateList.add(templateMap);
            }

            return new ResponseEntity<>(objectMapper.writeValueAsString(templateList), HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error fetching templates", e);
            return AutoUtils.getResponseEntity("Failed to fetch templates", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> getTemplatesByCategory(String category) {
        try {
            List<ResumeTemplate> templates = templateDAO.findByCategoryAndIsActiveTrue(category);

            List<Map<String, Object>> templateList = new ArrayList<>();
            for (ResumeTemplate template : templates) {
                Map<String, Object> templateMap = new HashMap<>();
                templateMap.put("templateId", template.getTemplateId());
                templateMap.put("templateName", template.getTemplateName());
                templateMap.put("templateDescription", template.getTemplateDescription());
                templateMap.put("category", template.getCategory());

                if (template.getPreviewImage() != null) {
                    templateMap.put("previewImage", Base64.getEncoder().encodeToString(template.getPreviewImage()));
                }

                templateList.add(templateMap);
            }

            return new ResponseEntity<>(objectMapper.writeValueAsString(templateList), HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error fetching templates by category", e);
            return AutoUtils.getResponseEntity("Failed to fetch templates", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> transformResume(Map<String, String> requestMap) {
        try {
            String resumeId = requestMap.get("resumeId");
            String templateId = requestMap.get("templateId");

            Optional<Resume> optionalResume = resumeDAO.findById(resumeId);
            Optional<ResumeTemplate> optionalTemplate = templateDAO.findById(templateId);

            if (optionalResume.isEmpty()) {
                return AutoUtils.getResponseEntity("Resume not found", HttpStatus.NOT_FOUND);
            }

            if (optionalTemplate.isEmpty()) {
                return AutoUtils.getResponseEntity("Template not found", HttpStatus.NOT_FOUND);
            }

            Resume resume = optionalResume.get();
            ResumeTemplate template = optionalTemplate.get();

            // Check authorization
            if (!resume.getUserId().equals(jwtFilter.getCurrentUser())) {
                return AutoUtils.getResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
            }

            // Transform resume using Gemini API
            String transformedLatex = transformResumeWithGemini(
                    resume.getExtractedLatex(),
                    resume.getJobDescription(),
                    template.getTemplateLatexStructure()
            );

            resume.setTransformedLatex(transformedLatex);
            resume.setTemplateId(templateId);
            resume.setStatus("TRANSFORMED");

            // Generate PDF from LaTeX
            byte[] pdfBytes = generatePdfFromLatex(transformedLatex);
            resume.setFinalPdf(pdfBytes);
            resume.setStatus("COMPLETED");

            resumeDAO.save(resume);

            Map<String, String> response = new HashMap<>();
            response.put("resumeId", resume.getResumeId());
            response.put("status", resume.getStatus());
            response.put("message", "Resume transformed successfully");

            return new ResponseEntity<>(objectMapper.writeValueAsString(response), HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error transforming resume", e);
            return AutoUtils.getResponseEntity("Failed to transform resume", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<byte[]> downloadResume(String resumeId) {
        try {
            Optional<Resume> optionalResume = resumeDAO.findById(resumeId);

            if (optionalResume.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            Resume resume = optionalResume.get();

            // Check authorization
            if (!resume.getUserId().equals(jwtFilter.getCurrentUser())) {
                return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
            }

            if (resume.getFinalPdf() == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.builder("attachment")
                    .filename("resume_" + resumeId + ".pdf")
                    .build());

            return new ResponseEntity<>(resume.getFinalPdf(), headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error downloading resume", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> getUserResumes() {
        try {
            String currentUser = jwtFilter.getCurrentUser();
            List<Resume> resumes = resumeDAO.findRecentResumesByUserId(currentUser);

            List<Map<String, Object>> resumeList = new ArrayList<>();
            for (Resume resume : resumes) {
                Map<String, Object> resumeMap = new HashMap<>();
                resumeMap.put("resumeId", resume.getResumeId());
                resumeMap.put("originalFilename", resume.getOriginalFilename());
                resumeMap.put("status", resume.getStatus());
                resumeMap.put("createdAt", resume.getCreatedAt().toString());
                resumeMap.put("updatedAt", resume.getUpdatedAt().toString());

                resumeList.add(resumeMap);
            }

            return new ResponseEntity<>(objectMapper.writeValueAsString(resumeList), HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error fetching user resumes", e);
            return AutoUtils.getResponseEntity("Failed to fetch resumes", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> getResumeStatus(String resumeId) {
        try {
            Optional<Resume> optionalResume = resumeDAO.findById(resumeId);

            if (optionalResume.isEmpty()) {
                return AutoUtils.getResponseEntity("Resume not found", HttpStatus.NOT_FOUND);
            }

            Resume resume = optionalResume.get();

            // Check authorization
            if (!resume.getUserId().equals(jwtFilter.getCurrentUser())) {
                return AutoUtils.getResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
            }

            Map<String, String> response = new HashMap<>();
            response.put("resumeId", resume.getResumeId());
            response.put("status", resume.getStatus());

            return new ResponseEntity<>(objectMapper.writeValueAsString(response), HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error fetching resume status", e);
            return AutoUtils.getResponseEntity("Failed to fetch status", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> deleteResume(String resumeId) {
        try {
            Optional<Resume> optionalResume = resumeDAO.findById(resumeId);

            if (optionalResume.isEmpty()) {
                return AutoUtils.getResponseEntity("Resume not found", HttpStatus.NOT_FOUND);
            }

            Resume resume = optionalResume.get();

            // Check authorization
            if (!resume.getUserId().equals(jwtFilter.getCurrentUser())) {
                return AutoUtils.getResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
            }

            resumeDAO.delete(resume);

            return AutoUtils.getResponseEntity("Resume deleted successfully", HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error deleting resume", e);
            return AutoUtils.getResponseEntity("Failed to delete resume", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Helper methods

    private String generateResumeId() {
        String lastId = resumeDAO.getLastResumeId();
        int nextIdNumber = 1;

        if (lastId != null && lastId.startsWith("R")) {
            String numberPart = lastId.substring(1);
            try {
                nextIdNumber = Integer.parseInt(numberPart) + 1;
            } catch (NumberFormatException e) {
                nextIdNumber = 1;
            }
        }
        return String.format("R%06d", nextIdNumber);
    }

    private String extractLatexFromResume(MultipartFile file) throws IOException {
        // TODO: Implement actual LaTeX extraction logic
        // This could use libraries like Apache PDFBox, Apache POI for different file formats
        // Or call an external API/service for extraction

        log.info("Extracting LaTeX from file: {}", file.getOriginalFilename());

        // Placeholder implementation
        return "\\documentclass{article}\n\\begin{document}\nExtracted resume content here\n\\end{document}";
    }

    private String transformResumeWithGemini(String extractedLatex, String jobDescription, String templateStructure) {
        try {
            // Prepare Gemini API request
            String prompt = String.format(
                    "You are an expert resume writer and LaTeX expert. " +
                            "Transform the following resume content to match the job description while maintaining LaTeX formatting.\n\n" +
                            "Original LaTeX Resume:\n%s\n\n" +
                            "Job Description:\n%s\n\n" +
                            "Template Structure:\n%s\n\n" +
                            "Instructions:\n" +
                            "1. Modify the resume content to highlight relevant skills and experiences matching the job description\n" +
                            "2. Maintain proper LaTeX syntax\n" +
                            "3. Adapt the content to fit the template structure\n" +
                            "4. Optimize keywords for ATS compatibility\n" +
                            "5. Return ONLY the complete LaTeX code, no explanations",
                    extractedLatex, jobDescription, templateStructure
            );

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            // Extract transformed LaTeX from response
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                    List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
                    if (!parts.isEmpty()) {
                        return parts.get(0).get("text");
                    }
                }
            }

            throw new RuntimeException("Failed to get valid response from Gemini API");

        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            throw new RuntimeException("Failed to transform resume with Gemini", e);
        }
    }

    private byte[] generatePdfFromLatex(String latexCode) {
        try {
            log.info("Generating PDF from LaTeX code");

            // Validate LaTeX before attempting conversion
            if (!latexToPdfService.isValidLatex(latexCode)) {
                log.warn("Invalid LaTeX structure detected");
                throw new RuntimeException("Invalid LaTeX code structure");
            }

            // Convert LaTeX to PDF using the service
            byte[] pdfBytes = latexToPdfService.convertLatexToPdf(latexCode);

            if (pdfBytes == null || pdfBytes.length == 0) {
                throw new RuntimeException("PDF generation returned empty result");
            }

            log.info("PDF generated successfully, size: {} bytes", pdfBytes.length);
            return pdfBytes;

        } catch (Exception e) {
            log.error("Error generating PDF from LaTeX", e);
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }
}