package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.mythosforge.chapter.dto.GenerationJobQueuedResponse;
import com.mythosforge.chapter.dto.GenerationJobStatusResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 单章生成 SSE（Day 9：生成结束 PENDING_REVIEW；定稿见 {@link ChapterReviewController}）。
 */
@RestController
@RequestMapping("/api/projects/{projectId}/chapters")
public class ChapterController {

    private final ChapterGenerationService chapterGenerationService;
    private final ChapterVersionRepository chapterVersionRepository;
    private final LlmUsageQueryService llmUsageQueryService;
    private final GenerationJobService generationJobService;
    private final TaskExecutor applicationTaskExecutor;

    public ChapterController(
            ChapterGenerationService chapterGenerationService,
            ChapterVersionRepository chapterVersionRepository,
            LlmUsageQueryService llmUsageQueryService,
            GenerationJobService generationJobService,
            @Qualifier("applicationTaskExecutor") TaskExecutor applicationTaskExecutor
    ) {
        this.chapterGenerationService = chapterGenerationService;
        this.chapterVersionRepository = chapterVersionRepository;
        this.llmUsageQueryService = llmUsageQueryService;
        this.generationJobService = generationJobService;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @GetMapping("/{chapterNo}/versions/latest")
    public ChapterVersionSnapshot latestVersion(
            @PathVariable String projectId,
            @PathVariable int chapterNo
    ) {
        ChapterVersionEntity v = chapterVersionRepository
                .findFirstByProjectIdAndChapterNoOrderByVersionDesc(projectId, chapterNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        JsonNode critic = v.getCriticReportJson();
        boolean pass = critic != null && !critic.isNull() && critic.path("pass").asBoolean(false);
        String raw = v.getChapterText() != null ? v.getChapterText() : "";
        String styled = v.getStyledText() != null ? v.getStyledText() : "";
        return new ChapterVersionSnapshot(
                v.getId(),
                v.getProjectId(),
                v.getChapterNo(),
                v.getVersion(),
                v.getStatus(),
                raw,
                styled,
                v.getTokenBudgetStatusJson(),
                v.getLlmUsageSummaryJson(),
                v.getScenePlanJson(),
                critic,
                pass
        );
    }

    @GetMapping("/usage-by-chapter")
    public List<ChapterUsageAggregateRow> usageByChapter(@PathVariable String projectId) {
        return llmUsageQueryService.aggregateTokensByChapter(projectId);
    }

    @PostMapping(value = "/{chapterNo}/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(
            @PathVariable String projectId,
            @PathVariable int chapterNo,
            @RequestBody(required = false) ChapterGenerateBody body
    ) {
        chapterGenerationService.requireProjectStoryAndChapter(projectId, chapterNo);
        SseEmitter emitter = new SseEmitter(900_000L);
        applicationTaskExecutor.execute(
                () -> chapterGenerationService.generateChapterStream(projectId, chapterNo, emitter, body)
        );
        return emitter;
    }

    /** Day 14：异步排队生成，立即返回 jobId（Python Worker 消费 RabbitMQ）。 */
    @PostMapping("/{chapterNo}/generate-async")
    public GenerationJobQueuedResponse generateAsync(
            @PathVariable String projectId,
            @PathVariable int chapterNo,
            @RequestBody(required = false) ChapterGenerateBody body
    ) {
        ChapterGenerateBody b = body != null ? body : ChapterGenerateBody.empty();
        return generationJobService.enqueue(projectId, chapterNo, b);
    }

    @GetMapping("/{chapterNo}/generation-jobs/latest")
    public ResponseEntity<GenerationJobStatusResponse> latestGenerationJob(
            @PathVariable String projectId,
            @PathVariable int chapterNo
    ) {
        chapterGenerationService.requireProjectStoryAndChapter(projectId, chapterNo);
        return generationJobService.latestJob(projectId, chapterNo)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
