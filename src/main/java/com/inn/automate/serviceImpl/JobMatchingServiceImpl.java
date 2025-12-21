package com.inn.automate.serviceImpl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.inn.automate.JWT.JwtFilter;
import com.inn.automate.Service.JobMatchingService;
import com.inn.automate.Service.ResumeExtractorService;
import com.inn.automate.wrapper.JobMatchResult;
import com.inn.automate.wrapper.JobPosting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JobMatchingServiceImpl implements JobMatchingService {

    // Maximum jobs to process with Gemini AI (to avoid rate limits)
    private static final int MAX_JOBS_FOR_AI = 30;
    // Batch size for Gemini (multiple jobs per API call)
    private static final int BATCH_SIZE = 10;
    // Delay between batches in milliseconds
    private static final int BATCH_DELAY_MS = 1000;

    @Autowired
    private ResumeExtractorService resumeExtractorService;

    @Autowired
    private JwtFilter jwtFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<JobPosting> processJobList(String jobsJson) {
        try {
            log.info("Processing job list JSON");
            
            ObjectMapper snakeCaseMapper = new ObjectMapper();
            snakeCaseMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            
            JsonNode root = snakeCaseMapper.readTree(jobsJson);
            JsonNode jobsNode = root.has("jobs") ? root.get("jobs") : root;
            
            List<JobPosting> jobs = snakeCaseMapper.readValue(
                jobsNode.toString(),
                new TypeReference<List<JobPosting>>() {}
            );
            
            // Filter out invalid jobs
            jobs = jobs.stream()
                .filter(j -> j.getCompany() != null && !j.getCompany().equals("N/A"))
                .filter(j -> j.getDescription() != null && !j.getDescription().equals("N/A"))
                .collect(Collectors.toList());
            
            log.info("Parsed {} valid job postings", jobs.size());
            return jobs;
        } catch (Exception e) {
            log.error("Error parsing job list JSON: {}", e.getMessage());
            throw new RuntimeException("Failed to parse job list: " + e.getMessage());
        }
    }

    @Override
    public List<JobMatchResult> matchResumeToJobs(MultipartFile resumeFile, List<JobPosting> jobs) {
        try {
            log.info("Matching resume against {} jobs (optimized batch mode)", jobs.size());
            
            // Extract resume text
            String resumeText = resumeExtractorService.extractTextFromFile(resumeFile);
            log.info("Extracted {} characters from resume", resumeText.length());
            
            // STEP 1: Pre-filter using keyword matching to find most relevant jobs
            List<JobMatchResult> keywordResults = new ArrayList<>();
            for (JobPosting job : jobs) {
                JobMatchResult result = simpleKeywordMatch(resumeText, job);
                if (result.getCompatibilityScore() > 0) {
                    keywordResults.add(result);
                }
            }
            
            // Sort by keyword score and take top candidates for AI analysis
            keywordResults.sort((a, b) -> Integer.compare(b.getCompatibilityScore(), a.getCompatibilityScore()));
            List<JobPosting> topJobs = keywordResults.stream()
                .limit(MAX_JOBS_FOR_AI)
                .map(JobMatchResult::getJob)
                .collect(Collectors.toList());
            
            log.info("Pre-filtered to {} top candidates for AI analysis", topJobs.size());
            
            // STEP 2: Use Gemini AI to analyze top candidates in BATCHES
            List<JobMatchResult> aiResults = new ArrayList<>();
            
            if (geminiApiKey != null && !geminiApiKey.isEmpty() && !geminiApiKey.equals("YOUR_GEMINI_API_KEY_HERE")) {
                // Process in batches of BATCH_SIZE
                for (int i = 0; i < topJobs.size(); i += BATCH_SIZE) {
                    int endIndex = Math.min(i + BATCH_SIZE, topJobs.size());
                    List<JobPosting> batch = topJobs.subList(i, endIndex);
                    
                    log.info("Processing batch {}/{}", (i / BATCH_SIZE) + 1, (topJobs.size() + BATCH_SIZE - 1) / BATCH_SIZE);
                    
                    try {
                        List<JobMatchResult> batchResults = matchBatchWithGemini(resumeText, batch);
                        aiResults.addAll(batchResults);
                        
                        // Delay between batches to avoid rate limits
                        if (endIndex < topJobs.size()) {
                            Thread.sleep(BATCH_DELAY_MS);
                        }
                    } catch (Exception e) {
                        log.warn("Batch failed, using keyword fallback: {}", e.getMessage());
                        // Fallback to keyword matching for this batch
                        for (JobPosting job : batch) {
                            aiResults.add(simpleKeywordMatch(resumeText, job));
                        }
                    }
                }
            } else {
                // No Gemini key - use keyword matching only
                log.warn("Gemini API key not configured - using keyword matching only");
                aiResults = keywordResults.stream().limit(MAX_JOBS_FOR_AI).collect(Collectors.toList());
            }
            
            // Sort by score (highest first)
            aiResults.sort((a, b) -> Integer.compare(b.getCompatibilityScore(), a.getCompatibilityScore()));
            
            log.info("Completed matching - found {} results", aiResults.size());
            return aiResults;
        } catch (Exception e) {
            log.error("Error matching resume to jobs: {}", e.getMessage());
            throw new RuntimeException("Failed to match resume: " + e.getMessage());
        }
    }

    /**
     * Match multiple jobs in a single Gemini API call (batch processing)
     */
    private List<JobMatchResult> matchBatchWithGemini(String resumeText, List<JobPosting> jobs) {
        try {
            String prompt = buildBatchMatchingPrompt(resumeText, jobs);
            
            Map<String, Object> body = new HashMap<>();
            body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + geminiApiKey;

            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            if (resp.getBody() != null && resp.getBody().containsKey("candidates")) {
                List<Map<String, Object>> cands = (List<Map<String, Object>>) resp.getBody().get("candidates");
                if (!cands.isEmpty()) {
                    Map<String, Object> content = (Map<String, Object>) cands.get(0).get("content");
                    List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
                    if (!parts.isEmpty()) {
                        String result = parts.get(0).get("text").trim();
                        return parseBatchGeminiResponse(result, jobs);
                    }
                }
            }
            
            // Fallback if Gemini fails
            return jobs.stream().map(job -> simpleKeywordMatch(resumeText, job)).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Batch Gemini matching failed: {}", e.getMessage());
            return jobs.stream().map(job -> simpleKeywordMatch(resumeText, job)).collect(Collectors.toList());
        }
    }

    /**
     * Build prompt for batch matching (multiple jobs in one prompt)
     */
    private String buildBatchMatchingPrompt(String resumeText, List<JobPosting> jobs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze how well this resume matches EACH of the following job postings.\n\n");
        sb.append("=== RESUME ===\n");
        sb.append(resumeText.substring(0, Math.min(resumeText.length(), 2500))).append("\n\n");
        sb.append("=== JOB POSTINGS ===\n\n");
        
        for (int i = 0; i < jobs.size(); i++) {
            JobPosting job = jobs.get(i);
            sb.append("JOB ").append(i + 1).append(":\n");
            sb.append("Title: ").append(job.getTitle()).append("\n");
            sb.append("Company: ").append(job.getCompany()).append("\n");
            String desc = job.getDescription();
            sb.append("Description: ").append(desc.substring(0, Math.min(desc.length(), 500))).append("\n\n");
        }
        
        sb.append("=== RESPONSE FORMAT (JSON ARRAY ONLY) ===\n");
        sb.append("Return ONLY a valid JSON array with one object per job:\n");
        sb.append("[\n");
        sb.append("  {\"jobIndex\": 1, \"score\": <0-100>, \"matchingSkills\": [\"skill1\", ...], \"reasoning\": \"brief\"},\n");
        sb.append("  {\"jobIndex\": 2, \"score\": <0-100>, \"matchingSkills\": [...], \"reasoning\": \"...\"},\n");
        sb.append("  ...\n");
        sb.append("]\n\n");
        sb.append("Return ONLY the JSON array, no other text:");
        
        return sb.toString();
    }

    /**
     * Parse batch response from Gemini (array of job matches)
     */
    private List<JobMatchResult> parseBatchGeminiResponse(String response, List<JobPosting> jobs) {
        List<JobMatchResult> results = new ArrayList<>();
        try {
            response = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            
            int start = response.indexOf('[');
            int end = response.lastIndexOf(']');
            if (start >= 0 && end > start) {
                response = response.substring(start, end + 1);
            }
            
            JsonNode jsonArray = objectMapper.readTree(response);
            
            if (jsonArray.isArray()) {
                for (JsonNode json : jsonArray) {
                    int jobIndex = json.has("jobIndex") ? json.get("jobIndex").asInt() - 1 : -1;
                    if (jobIndex >= 0 && jobIndex < jobs.size()) {
                        int score = json.has("score") ? json.get("score").asInt() : 0;
                        String reasoning = json.has("reasoning") ? json.get("reasoning").asText() : "";
                        
                        List<String> skills = new ArrayList<>();
                        if (json.has("matchingSkills") && json.get("matchingSkills").isArray()) {
                            for (JsonNode skill : json.get("matchingSkills")) {
                                skills.add(skill.asText());
                            }
                        }
                        
                        results.add(new JobMatchResult(jobs.get(jobIndex), score, skills, reasoning));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse batch Gemini response: {}", e.getMessage());
        }
        
        // Fill in any missing jobs with keyword matching
        Set<JobPosting> matched = results.stream().map(JobMatchResult::getJob).collect(Collectors.toSet());
        for (JobPosting job : jobs) {
            if (!matched.contains(job)) {
                results.add(simpleKeywordMatch("", job));
            }
        }
        
        return results;
    }

    private JobMatchResult simpleKeywordMatch(String resumeText, JobPosting job) {
        String resumeLower = resumeText.toLowerCase();
        String descLower = job.getDescription().toLowerCase();
        
        List<String> techKeywords = Arrays.asList(
            "python", "java", "javascript", "react", "angular", "node", "aws", "azure",
            "docker", "kubernetes", "sql", "mongodb", "spring", "django", "flask",
            "machine learning", "ai", "data", "api", "rest", "microservices", "typescript",
            "html", "css", "git", "linux", "agile", "scrum", "devops", "cloud"
        );
        
        List<String> matched = new ArrayList<>();
        for (String keyword : techKeywords) {
            if (resumeLower.contains(keyword) && descLower.contains(keyword)) {
                matched.add(keyword);
            }
        }
        
        int score = Math.min(100, matched.size() * 8);
        String reasoning = matched.size() > 0 
            ? String.format("Found %d matching skills: %s", matched.size(), String.join(", ", matched.subList(0, Math.min(5, matched.size()))))
            : "No significant skill overlap found";
        
        return new JobMatchResult(job, score, matched, reasoning);
    }
}
