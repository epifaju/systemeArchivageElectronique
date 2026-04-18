package com.archivage.config;

import com.archivage.common.domain.ConfidentialityLevel;
import com.archivage.common.domain.DocumentLanguage;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.ingest.watch")
public class WatchedIngestProperties {

    /**
     * Désactivé par défaut : activer explicitement en prod si besoin.
     */
    private boolean enabled = false;

    private String directory = "./data/watch-incoming";

    private long intervalMs = 60_000L;

    private Long userId = 1L;

    private Long documentTypeId = 1L;

    private String folderNumber = "INBOX";

    private String titlePrefix = "Import automatique";

    /**
     * Si null, la date du jour (UTC) est utilisée.
     */
    private LocalDate documentDate;

    private DocumentLanguage language = DocumentLanguage.FRENCH;

    private ConfidentialityLevel confidentiality = ConfidentialityLevel.INTERNAL;

    private Long departmentId;

    private String externalReference;

    private String author;

    private String notes;

    private List<String> tags = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getDocumentTypeId() {
        return documentTypeId;
    }

    public void setDocumentTypeId(Long documentTypeId) {
        this.documentTypeId = documentTypeId;
    }

    public String getFolderNumber() {
        return folderNumber;
    }

    public void setFolderNumber(String folderNumber) {
        this.folderNumber = folderNumber;
    }

    public String getTitlePrefix() {
        return titlePrefix;
    }

    public void setTitlePrefix(String titlePrefix) {
        this.titlePrefix = titlePrefix;
    }

    public LocalDate getDocumentDate() {
        return documentDate;
    }

    public void setDocumentDate(LocalDate documentDate) {
        this.documentDate = documentDate;
    }

    public DocumentLanguage getLanguage() {
        return language;
    }

    public void setLanguage(DocumentLanguage language) {
        this.language = language;
    }

    public ConfidentialityLevel getConfidentiality() {
        return confidentiality;
    }

    public void setConfidentiality(ConfidentialityLevel confidentiality) {
        this.confidentiality = confidentiality;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? tags : new ArrayList<>();
    }
}
