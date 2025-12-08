package com.inn.automate.DAO;

import com.inn.automate.POJO.ResumeTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResumeTemplateDAO extends JpaRepository<ResumeTemplate, String> {

    List<ResumeTemplate> findByIsActiveTrue();

    List<ResumeTemplate> findByCategory(String category);

    List<ResumeTemplate> findByCategoryAndIsActiveTrue(String category);
}