package com.mythosforge.project;

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

    /** 可选：全书叙事阶段（如 ACTIVE、PAUSED_ERROR），详见 docs/narrative-checkpoint-and-fuse.md */
    @Column(name = "narrative_phase", length = 64)
    private String narrativePhase;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "narrative_checkpoint_json")
    private JsonNode narrativeCheckpointJson;

    @Column(name = "autopilot_mode", nullable = false, length = 32)
    private String autopilotMode = "MANUAL";

    @Column(name = "auto_accept_policy", nullable = false, length = 32)
    private String autoAcceptPolicy = "NEVER";

    @Column(name = "max_auto_chapters_per_run", nullable = false)
    private int maxAutoChaptersPerRun = 20;

    @Column(name = "autopilot_chapters_this_run", nullable = false)
    private int autopilotChaptersThisRun = 0;

    @Column(name = "autopilot_paused", nullable = false)
    private boolean autopilotPaused = false;

    @Column(name = "autopilot_pause_reason", columnDefinition = "text")
    private String autopilotPauseReason;

    @Column(name = "pause_on_vector_sync_failed", nullable = false)
    private boolean pauseOnVectorSyncFailed = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "narrative_domain_json")
    private JsonNode narrativeDomainJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "autopilot_last_action_json")
    private JsonNode autopilotLastActionJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "narrative_accept_policy_json")
    private JsonNode narrativeAcceptPolicyJson;

    /** 创作向导入口：standard | skill */
    @Column(name = "setup_mode", length = 32)
    private String setupMode;

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

    public String getNarrativePhase() {
        return narrativePhase;
    }

    public void setNarrativePhase(String narrativePhase) {
        this.narrativePhase = narrativePhase;
    }

    public JsonNode getNarrativeCheckpointJson() {
        return narrativeCheckpointJson;
    }

    public void setNarrativeCheckpointJson(JsonNode narrativeCheckpointJson) {
        this.narrativeCheckpointJson = narrativeCheckpointJson;
    }

    public String getAutopilotMode() {
        return autopilotMode;
    }

    public void setAutopilotMode(String autopilotMode) {
        this.autopilotMode = autopilotMode;
    }

    public String getAutoAcceptPolicy() {
        return autoAcceptPolicy;
    }

    public void setAutoAcceptPolicy(String autoAcceptPolicy) {
        this.autoAcceptPolicy = autoAcceptPolicy;
    }

    public int getMaxAutoChaptersPerRun() {
        return maxAutoChaptersPerRun;
    }

    public void setMaxAutoChaptersPerRun(int maxAutoChaptersPerRun) {
        this.maxAutoChaptersPerRun = maxAutoChaptersPerRun;
    }

    public int getAutopilotChaptersThisRun() {
        return autopilotChaptersThisRun;
    }

    public void setAutopilotChaptersThisRun(int autopilotChaptersThisRun) {
        this.autopilotChaptersThisRun = autopilotChaptersThisRun;
    }

    public boolean getAutopilotPaused() {
        return autopilotPaused;
    }

    public void setAutopilotPaused(boolean autopilotPaused) {
        this.autopilotPaused = autopilotPaused;
    }

    public String getAutopilotPauseReason() {
        return autopilotPauseReason;
    }

    public void setAutopilotPauseReason(String autopilotPauseReason) {
        this.autopilotPauseReason = autopilotPauseReason;
    }

    public boolean isPauseOnVectorSyncFailed() {
        return pauseOnVectorSyncFailed;
    }

    public void setPauseOnVectorSyncFailed(boolean pauseOnVectorSyncFailed) {
        this.pauseOnVectorSyncFailed = pauseOnVectorSyncFailed;
    }

    public JsonNode getNarrativeDomainJson() {
        return narrativeDomainJson;
    }

    public void setNarrativeDomainJson(JsonNode narrativeDomainJson) {
        this.narrativeDomainJson = narrativeDomainJson;
    }

    public JsonNode getAutopilotLastActionJson() {
        return autopilotLastActionJson;
    }

    public void setAutopilotLastActionJson(JsonNode autopilotLastActionJson) {
        this.autopilotLastActionJson = autopilotLastActionJson;
    }

    public JsonNode getNarrativeAcceptPolicyJson() {
        return narrativeAcceptPolicyJson;
    }

    public void setNarrativeAcceptPolicyJson(JsonNode narrativeAcceptPolicyJson) {
        this.narrativeAcceptPolicyJson = narrativeAcceptPolicyJson;
    }

    public String getSetupMode() {
        return setupMode;
    }

    public void setSetupMode(String setupMode) {
        this.setupMode = setupMode;
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
