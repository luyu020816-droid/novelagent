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
 * 故事线真源表一行：线程键、父子关系、预估章窗、里程碑 JSON、进度摘要与活跃章号。
 */
@Entity
@Table(name = "narrative_storylines")
public class NarrativeStorylineEntity {

    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "storyline_key", nullable = false, length = 64)
    private String storylineKey;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "parent_storyline_id", length = 64)
    private String parentStorylineId;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    /** MAIN / SUB / DARK */
    @Column(name = "storyline_role", nullable = false, length = 16)
    private String storylineRole = "SUB";

    @Column(name = "est_start_chapter")
    private Integer estStartChapter;

    @Column(name = "est_end_chapter")
    private Integer estEndChapter;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "milestones_json")
    private JsonNode milestonesJson;

    @Column(name = "current_milestone_index", nullable = false)
    private int currentMilestoneIndex;

    @Column(name = "last_active_chapter_no")
    private Integer lastActiveChapterNo;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "progress_summary", columnDefinition = "text")
    private String progressSummary;

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

    public String getStorylineKey() {
        return storylineKey;
    }

    public void setStorylineKey(String storylineKey) {
        this.storylineKey = storylineKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getParentStorylineId() {
        return parentStorylineId;
    }

    public void setParentStorylineId(String parentStorylineId) {
        this.parentStorylineId = parentStorylineId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStorylineRole() {
        return storylineRole;
    }

    public void setStorylineRole(String storylineRole) {
        this.storylineRole = storylineRole;
    }

    public Integer getEstStartChapter() {
        return estStartChapter;
    }

    public void setEstStartChapter(Integer estStartChapter) {
        this.estStartChapter = estStartChapter;
    }

    public Integer getEstEndChapter() {
        return estEndChapter;
    }

    public void setEstEndChapter(Integer estEndChapter) {
        this.estEndChapter = estEndChapter;
    }

    public JsonNode getMilestonesJson() {
        return milestonesJson;
    }

    public void setMilestonesJson(JsonNode milestonesJson) {
        this.milestonesJson = milestonesJson;
    }

    public int getCurrentMilestoneIndex() {
        return currentMilestoneIndex;
    }

    public void setCurrentMilestoneIndex(int currentMilestoneIndex) {
        this.currentMilestoneIndex = currentMilestoneIndex;
    }

    public Integer getLastActiveChapterNo() {
        return lastActiveChapterNo;
    }

    public void setLastActiveChapterNo(Integer lastActiveChapterNo) {
        this.lastActiveChapterNo = lastActiveChapterNo;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getProgressSummary() {
        return progressSummary;
    }

    public void setProgressSummary(String progressSummary) {
        this.progressSummary = progressSummary;
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
