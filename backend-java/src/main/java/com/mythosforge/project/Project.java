package com.mythosforge.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA 实体 → 表 {@code projects}。当前选定题材/初始化快照 ID 存在此表，供工作区与初始化解析。
 */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String language = "zh-CN";

    @Column(name = "target_chapters", nullable = false)
    private Integer targetChapters = 100;

    @Column(name = "current_chapter", nullable = false)
    private Integer currentChapter = 0;

    @Column(nullable = false)
    private String status = "created";

    @Column(name = "selected_genre_contract_id")
    private String selectedGenreContractId;

    @Column(name = "selected_story_contract_id")
    private String selectedStoryContractId;

    /** 丛书/同人预设（如 hp_fan），章节生成与初始化时传给 Writer。 */
    @Column(name = "fan_series_preset")
    private String fanSeriesPreset;

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
        updatedAt = now;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Integer getTargetChapters() {
        return targetChapters;
    }

    public void setTargetChapters(Integer targetChapters) {
        this.targetChapters = targetChapters;
    }

    public Integer getCurrentChapter() {
        return currentChapter;
    }

    public void setCurrentChapter(Integer currentChapter) {
        this.currentChapter = currentChapter;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSelectedGenreContractId() {
        return selectedGenreContractId;
    }

    public void setSelectedGenreContractId(String selectedGenreContractId) {
        this.selectedGenreContractId = selectedGenreContractId;
    }

    public String getSelectedStoryContractId() {
        return selectedStoryContractId;
    }

    public void setSelectedStoryContractId(String selectedStoryContractId) {
        this.selectedStoryContractId = selectedStoryContractId;
    }

    public String getFanSeriesPreset() {
        return fanSeriesPreset;
    }

    public void setFanSeriesPreset(String fanSeriesPreset) {
        this.fanSeriesPreset = fanSeriesPreset;
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
