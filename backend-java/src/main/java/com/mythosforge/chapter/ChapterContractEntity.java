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
 * JPA 实体 → {@code chapter_contracts}：单章写作指引；{@code story_contract_id} 把章纲绑到某次初始化快照。
 */
@Entity
@Table(name = "chapter_contracts")
public class ChapterContractEntity {

    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "story_contract_id", nullable = false)
    private String storyContractId;

    @Column(name = "chapter_no", nullable = false)
    private int chapterNo;

    @Column(name = "title_hint")
    private String titleHint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json", nullable = false, columnDefinition = "jsonb")
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

    public String getStoryContractId() {
        return storyContractId;
    }

    public void setStoryContractId(String storyContractId) {
        this.storyContractId = storyContractId;
    }

    public int getChapterNo() {
        return chapterNo;
    }

    public void setChapterNo(int chapterNo) {
        this.chapterNo = chapterNo;
    }

    public String getTitleHint() {
        return titleHint;
    }

    public void setTitleHint(String titleHint) {
        this.titleHint = titleHint;
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
