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
 * 与 {@code chapter_commits.summary} 双写：便于 SQL 报表与「记忆」单一查询面；
 * 章节生成 Curator 仍以 commits 链为准（见 docs/memory-source.md）。
 */
@Entity
@Table(name = "memory_summaries")
public class MemorySummaryEntity {

    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "chapter_no", nullable = false)
    private int chapterNo;

    @Column(name = "commit_id", nullable = false)
    private String commitId;

    @Column
    private String title;

    @Column(name = "summary_text", nullable = false, columnDefinition = "text")
    private String summaryText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_events")
    private JsonNode keyEvents;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "character_state_changes")
    private JsonNode characterStateChanges;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_foreshadowing")
    private JsonNode newForeshadowing;

    @Column(name = "cliffhanger", columnDefinition = "text")
    private String cliffhanger;

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

    public String getCommitId() {
        return commitId;
    }

    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public JsonNode getKeyEvents() {
        return keyEvents;
    }

    public void setKeyEvents(JsonNode keyEvents) {
        this.keyEvents = keyEvents;
    }

    public JsonNode getCharacterStateChanges() {
        return characterStateChanges;
    }

    public void setCharacterStateChanges(JsonNode characterStateChanges) {
        this.characterStateChanges = characterStateChanges;
    }

    public JsonNode getNewForeshadowing() {
        return newForeshadowing;
    }

    public void setNewForeshadowing(JsonNode newForeshadowing) {
        this.newForeshadowing = newForeshadowing;
    }

    public String getCliffhanger() {
        return cliffhanger;
    }

    public void setCliffhanger(String cliffhanger) {
        this.cliffhanger = cliffhanger;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
