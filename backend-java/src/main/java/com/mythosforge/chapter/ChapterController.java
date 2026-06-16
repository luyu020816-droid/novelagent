package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.chapter.dto.ChapterFanqieReviewRequest;
import com.mythosforge.chapter.dto.ChapterLatestCommitVectorDto;
import com.mythosforge.chapter.dto.ChapterPolishWithNotesRequest;
import com.mythosforge.chapter.dto.ChapterPrewritePlanResponse;
import com.mythosforge.chapter.dto.ChapterPrewritePlanSaveRequest;
import com.mythosforge.chapter.dto.GenerationJobQueuedResponse;
import com.mythosforge.chapter.dto.GenerationJobStatusResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 单章生成 SSE、异步队列、动笔前摘要、番茄编辑与润色辅助。
 */
@RestController
@RequestMapping("/api/projects/{projectId}/chapters")
public class ChapterController {

    private final ChapterGenerationService chapterGenerationService;
    private final ChapterVersionRepository chapterVersionRepository;
    private final LlmUsageQueryService llmUsageQueryService;
    private final GenerationJobService generationJobService;
    private final ChapterPrewritePlanService chapterPrewritePlanService;
    private final ChapterEditorAssistService chapterEditorAssistService;
    private final ChapterReviewService chapterReviewService;
    private final TaskExecutor applicationTaskExecutor;

    public ChapterController(
            ChapterGenerationService chapterGenerationService,
            ChapterVersionRepository chapterVersionRepository,
            LlmUsageQueryService llmUsageQueryService,
            GenerationJobService generationJobService,
            ChapterPrewritePlanService chapterPrewritePlanService,
            ChapterEditorAssistService chapterEditorAssistService,
            ChapterReviewService chapterReviewService,
            @Qualifier("applicationTaskExecutor") TaskExecutor applicationTaskExecutor
    ) {
        this.chapterGenerationService = chapterGenerationService;
        this.chapterVersionRepository = chapterVersionRepository;
        this.llmUsageQueryService = llmUsageQueryService;
        this.generationJobService = generationJobService;
        this.chapterPrewritePlanService = chapterPrewritePlanService;
        this.chapterEditorAssistService = chapterEditorAssistService;
        this.chapterReviewService = chapterReviewService;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    private static ChapterVersionSnapshot toSnapshot(ChapterVersionEntity v) {
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

    /**
     * 尚无版本时返回 404（无 ResponseStatusException），避免被前端高频轮询时刷满 WARN 日志。
     */
    @GetMapping("/{chapterNo}/versions/latest")
    public ResponseEntity<ChapterVersionSnapshot> latestVersion(
            @PathVariable String projectId,
            @PathVariable int chapterNo
    ) {
        return chapterVersionRepository
                .findFirstByProjectIdAndChapterNoOrderByVersionDesc(projectId, chapterNo)
                .map(ChapterController::toSnapshot)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 该章最新已定稿 commit 的向量同步状态（工作区展示 / 重试入口用）。 */
    @GetMapping("/{chapterNo}/commits/latest-vector")
    public ResponseEntity<ChapterLatestCommitVectorDto> latestAcceptedCommitVector(
            @PathVariable String projectId,
            @PathVariable int chapterNo
    ) {
        return chapterReviewService.findLatestAcceptedCommitVector(projectId, chapterNo)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{chapterNo}/prewrite-plan")
    public ChapterPrewritePlanResponse getPrewritePlan(
            @PathVariable String projectId,
            @PathVariable int chapterNo
    ) {
        return chapterPrewritePlanService.getPlan(projectId, chapterNo);
    }

    @PutMapping("/{chapterNo}/prewrite-plan")
    public ChapterPrewritePlanResponse savePrewritePlan(
            @PathVariable String projectId,
            @PathVariable int chapterNo,
            @RequestBody(required = false) ChapterPrewritePlanSaveRequest body
    ) {
        String text = body != null && body.planSummary() != null ? body.planSummary() : "";
        return chapterPrewritePlanService.savePlan(projectId, chapterNo, text);
    }

    @PostMapping("/{chapterNo}/prewrite-plan/confirm")
    public ChapterPrewritePlanResponse confirmPrewritePlan(
            @PathVariable String projectId,
            @PathVariable int chapterNo
    ) {
        return chapterPrewritePlanService.confirmPlan(projectId, chapterNo);
    }

    @PostMapping("/{chapterNo}/prewrite-plan/propose-ai")
    public Map<String, String> proposePrewritePlanAi(
            @PathVariable String projectId,
            @PathVariable int chapterNo
    ) {
        String plan = chapterPrewritePlanService.proposePlanSummaryAi(projectId, chapterNo);
        return Map.of("planSummary", plan);
    }

    @PostMapping("/{chapterNo}/fanqie-editor-review")
    public JsonNode fanqieEditorReview(
            @PathVariable String projectId,
            @PathVariable int chapterNo,
            @RequestBody(required = false) ChapterFanqieReviewRequest body
    ) {
        String override = body != null ? body.chapterText() : null;
        return chapterEditorAssistService.fanqieEditorReview(projectId, chapterNo, override);
    }

    @PostMapping("/{chapterNo}/polish-with-notes")
    public JsonNode polishWithNotes(
            @PathVariable String projectId,
            @PathVariable int chapterNo,
            @RequestBody ChapterPolishWithNotesRequest body
    ) {
        return chapterEditorAssistService.polishWithNotes(
                projectId,
                chapterNo,
                body.chapterText(),
                body.tomatoReview(),
                body.authorNotes()
        );
    }

    @PostMapping("/{chapterNo}/import-polished-draft")
    public ChapterVersionSnapshot importPolishedDraft(
            @PathVariable String projectId,
            @PathVariable int chapterNo,
            @RequestBody ObjectNode body
    ) {
        String text = body.path("chapterText").asText("");
        String styled = body.path("styledText").asText("");
        ChapterVersionEntity v = chapterGenerationService.importPolishedDraft(projectId, chapterNo, text, styled);
        return toSnapshot(v);
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
        chapterPrewritePlanService.requireConfirmedPlanForGeneration(projectId, chapterNo);
        SseEmitter emitter = new SseEmitter(900_000L);
        applicationTaskExecutor.execute(
                () -> chapterGenerationService.generateChapterStream(projectId, chapterNo, emitter, body)
        );
        return emitter;
    }

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
