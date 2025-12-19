package com.inn.automate.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inn.automate.JWT.JwtFilter;
import com.inn.automate.POJO.Resume;
import com.inn.automate.POJO.ResumeTemplate;
import com.inn.automate.DAO.ResumeDAO;
import com.inn.automate.DAO.ResumeTemplateDAO;
import com.inn.automate.Service.LatexToPdfService;
import com.inn.automate.Service.ResumeExtractorService;
import com.inn.automate.Service.ResumeService;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    @Autowired
    private ResumeExtractorService resumeExtractorService;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public ResponseEntity<String> uploadResume(MultipartFile file, String jobDescription) {
        try {
            String currentUser = jwtFilter.getCurrentUser();
            if (currentUser == null) return AutoUtils.getResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
            if (file.isEmpty()) return AutoUtils.getResponseEntity("File is empty", HttpStatus.BAD_REQUEST);

            Resume resume = new Resume();
            resume.setResumeId(generateResumeId());
            resume.setUserId(currentUser);
            resume.setOriginalResume(file.getBytes());
            resume.setOriginalFilename(file.getOriginalFilename());
            resume.setJobDescription(jobDescription);
            resume.setStatus("UPLOADED");

            String extractedText = resumeExtractorService.extractTextFromFile(file);
            resume.setExtractedLatex(extractedText);
            resume.setStatus("EXTRACTED");
            resumeDAO.save(resume);

            Map<String, String> response = new HashMap<>();
            response.put("resumeId", resume.getResumeId());
            response.put("status", resume.getStatus());
            return new ResponseEntity<>(objectMapper.writeValueAsString(response), HttpStatus.OK);
        } catch (IOException e) {
            log.error("Error uploading resume", e);
            return AutoUtils.getResponseEntity("Failed to upload resume", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> createAndTransformResume(MultipartFile file, String jobDescription, String templateId) {
        try {
            String currentUser = jwtFilter.getCurrentUser();
            if (currentUser == null) return AutoUtils.getResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
            if (file.isEmpty()) return AutoUtils.getResponseEntity("File is empty", HttpStatus.BAD_REQUEST);
            if (jobDescription == null || jobDescription.trim().isEmpty()) return AutoUtils.getResponseEntity("Job description required", HttpStatus.BAD_REQUEST);
            if (templateId == null || templateId.trim().isEmpty()) return AutoUtils.getResponseEntity("Template ID required", HttpStatus.BAD_REQUEST);

            Optional<ResumeTemplate> optionalTemplate = templateDAO.findById(templateId);
            if (optionalTemplate.isEmpty()) return AutoUtils.getResponseEntity("Template not found", HttpStatus.NOT_FOUND);

            log.info("Starting job-tailored resume creation for user: {}", currentUser);

            Resume resume = new Resume();
            resume.setResumeId(generateResumeId());
            resume.setUserId(currentUser);
            resume.setOriginalResume(file.getBytes());
            resume.setOriginalFilename(file.getOriginalFilename());
            resume.setJobDescription(jobDescription);
            resume.setTemplateId(templateId);
            resume.setStatus("UPLOADED");

            String extractedText = resumeExtractorService.extractTextFromFile(file);
            resume.setExtractedLatex(extractedText);
            resume.setStatus("EXTRACTED");
            resumeDAO.save(resume);
            log.info("Extracted {} characters from resume", extractedText.length());

            String transformedLatex = transformResumeWithGemini(extractedText, jobDescription);
            resume.setTransformedLatex(transformedLatex);
            resume.setStatus("TRANSFORMED");
            resumeDAO.save(resume);

            byte[] pdfBytes = generatePdfFromLatex(transformedLatex);
            resume.setFinalPdf(pdfBytes);
            resume.setStatus("COMPLETED");
            resumeDAO.save(resume);
            log.info("Job-tailored PDF generated for resume: {}", resume.getResumeId());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("resumeId", resume.getResumeId());
            response.put("status", resume.getStatus());
            response.put("pdfSize", pdfBytes.length);
            response.put("downloadUrl", "/resume/download/" + resume.getResumeId());
            return new ResponseEntity<>(objectMapper.writeValueAsString(response), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error creating resume", e);
            return AutoUtils.getResponseEntity("Failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> getAllTemplates() {
        try {
            List<ResumeTemplate> templates = templateDAO.findByIsActiveTrue();
            List<Map<String, Object>> list = new ArrayList<>();
            for (ResumeTemplate t : templates) {
                Map<String, Object> m = new HashMap<>();
                m.put("templateId", t.getTemplateId());
                m.put("templateName", t.getTemplateName());
                m.put("templateDescription", t.getTemplateDescription());
                m.put("category", t.getCategory());
                if (t.getPreviewImage() != null) m.put("previewImage", Base64.getEncoder().encodeToString(t.getPreviewImage()));
                list.add(m);
            }
            return new ResponseEntity<>(objectMapper.writeValueAsString(list), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error fetching templates", e);
            return AutoUtils.getResponseEntity("Failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> getTemplatesByCategory(String category) {
        try {
            List<ResumeTemplate> templates = templateDAO.findByCategoryAndIsActiveTrue(category);
            List<Map<String, Object>> list = new ArrayList<>();
            for (ResumeTemplate t : templates) {
                Map<String, Object> m = new HashMap<>();
                m.put("templateId", t.getTemplateId());
                m.put("templateName", t.getTemplateName());
                m.put("category", t.getCategory());
                list.add(m);
            }
            return new ResponseEntity<>(objectMapper.writeValueAsString(list), HttpStatus.OK);
        } catch (Exception e) {
            return AutoUtils.getResponseEntity("Failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> transformResume(Map<String, String> requestMap) {
        try {
            Optional<Resume> optResume = resumeDAO.findById(requestMap.get("resumeId"));
            if (optResume.isEmpty()) return AutoUtils.getResponseEntity("Not found", HttpStatus.NOT_FOUND);

            Resume resume = optResume.get();
            if (!resume.getUserId().equals(jwtFilter.getCurrentUser())) return AutoUtils.getResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);

            String latex = transformResumeWithGemini(resume.getExtractedLatex(), resume.getJobDescription());
            resume.setTransformedLatex(latex);
            resume.setFinalPdf(generatePdfFromLatex(latex));
            resume.setStatus("COMPLETED");
            resumeDAO.save(resume);

            Map<String, String> resp = new HashMap<>();
            resp.put("resumeId", resume.getResumeId());
            resp.put("status", "COMPLETED");
            return new ResponseEntity<>(objectMapper.writeValueAsString(resp), HttpStatus.OK);
        } catch (Exception e) {
            return AutoUtils.getResponseEntity("Failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<byte[]> downloadResume(String resumeId) {
        try {
            Optional<Resume> opt = resumeDAO.findById(resumeId);
            if (opt.isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            Resume r = opt.get();
            if (!r.getUserId().equals(jwtFilter.getCurrentUser())) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
            if (r.getFinalPdf() == null) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            if (!latexToPdfService.isPdfValid(r.getFinalPdf())) return new ResponseEntity<>(HttpStatus.UNPROCESSABLE_ENTITY);

            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_PDF);
            h.setContentDisposition(ContentDisposition.builder("attachment").filename("resume_" + resumeId + ".pdf").build());
            return new ResponseEntity<>(r.getFinalPdf(), h, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> getUserResumes() {
        try {
            List<Resume> resumes = resumeDAO.findRecentResumesByUserId(jwtFilter.getCurrentUser());
            List<Map<String, Object>> list = new ArrayList<>();
            for (Resume r : resumes) {
                Map<String, Object> m = new HashMap<>();
                m.put("resumeId", r.getResumeId());
                m.put("filename", r.getOriginalFilename());
                m.put("status", r.getStatus());
                list.add(m);
            }
            return new ResponseEntity<>(objectMapper.writeValueAsString(list), HttpStatus.OK);
        } catch (Exception e) {
            return AutoUtils.getResponseEntity("Failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> getResumeStatus(String resumeId) {
        try {
            Optional<Resume> opt = resumeDAO.findById(resumeId);
            if (opt.isEmpty()) return AutoUtils.getResponseEntity("Not found", HttpStatus.NOT_FOUND);
            Map<String, String> r = new HashMap<>();
            r.put("status", opt.get().getStatus());
            return new ResponseEntity<>(objectMapper.writeValueAsString(r), HttpStatus.OK);
        } catch (Exception e) {
            return AutoUtils.getResponseEntity("Failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> deleteResume(String resumeId) {
        try {
            Optional<Resume> opt = resumeDAO.findById(resumeId);
            if (opt.isEmpty()) return AutoUtils.getResponseEntity("Not found", HttpStatus.NOT_FOUND);
            if (!opt.get().getUserId().equals(jwtFilter.getCurrentUser())) return AutoUtils.getResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
            resumeDAO.delete(opt.get());
            return AutoUtils.getResponseEntity("Deleted", HttpStatus.OK);
        } catch (Exception e) {
            return AutoUtils.getResponseEntity("Failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String generateResumeId() {
        String lastId = resumeDAO.getLastResumeId();
        int n = 1;
        if (lastId != null && lastId.startsWith("R")) {
            try { n = Integer.parseInt(lastId.substring(1)) + 1; } catch (Exception ignored) {}
        }
        return String.format("R%06d", n);
    }

    private String transformResumeWithGemini(String resumeText, String jobDescription) {
        resumeText = cleanTextForLatex(resumeText);
        jobDescription = cleanTextForLatex(jobDescription);
        
        if (geminiApiKey == null || geminiApiKey.isEmpty() || geminiApiKey.equals("YOUR_GEMINI_API_KEY_HERE")) {
            log.warn("Gemini API key not configured - using simple formatting");
            return simpleFormatFallback(resumeText, jobDescription);
        }
        
        try {
            String prompt = buildTailoringPrompt(resumeText, jobDescription);
            
            Map<String, Object> body = new HashMap<>();
            body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + geminiApiKey;

            log.info("Calling Gemini API to tailor resume to job description");
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            if (resp.getBody() != null && resp.getBody().containsKey("candidates")) {
                List<Map<String, Object>> cands = (List<Map<String, Object>>) resp.getBody().get("candidates");
                if (!cands.isEmpty()) {
                    Map<String, Object> content = (Map<String, Object>) cands.get(0).get("content");
                    List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
                    if (!parts.isEmpty()) {
                        String result = parts.get(0).get("text");
                        result = result.replaceAll("```latex\\s*", "").replaceAll("```\\s*", "").trim();
                        log.info("Successfully tailored resume to job description");
                        return result;
                    }
                }
            }
            throw new RuntimeException("Invalid Gemini response");
        } catch (Exception e) {
            log.error("Gemini API error: {} - falling back to simple formatting", e.getMessage());
            return simpleFormatFallback(resumeText, jobDescription);
        }
    }

    /**
     * ENHANCED PROMPT - Specifically targets Summary, Technical Skills, and Projects sections
     */
    private String buildTailoringPrompt(String resumeText, String jobDescription) {
        return "You are an expert resume writer and ATS optimization specialist.\n\n" +
            "=== CANDIDATE'S ORIGINAL RESUME ===\n" + resumeText + "\n\n" +
            "=== TARGET JOB DESCRIPTION ===\n" + jobDescription + "\n\n" +
            "=== SECTION-BY-SECTION TAILORING INSTRUCTIONS ===\n\n" +
            "**SUMMARY SECTION** (REWRITE COMPLETELY FOR THIS JOB):\n" +
            "- Write a NEW 2-3 sentence summary specifically for THIS job\n" +
            "- Start with experience level and field matching the job\n" +
            "- Mention 2-3 KEY TECHNOLOGIES from the job description that candidate knows\n" +
            "- Include the job's MAIN FOCUS (backend, frontend, data, ML, etc.)\n" +
            "- End with a relevant strength matching job requirements\n\n" +
            "**TECHNICAL SKILLS SECTION** (REORDER BY JOB RELEVANCE):\n" +
            "- REORDER skill categories so job-mentioned tech appears FIRST\n" +
            "- Within each category, list job-matching skills before others\n" +
            "- If job mentions 'Python, Java' - list these first in Programming Languages\n" +
            "- If job mentions 'AWS, Docker' - put Cloud/DevOps category near top\n\n" +
            "**PROJECTS SECTION** (MODIFY FOR JOB KEYWORDS):\n" +
            "- REORDER projects so most job-relevant appear FIRST\n" +
            "- REWRITE bullet points to include job description keywords\n" +
            "- If job says 'REST APIs' - add 'RESTful API development' to relevant bullets\n" +
            "- If job says 'microservices' - mention 'microservices architecture' where applicable\n" +
            "- Add metrics that match job requirements (scalability, performance, users)\n" +
            "- Include Technologies: line under each project\n\n" +
            "**KEEP UNCHANGED**: Name, Contact Info, Education, Certifications\n\n" +
            "=== LATEX FORMAT ===\n" +
            "\\documentclass[11pt,a4paper]{article}\n" +
            "\\usepackage[margin=0.7in]{geometry}\n" +
            "\\usepackage{enumitem,hyperref,titlesec,xcolor}\n" +
            "\\definecolor{linkblue}{RGB}{0,0,180}\n" +
            "\\hypersetup{colorlinks=true,urlcolor=linkblue}\n" +
            "\\titleformat{\\section}{\\large\\bfseries}{}{0em}{}[\\titlerule]\n" +
            "\\setlist[itemize]{leftmargin=*,label=$\\circ$}\n" +
            "\\pagestyle{empty}\n\n" +
            "CRITICAL RULES:\n" +
            "- Use ASCII hyphen (-) only, no en-dash or em-dash\n" +
            "- Every \\begin{itemize} MUST have at least one \\item\n" +
            "- Use \\hfill for dates, \\textbf{} for bold\n" +
            "- Output ONLY LaTeX code, NO explanations\n\n" +
            "Generate the job-tailored LaTeX resume:";
    }

    private String cleanTextForLatex(String text) {
        if (text == null) return "";
        text = text.replace("\u2013", "-").replace("\u2014", "-").replace("\u2012", "-");
        text = text.replace("\u201C", "\"").replace("\u201D", "\"");
        text = text.replace("\u2018", "'").replace("\u2019", "'");
        text = text.replace("\u2022", "-").replace("\u25CF", "-").replace("\u25CB", "-");
        text = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        return text;
    }

    private String simpleFormatFallback(String text, String jobDescription) {
        log.info("Using simple format fallback");
        
        String name = extractName(text);
        String email = extractEmail(text);
        String phone = extractPhone(text);
        String summary = extractSection(text, "summary", new String[]{"education","skills"});
        String education = extractSection(text, "education", new String[]{"skills","technical","projects"});
        String skills = extractSection(text, "skill", new String[]{"projects","experience"});
        String projects = extractSection(text, "project", new String[]{"certification","award"});
        String certs = extractSection(text, "certification", new String[]{"award",null});

        StringBuilder sb = new StringBuilder();
        sb.append("\\documentclass[11pt,a4paper]{article}\n");
        sb.append("\\usepackage[margin=0.7in]{geometry}\n");
        sb.append("\\usepackage{enumitem,hyperref,titlesec,xcolor}\n");
        sb.append("\\definecolor{linkblue}{RGB}{0,0,180}\n");
        sb.append("\\hypersetup{colorlinks=true,urlcolor=linkblue}\n");
        sb.append("\\titleformat{\\section}{\\large\\bfseries}{}{0em}{}[\\titlerule]\n");
        sb.append("\\setlist[itemize]{leftmargin=*,label=$\\circ$}\n");
        sb.append("\\pagestyle{empty}\n\\begin{document}\n\n");

        sb.append("\\begin{center}\n{\\Huge\\textit{").append(esc(name)).append("}}\\\\\n\\vspace{3pt}\n");
        if (!email.isEmpty()) sb.append("\\href{mailto:").append(email).append("}{").append(esc(email)).append("} ");
        if (!phone.isEmpty()) sb.append("\\quad ").append(esc(phone));
        sb.append("\n\\end{center}\n\n");

        if (summary.length() > 30) {
            sb.append("\\section*{Summary}\n").append(esc(cleanSection(summary))).append("\n\n");
        }

        if (education.length() > 30) {
            sb.append("\\section*{Education}\n");
            for (String line : education.split("\n")) {
                line = line.trim();
                if (line.length() > 10) {
                    if (line.toLowerCase().contains("university") || line.toLowerCase().contains("college")) {
                        sb.append("\\textbf{").append(esc(line)).append("}\\\\\n");
                    } else {
                        sb.append(esc(line)).append("\\\\\n");
                    }
                }
            }
            sb.append("\n");
        }

        if (skills.length() > 30) {
            sb.append("\\section*{Technical Skills}\n");
            for (String line : skills.split("\n")) {
                line = line.trim();
                if (line.contains(":") && line.length() > 10) {
                    String[] p = line.split(":", 2);
                    sb.append("\\textbf{").append(esc(p[0].trim())).append(":} ");
                    if (p.length > 1) sb.append(esc(p[1].trim()));
                    sb.append("\\\\\n");
                } else if (line.length() > 10) {
                    sb.append(esc(line)).append("\\\\\n");
                }
            }
            sb.append("\n");
        }

        if (projects.length() > 30) {
            sb.append("\\section*{Projects}\n");
            String[] lines = projects.split("\n");
            boolean inList = false;
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.length() < 80 && !line.startsWith("-") && !line.startsWith("o ")) {
                    if (inList) sb.append("\\end{itemize}\n");
                    sb.append("\\textbf{").append(esc(line)).append("}\n\\begin{itemize}\n");
                    sb.append("\\item Project details\n"); // Ensure at least one item
                    inList = true;
                } else {
                    String cleaned = line.replaceAll("^[-o\\s]+", "").trim();
                    if (cleaned.length() > 10 && inList) sb.append("\\item ").append(esc(cleaned)).append("\n");
                }
            }
            if (inList) sb.append("\\end{itemize}\n");
            sb.append("\n");
        }

        if (certs.length() > 20) {
            sb.append("\\section*{Certifications}\n\\begin{itemize}\n");
            for (String line : certs.split("\n")) {
                line = line.trim().replaceAll("^[-o\\s]+", "");
                if (line.length() > 10) sb.append("\\item ").append(esc(line)).append("\n");
            }
            sb.append("\\end{itemize}\n\n");
        }

        sb.append("\\end{document}\n");
        return sb.toString();
    }

    private String extractName(String t) {
        for (String l : t.split("\n")) {
            l = l.trim();
            if (l.length() > 3 && l.length() < 50 && !l.contains("@") && !l.toLowerCase().contains("resume")) return l;
        }
        return "Resume";
    }

    private String extractEmail(String t) {
        Matcher m = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}").matcher(t);
        return m.find() ? m.group() : "";
    }

    private String extractPhone(String t) {
        Matcher m = Pattern.compile("[+]?[0-9][0-9\\-\\s]{8,14}").matcher(t);
        return m.find() ? m.group().trim() : "";
    }

    private String extractSection(String t, String start, String[] ends) {
        String lt = t.toLowerCase();
        int si = lt.indexOf(start);
        if (si < 0) return "";
        int ei = t.length();
        if (ends != null) {
            for (String e : ends) {
                if (e == null) continue;
                int i = lt.indexOf(e.toLowerCase(), si + start.length());
                if (i > si && i < ei) ei = i;
            }
        }
        return t.substring(si + start.length(), ei).trim();
    }

    private String cleanSection(String s) { return s.replaceAll("^[:\\s]+", "").trim(); }

    private String esc(String t) {
        if (t == null) return "";
        return t.replace("\\", "\\textbackslash{}")
            .replace("&", "\\&").replace("%", "\\%").replace("$", "\\$")
            .replace("#", "\\#").replace("_", "\\_").replace("{", "\\{")
            .replace("}", "\\}").replace("~", "\\textasciitilde{}").replace("^", "\\textasciicircum{}");
    }

    private byte[] generatePdfFromLatex(String latex) {
        log.info("Generating PDF from LaTeX");
        if (!latexToPdfService.isValidLatex(latex)) throw new RuntimeException("Invalid LaTeX");
        byte[] pdf = latexToPdfService.convertLatexToPdf(latex);
        if (pdf == null || pdf.length == 0) throw new RuntimeException("Empty PDF");
        if (!latexToPdfService.isPdfValid(pdf)) throw new RuntimeException("Invalid PDF");
        log.info("PDF generated: {} bytes", pdf.length);
        return pdf;
    }
}