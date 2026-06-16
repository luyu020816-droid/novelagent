package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "generation_jobs")
public class GenerationJobEntity {

    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "chapter_no", nullable = false)
    private int chapterNo;

    @Column(name = "job_type", nullable = false, length = 32)
    private String jobType;

    @Column(nullable = false)
    private String status;

    @Column(name = "current_stage")
    private String currentStage;

    @Column(name = "progress_pct", nullable = false)
    private int progressPct;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json")
    private JsonNode payloadJson;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "chapter_version_id")
    private String chapterVersionId;

    @Column(name = "total_prompt_tokens")
    private Long totalPromptTokens;

    @Column(name = "total_completion_tokens")
    private Long totalCompletionTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    @Column(name = "retry_waste_tokens")
    private Long retryWasteTokens;

    @Column(name = "trimmed_optional_count")
    private Integer trimmedOptionalCount;

    @Column(name = "critic_reject_rounds")
    private Integer criticRejectRounds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "llm_usage_summary_json")
    private JsonNode llmUsageSummaryJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "token_budget_status_json")
    private JsonNode tokenBudgetStatusJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
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

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
    }

    public int getProgressPct() {
        return progressPct;
    }

    public void setProgressPct(int progressPct) {
        this.progressPct = progressPct;
    }

    public JsonNode getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(JsonNode payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getChapterVersionId() {
        return chapterVersionId;
    }

    public void setChapterVersionId(String chapterVersionId) {
        this.chapterVersionId = chapterVersionId;
    }

    public Long getTotalPromptTokens() {
        return totalPromptTokens;
    }

    public void setTotalPromptTokens(Long totalPromptTokens) {
        this.totalPromptTokens = totalPromptTokens;
    }

    public Long getTotalCompletionTokens() {
        return totalCompletionTokens;
    }

    public void setTotalCompletionTokens(Long totalCompletionTokens) {
        this.totalCompletionTokens = totalCompletionTokens;
    }

    public Long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Long getRetryWasteTokens() {
        return retryWasteTokens;
    }

    public void setRetryWasteTokens(Long retryWasteTokens) {
        this.retryWasteTokens = retryWasteTokens;
    }

    public Integer getTrimmedOptionalCount() {
        return trimmedOptionalCount;
    }

    public void setTrimmedOptionalCount(Integer trimmedOptionalCount) {
        this.trimmedOptionalCount = trimmedOptionalCount;
    }

    public Integer getCriticRejectRounds() {
        return criticRejectRounds;
    }

    public void setCriticRejectRounds(Integer criticRejectRounds) {
        this.criticRejectRounds = criticRejectRounds;
    }

    public JsonNode getLlmUsageSummaryJson() {
        return llmUsageSummaryJson;
    }

    public void setLlmUsageSummaryJson(JsonNode llmUsageSummaryJson) {
        this.llmUsageSummaryJson = llmUsageSummaryJson;
    }

    public JsonNode getTokenBudgetStatusJson() {
        return tokenBudgetStatusJson;
    }

    public void setTokenBudgetStatusJson(JsonNode tokenBudgetStatusJson) {
        this.tokenBudgetStatusJson = tokenBudgetStatusJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
