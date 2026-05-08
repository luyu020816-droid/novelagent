package com.mythosforge.genre;

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
@Table(name = "genre_decision_contracts")
public class GenreDecisionContract {

    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_direction", columnDefinition = "jsonb")
    private JsonNode selectedDirection;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_rankings", columnDefinition = "jsonb")
    private JsonNode candidateRankings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_notes", columnDefinition = "jsonb")
    private JsonNode riskNotes;

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

    public JsonNode getSelectedDirection() {
        return selectedDirection;
    }

    public void setSelectedDirection(JsonNode selectedDirection) {
        this.selectedDirection = selectedDirection;
    }

    public JsonNode getCandidateRankings() {
        return candidateRankings;
    }

    public void setCandidateRankings(JsonNode candidateRankings) {
        this.candidateRankings = candidateRankings;
    }

    public JsonNode getRiskNotes() {
        return riskNotes;
    }

    public void setRiskNotes(JsonNode riskNotes) {
        this.riskNotes = riskNotes;
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
