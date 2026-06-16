package com.mythosforge.chapter.events;

import org.springframework.context.ApplicationEvent;

/** 人工或系统自动 accept 定稿并提交事务后发布。 */
public class ChapterAcceptedEvent extends ApplicationEvent {

    private final String projectId;
    private final int chapterNo;
    private final String commitId;

    public ChapterAcceptedEvent(Object source, String projectId, int chapterNo, String commitId) {
        super(source);
        this.projectId = projectId;
        this.chapterNo = chapterNo;
        this.commitId = commitId;
    }

    public String getProjectId() {
        return projectId;
    }

    public int getChapterNo() {
        return chapterNo;
    }

    public String getCommitId() {
        return commitId;
    }
}
