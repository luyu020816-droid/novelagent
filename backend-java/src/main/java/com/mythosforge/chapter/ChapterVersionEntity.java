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

/**
 * 章节生成版本（每次调用 Graph 一条记录）；scene_plan / critic 存 jsonb。
 */
@Entity
@Table(name = "chapter_versions")
public class ChapterVersionEntity {

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scene_plan_json")
    private JsonNode scenePlanJson;

    @Column(name = "chapter_text", columnDefinition = "text")
    private String chapterText;

    @Column(name = "styled_text", columnDefinition = "text")
    private String styledText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "token_budget_status_json")
    private JsonNode tokenBudgetStatusJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "llm_usage_summary_json")
    private JsonNode llmUsageSummaryJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "critic_report_json")
    private JsonNode criticReportJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rewrite_instruction_json")
    private JsonNode rewriteInstructionJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fulfillment_report_json")
    private JsonNode fulfillmentReportJson;

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

    public JsonNode getScenePlanJson() {
        return scenePlanJson;
    }

    public void setScenePlanJson(JsonNode scenePlanJson) {
        this.scenePlanJson = scenePlanJson;
    }

    public String getChapterText() {
        return chapterText;
    }

    public void setChapterText(String chapterText) {
        this.chapterText = chapterText;
    }

    public String getStyledText() {
        return styledText;
    }

    public void setStyledText(String styledText) {
        this.styledText = styledText;
    }

    public JsonNode getTokenBudgetStatusJson() {
        return tokenBudgetStatusJson;
    }

    public void setTokenBudgetStatusJson(JsonNode tokenBudgetStatusJson) {
        this.tokenBudgetStatusJson = tokenBudgetStatusJson;
    }

    public JsonNode getLlmUsageSummaryJson() {
        return llmUsageSummaryJson;
    }

    public void setLlmUsageSummaryJson(JsonNode llmUsageSummaryJson) {
        this.llmUsageSummaryJson = llmUsageSummaryJson;
    }

    public JsonNode getCriticReportJson() {
        return criticReportJson;
    }

    public void setCriticReportJson(JsonNode criticReportJson) {
        this.criticReportJson = criticReportJson;
    }

    public JsonNode getRewriteInstructionJson() {
        return rewriteInstructionJson;
    }

    public void setRewriteInstructionJson(JsonNode rewriteInstructionJson) {
        this.rewriteInstructionJson = rewriteInstructionJson;
    }

    public JsonNode getFulfillmentReportJson() {
        return fulfillmentReportJson;
    }

    public void setFulfillmentReportJson(JsonNode fulfillmentReportJson) {
        this.fulfillmentReportJson = fulfillmentReportJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
