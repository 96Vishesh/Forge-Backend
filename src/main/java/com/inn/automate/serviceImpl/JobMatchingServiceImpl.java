package com.inn.automate.serviceImpl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
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

    @Autowired
    private ResumeExtractorService resumeExtractorService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<JobPosting> processJobList(String jobsJson) {
        try {
            log.info("Processing job list JSON");
            
            // Configure ObjectMapper for snake_case JSON
            ObjectMapper snakeCaseMapper = new ObjectMapper();
            snakeCaseMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            
            // Parse the JSON - expecting {"jobs": [...]}
            JsonNode root = snakeCaseMapper.readTree(jobsJson);
            JsonNode jobsNode = root.has("jobs") ? root.get("jobs") : root;
            
            List<JobPosting> jobs = snakeCaseMapper.readValue(
                jobsNode.toString(),
                new TypeReference<List<JobPosting>>() {}
            );
            
            // Filter out invalid jobs (N/A company or no description)
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
            log.info("Matching resume against {} jobs", jobs.size());
            
            // Extract text from resume
            String resumeText = resumeExtractorService.extractTextFromFile(resumeFile);
            log.info("Extracted {} characters from resume", resumeText.length());
            
            // Match against each job using Gemini
            List<JobMatchResult> results = new ArrayList<>();
            
            for (JobPosting job : jobs) {
                try {
                    JobMatchResult result = matchSingleJob(resumeText, job);
                    if (result != null && result.getCompatibilityScore() > 0) {
                        results.add(result);
                    }
                } catch (Exception e) {
                    log.warn("Failed to match job {}: {}", job.getTitle(), e.getMessage());
                }
            }
            
            // Sort by compatibility score (highest first)
            results.sort((a, b) -> Integer.compare(b.getCompatibilityScore(), a.getCompatibilityScore()));
            
            log.info("Found {} matching jobs", results.size());
            return results;
        } catch (Exception e) {
            log.error("Error matching resume to jobs: {}", e.getMessage());
            throw new RuntimeException("Failed to match resume: " + e.getMessage());
        }
    }

    private JobMatchResult matchSingleJob(String resumeText, JobPosting job) {
        if (geminiApiKey == null || geminiApiKey.isEmpty() || geminiApiKey.equals("YOUR_GEMINI_API_KEY_HERE")) {
            log.warn("Gemini API key not configured - using simple keyword matching");
            return simpleKeywordMatch(resumeText, job);
        }

        try {
            String prompt = buildMatchingPrompt(resumeText, job);
            
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
                        return parseGeminiResponse(result, job);
                    }
                }
            }
            return simpleKeywordMatch(resumeText, job);
        } catch (Exception e) {
            log.warn("Gemini matching failed for {}: {}", job.getTitle(), e.getMessage());
            return simpleKeywordMatch(resumeText, job);
        }
    }

    private String buildMatchingPrompt(String resumeText, JobPosting job) {
        return "Analyze how well this resume matches the job posting.\n\n" +
            "=== RESUME ===\n" + resumeText.substring(0, Math.min(resumeText.length(), 3000)) + "\n\n" +
            "=== JOB POSTING ===\n" +
            "Title: " + job.getTitle() + "\n" +
            "Company: " + job.getCompany() + "\n" +
            "Description: " + job.getDescription().substring(0, Math.min(job.getDescription().length(), 2000)) + "\n\n" +
            "=== RESPONSE FORMAT (JSON ONLY) ===\n" +
            "Return ONLY a valid JSON object with these exact fields:\n" +
            "{\n" +
            "  \"score\": <number 0-100>,\n" +
            "  \"matchingSkills\": [\"skill1\", \"skill2\", ...],\n" +
            "  \"reasoning\": \"Brief explanation\"\n" +
            "}\n\n" +
            "Score criteria:\n" +
            "80-100: Excellent match (most skills and experience align)\n" +
            "60-79: Good match (majority of skills match)\n" +
            "40-59: Moderate match (some relevant skills)\n" +
            "20-39: Partial match (few matching skills)\n" +
            "0-19: Poor match (minimal alignment)\n\n" +
            "Return ONLY the JSON, no other text:";
    }

    private JobMatchResult parseGeminiResponse(String response, JobPosting job) {
        try {
            // Clean up response - extract JSON
            response = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            
            // Find JSON object bounds
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start >= 0 && end > start) {
                response = response.substring(start, end + 1);
            }
            
            JsonNode json = objectMapper.readTree(response);
            
            int score = json.has("score") ? json.get("score").asInt() : 0;
            String reasoning = json.has("reasoning") ? json.get("reasoning").asText() : "";
            
            List<String> skills = new ArrayList<>();
            if (json.has("matchingSkills") && json.get("matchingSkills").isArray()) {
                for (JsonNode skill : json.get("matchingSkills")) {
                    skills.add(skill.asText());
                }
            }
            
            return new JobMatchResult(job, score, skills, reasoning);
        } catch (Exception e) {
            log.warn("Failed to parse Gemini response: {}", e.getMessage());
            return simpleKeywordMatch("", job);
        }
    }

    private JobMatchResult simpleKeywordMatch(String resumeText, JobPosting job) {
        // Simple keyword matching fallback
        String resumeLower = resumeText.toLowerCase();
        String descLower = job.getDescription().toLowerCase();
        
        List<String> techKeywords = Arrays.asList(
            "python", "java", "javascript", "react", "angular", "node", "aws", "azure",
            "docker", "kubernetes", "sql", "mongodb", "spring", "django", "flask",
            "machine learning", "ai", "data", "api", "rest", "microservices"
        );
        
        List<String> matched = new ArrayList<>();
        int matchCount = 0;
        
        for (String keyword : techKeywords) {
            if (resumeLower.contains(keyword) && descLower.contains(keyword)) {
                matched.add(keyword);
                matchCount++;
            }
        }
        
        int score = Math.min(100, matchCount * 10);
        String reasoning = matchCount > 0 
            ? String.format("Found %d matching technical skills", matchCount)
            : "No significant skill overlap found";
        
        return new JobMatchResult(job, score, matched, reasoning);
    }
}
