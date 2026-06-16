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

@Entity
@Table(name = "chapter_narrative_metrics")
public class ChapterNarrativeMetricsEntity {

    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "chapter_no", nullable = false)
    private int chapterNo;

    @Column(name = "commit_id")
    private String commitId;

    @Column(name = "tension_score")
    private Double tensionScore;

    @Column(name = "style_similarity")
    private Double styleSimilarity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json")
    private JsonNode rawJson;

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

    public Double getTensionScore() {
        return tensionScore;
    }

    public void setTensionScore(Double tensionScore) {
        this.tensionScore = tensionScore;
    }

    public Double getStyleSimilarity() {
        return styleSimilarity;
    }

    public void setStyleSimilarity(Double styleSimilarity) {
        this.styleSimilarity = styleSimilarity;
    }

    public JsonNode getRawJson() {
        return rawJson;
    }

    public void setRawJson(JsonNode rawJson) {
        this.rawJson = rawJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
