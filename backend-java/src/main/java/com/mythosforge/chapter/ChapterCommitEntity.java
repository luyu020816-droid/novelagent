package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Accepted 章节提交记录；final_text_path 为相对 {@code mythosforge.export.root} 的路径。 */
@Entity
@Table(name = "chapter_commits")
public class ChapterCommitEntity {

    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "chapter_no", nullable = false)
    private int chapterNo;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String status;

    @Column(name = "chapter_contract_id")
    private String chapterContractId;

    @Column(name = "context_pack_hash")
    private String contextPackHash;

    @Column(name = "final_text_path")
    private String finalTextPath;

    @Column(name = "review_report_id")
    private String reviewReportId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "token_usage_summary")
    private JsonNode tokenUsageSummary;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    /** Day 8：本章 accepted 后的滚动摘要（JSON：关键事件、人物状态、待揭伏笔等）。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary")
    private JsonNode summary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public int getChapterNo() {
        return chapterNo;
    }

    public void setChapterNo(int chapterNo) {
        this.chapterNo = chapterNo;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getChapterContractId() {
        return chapterContractId;
    }

    public void setChapterContractId(String chapterContractId) {
        this.chapterContractId = chapterContractId;
    }

    public String getContextPackHash() {
        return contextPackHash;
    }

    public void setContextPackHash(String contextPackHash) {
        this.contextPackHash = contextPackHash;
    }

    public String getFinalTextPath() {
        return finalTextPath;
    }

    public void setFinalTextPath(String finalTextPath) {
        this.finalTextPath = finalTextPath;
    }

    public String getReviewReportId() {
        return reviewReportId;
    }

    public void setReviewReportId(String reviewReportId) {
        this.reviewReportId = reviewReportId;
    }

    public JsonNode getTokenUsageSummary() {
        return tokenUsageSummary;
    }

    public void setTokenUsageSummary(JsonNode tokenUsageSummary) {
        this.tokenUsageSummary = tokenUsageSummary;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public JsonNode getSummary() {
        return summary;
    }

    public void setSummary(JsonNode summary) {
        this.summary = summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
