package com.mythosforge.narrative;

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
@Table(name = "narrative_causal_edges")
public class NarrativeCausalEdgeEntity {

    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "cause_summary", nullable = false)
    private String causeSummary;

    @Column(name = "effect_summary")
    private String effectSummary;

    @Column(name = "planted_chapter", nullable = false)
    private int plantedChapter;

    @Column(name = "due_chapter")
    private Integer dueChapter;

    @Column(nullable = false)
    private int importance = 2;

    @Column(nullable = false)
    private String status = "open";

    @Column(name = "resolved_chapter")
    private Integer resolvedChapter;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "involved_entities")
    private JsonNode involvedEntities;

    @Column(name = "source_chapter")
    private Integer sourceChapter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
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

    public String getCauseSummary() {
        return causeSummary;
    }

    public void setCauseSummary(String causeSummary) {
        this.causeSummary = causeSummary;
    }

    public String getEffectSummary() {
        return effectSummary;
    }

    public void setEffectSummary(String effectSummary) {
        this.effectSummary = effectSummary;
    }

    public int getPlantedChapter() {
        return plantedChapter;
    }

    public void setPlantedChapter(int plantedChapter) {
        this.plantedChapter = plantedChapter;
    }

    public Integer getDueChapter() {
        return dueChapter;
    }

    public void setDueChapter(Integer dueChapter) {
        this.dueChapter = dueChapter;
    }

    public int getImportance() {
        return importance;
    }

    public void setImportance(int importance) {
        this.importance = importance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getResolvedChapter() {
        return resolvedChapter;
    }

    public void setResolvedChapter(Integer resolvedChapter) {
        this.resolvedChapter = resolvedChapter;
    }

    public JsonNode getInvolvedEntities() {
        return involvedEntities;
    }

    public void setInvolvedEntities(JsonNode involvedEntities) {
        this.involvedEntities = involvedEntities;
    }

    public Integer getSourceChapter() {
        return sourceChapter;
    }

    public void setSourceChapter(Integer sourceChapter) {
        this.sourceChapter = sourceChapter;
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
