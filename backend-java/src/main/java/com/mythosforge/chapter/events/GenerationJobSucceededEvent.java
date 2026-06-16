package com.mythosforge.chapter.events;

import org.springframework.context.ApplicationEvent;

/** 异步章节生成任务已成功落库 PENDING_REVIEW 版本后发布。 */
public class GenerationJobSucceededEvent extends ApplicationEvent {

    private final String jobId;

    public GenerationJobSucceededEvent(Object source, String jobId) {
        super(source);
        this.jobId = jobId;
    }

    public String getJobId() {
        return jobId;
    }
}
