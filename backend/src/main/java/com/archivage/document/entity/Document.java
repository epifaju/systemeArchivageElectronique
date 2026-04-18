package com.archivage.document.entity;

import com.archivage.common.domain.ConfidentialityLevel;
import com.archivage.common.domain.DocumentLanguage;
import com.archivage.common.domain.DocumentStatus;
import com.archivage.common.entity.BaseEntity;
import com.archivage.metadata.entity.DocumentTypeEntity;
import com.archivage.user.entity.Department;
import com.archivage.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document extends BaseEntity {

    @Column(nullable = false, length = 500)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_type_id", nullable = false)
    private DocumentTypeEntity documentType;

    @Column(name = "folder_number", nullable = false, length = 100)
    private String folderNumber;

    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @Column(name = "archive_date")
    private Instant archiveDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DocumentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentLanguage language;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidentiality_level", nullable = false, length = 20)
    private ConfidentialityLevel confidentialityLevel;

    @Column(name = "original_path")
    private String originalPath;

    @Column(name = "ocr_path")
    private String ocrPath;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "ocr_text", columnDefinition = "text")
    private String ocrText;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(length = 64)
    private String sha256;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(length = 200)
    private String author;

    @Column(columnDefinition = "text")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by")
    private User validatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DocumentTag> tags = new ArrayList<>();
}
