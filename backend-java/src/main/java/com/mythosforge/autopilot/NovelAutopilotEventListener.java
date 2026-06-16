package com.mythosforge.autopilot;

import com.mythosforge.chapter.events.ChapterAcceptedEvent;
import com.mythosforge.chapter.events.GenerationJobSucceededEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class NovelAutopilotEventListener {

    private final TaskExecutor applicationTaskExecutor;
    private final NovelAutopilotOrchestrator novelAutopilotOrchestrator;

    public NovelAutopilotEventListener(
            @Qualifier("applicationTaskExecutor") TaskExecutor applicationTaskExecutor,
            NovelAutopilotOrchestrator novelAutopilotOrchestrator
    ) {
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.novelAutopilotOrchestrator = novelAutopilotOrchestrator;
    }

    @EventListener
    public void onGenerationJobSucceeded(GenerationJobSucceededEvent event) {
        String jobId = event.getJobId();
        applicationTaskExecutor.execute(() -> novelAutopilotOrchestrator.onGenerationSucceeded(jobId));
    }

    @EventListener
    public void onChapterAccepted(ChapterAcceptedEvent event) {
        applicationTaskExecutor.execute(
                () -> novelAutopilotOrchestrator.onChapterAccepted(
                        event.getProjectId(),
                        event.getChapterNo(),
                        event.getCommitId()
                )
        );
    }
}
