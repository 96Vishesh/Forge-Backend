package com.inn.automate.DAO;

import com.inn.automate.POJO.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResumeDAO extends JpaRepository<Resume, String> {

    List<Resume> findByUserId(String userId);

    @Query(value = "SELECT resume_id FROM resumes ORDER BY resume_id DESC LIMIT 1", nativeQuery = true)
    String getLastResumeId();

    List<Resume> findByUserIdAndStatus(String userId, String status);

    @Query("SELECT r FROM Resume r WHERE r.userId = :userId ORDER BY r.createdAt DESC")
    List<Resume> findRecentResumesByUserId(@Param("userId") String userId);
}