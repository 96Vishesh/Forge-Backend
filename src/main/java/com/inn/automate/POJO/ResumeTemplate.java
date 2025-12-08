package com.inn.automate.POJO;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.io.Serializable;

@Data
@Entity
@DynamicInsert
@DynamicUpdate
@Table(name="resume_templates")
public class ResumeTemplate implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name="template_id")
    private String templateId;

    @Column(name="template_name")
    private String templateName;

    @Column(name="template_description")
    private String templateDescription;


    @Column(name="template_latex_structure",columnDefinition = "text")
    private String templateLatexStructure;

    @Lob
    @Column(name="preview_image")
    private byte[] previewImage;

    @Column(name="category")
    private String category; // MODERN, CLASSIC, CREATIVE, MINIMALIST

    @Column(name="is_active")
    private Boolean isActive;
}