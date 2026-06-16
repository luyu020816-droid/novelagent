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

/**
 * 汇合点真源表一行：两线在某章交汇，类型 intersect / absorb / reveal，及揭露类行为约束。
 */
@Entity
@Table(name = "narrative_confluences")
public class NarrativeConfluenceEntity {

    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "primary_storyline_id", nullable = false, length = 64)
    private String primaryStorylineId;

    @Column(name = "secondary_storyline_id", nullable = false, length = 64)
    private String secondaryStorylineId;

    @Column(name = "target_chapter", nullable = false)
    private int targetChapter;

    @Column(name = "confluence_type", nullable = false, length = 32)
    private String confluenceType = "intersect";

    @Column(nullable = false)
    private boolean resolved;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "context_summary", columnDefinition = "text")
    private String contextSummary;

    @Column(name = "pre_reveal_hint", columnDefinition = "text")
    private String preRevealHint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "behavior_guards")
    private JsonNode behaviorGuards;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant n = Instant.now();
        if (createdAt == null) {
            createdAt = n;
        }
        updatedAt = n;
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

    public String getPrimaryStorylineId() {
        return primaryStorylineId;
    }

    public void setPrimaryStorylineId(String primaryStorylineId) {
        this.primaryStorylineId = primaryStorylineId;
    }

    public String getSecondaryStorylineId() {
        return secondaryStorylineId;
    }

    public void setSecondaryStorylineId(String secondaryStorylineId) {
        this.secondaryStorylineId = secondaryStorylineId;
    }

    public int getTargetChapter() {
        return targetChapter;
    }

    public void setTargetChapter(int targetChapter) {
        this.targetChapter = targetChapter;
    }

    public String getConfluenceType() {
        return confluenceType;
    }

    public void setConfluenceType(String confluenceType) {
        this.confluenceType = confluenceType;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getContextSummary() {
        return contextSummary;
    }

    public void setContextSummary(String contextSummary) {
        this.contextSummary = contextSummary;
    }

    public String getPreRevealHint() {
        return preRevealHint;
    }

    public void setPreRevealHint(String preRevealHint) {
        this.preRevealHint = preRevealHint;
    }

    public JsonNode getBehaviorGuards() {
        return behaviorGuards;
    }

    public void setBehaviorGuards(JsonNode behaviorGuards) {
        this.behaviorGuards = behaviorGuards;
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
