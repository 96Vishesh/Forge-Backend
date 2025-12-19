package com.inn.automate.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a job posting from the LinkedIn scraper
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobPosting {
    private String company;
    private String description;
    private String experienceLevel;
    private String location;
    private String postedDate;
    private String salary;
    private String title;
    private String url;
    
    // Alias for JSON mapping with snake_case
    public void setExperience_level(String experienceLevel) {
        this.experienceLevel = experienceLevel;
    }
    
    public void setPosted_date(String postedDate) {
        this.postedDate = postedDate;
    }
}
