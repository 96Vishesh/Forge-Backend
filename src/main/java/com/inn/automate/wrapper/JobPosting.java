package com.inn.automate.wrapper;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    
    @JsonProperty("experience_level")
    private String experienceLevel;
    
    private String location;
    
    @JsonProperty("posted_date")
    private String postedDate;
    
    private String salary;
    private String title;
    private String url;
}
