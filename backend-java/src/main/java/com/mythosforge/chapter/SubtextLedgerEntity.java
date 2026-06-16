package com.mythosforge.chapter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "subtext_ledger")
public class SubtextLedgerEntity {

    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "chapter_no", nullable = false)
    private int chapterNo;

    @Column(name = "character_ref")
    private String characterRef;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(nullable = false, length = 16)
    private String status = "pending";

    @Column(name = "suggested_resolve_chapter")
    private Integer suggestedResolveChapter;

    @Column(name = "consumed_at_chapter")
    private Integer consumedAtChapter;

    @Column(nullable = false, length = 16)
    private String importance = "medium";

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

    public String getCharacterRef() {
        return characterRef;
    }

    public void setCharacterRef(String characterRef) {
        this.characterRef = characterRef;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSuggestedResolveChapter() {
        return suggestedResolveChapter;
    }

    public void setSuggestedResolveChapter(Integer suggestedResolveChapter) {
        this.suggestedResolveChapter = suggestedResolveChapter;
    }

    public Integer getConsumedAtChapter() {
        return consumedAtChapter;
    }

    public void setConsumedAtChapter(Integer consumedAtChapter) {
        this.consumedAtChapter = consumedAtChapter;
    }

    public String getImportance() {
        return importance;
    }

    public void setImportance(String importance) {
        this.importance = importance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
