package com.mythosforge.story;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** JPA 实体 → {@code story_contracts}：一次「初始化快照」，含大纲与关联 novel_seed id。 */
@Entity
@Table(name = "story_contracts")
public class StoryContractEntity {

    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "novel_seed_contract_id")
    private String novelSeedContractId;

    @Column(nullable = false)
    private int version = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode rawJson;

    @Column(name = "first_volume_outline")
    private String firstVolumeOutline;

    /** 作者长期意图（治理面）；注入 Writer story_canon.author_governance.intent */
    @Column(name = "author_intent")
    private String authorIntent;

    /** 不可违背要点 JSON 数组 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "non_negotiables", columnDefinition = "jsonb")
    private JsonNode nonNegotiables;

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

    public String getNovelSeedContractId() {
        return novelSeedContractId;
    }

    public void setNovelSeedContractId(String novelSeedContractId) {
        this.novelSeedContractId = novelSeedContractId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public JsonNode getRawJson() {
        return rawJson;
    }

    public void setRawJson(JsonNode rawJson) {
        this.rawJson = rawJson;
    }

    public String getFirstVolumeOutline() {
        return firstVolumeOutline;
    }

    public void setFirstVolumeOutline(String firstVolumeOutline) {
        this.firstVolumeOutline = firstVolumeOutline;
    }

    public String getAuthorIntent() {
        return authorIntent;
    }

    public void setAuthorIntent(String authorIntent) {
        this.authorIntent = authorIntent;
    }

    public JsonNode getNonNegotiables() {
        return nonNegotiables;
    }

    public void setNonNegotiables(JsonNode nonNegotiables) {
        this.nonNegotiables = nonNegotiables;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
