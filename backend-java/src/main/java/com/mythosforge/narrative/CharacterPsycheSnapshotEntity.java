package com.mythosforge.narrative;

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
@Table(name = "character_psyche_snapshots")
public class CharacterPsycheSnapshotEntity {

    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "chapter_no", nullable = false)
    private int chapterNo;

    @Column(name = "character_ref", nullable = false)
    private String characterRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "masks_json")
    private JsonNode masksJson;

    @Column(name = "emotional_state")
    private String emotionalState;

    @Column(name = "scars_text")
    private String scarsText;

    @Column(name = "motivations_text")
    private String motivationsText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json")
    private JsonNode snapshotJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
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

    public String getCharacterRef() {
        return characterRef;
    }

    public void setCharacterRef(String characterRef) {
        this.characterRef = characterRef;
    }

    public JsonNode getMasksJson() {
        return masksJson;
    }

    public void setMasksJson(JsonNode masksJson) {
        this.masksJson = masksJson;
    }

    public String getEmotionalState() {
        return emotionalState;
    }

    public void setEmotionalState(String emotionalState) {
        this.emotionalState = emotionalState;
    }

    public String getScarsText() {
        return scarsText;
    }

    public void setScarsText(String scarsText) {
        this.scarsText = scarsText;
    }

    public String getMotivationsText() {
        return motivationsText;
    }

    public void setMotivationsText(String motivationsText) {
        this.motivationsText = motivationsText;
    }

    public JsonNode getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(JsonNode snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
