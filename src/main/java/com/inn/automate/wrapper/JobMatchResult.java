package com.inn.automate.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * DTO for job match results with compatibility score
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchResult {
    private JobPosting job;
    private int compatibilityScore; // 0-100 percentage
    private List<String> matchingSkills;
    private String reasoning;
}
