package com.inn.automate.POJO;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Entity
@DynamicInsert
@DynamicUpdate
@Table(name="resumes")
public class Resume implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name="resume_id")
    private String resumeId;

    @Column(name="user_id")
    private String userId;

    @Lob
    @Column(name="original_resume")
    private byte[] originalResume;

    @Column(name="original_filename")
    private String originalFilename;

    @Lob
    @Column(name="extracted_latex")
    private String extractedLatex;

    @Column(name="job_description", length = 5000)
    private String jobDescription;

    @Column(name="template_id")
    private String templateId;

    @Lob
    @Column(name="transformed_latex")
    private String transformedLatex;

    @Lob
    @Column(name="final_pdf")
    private byte[] finalPdf;

    @Column(name="created_at")
    private LocalDateTime createdAt;

    @Column(name="updated_at")
    private LocalDateTime updatedAt;

    @Column(name="status")
    private String status; // UPLOADED, EXTRACTED, TRANSFORMED, COMPLETED

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}