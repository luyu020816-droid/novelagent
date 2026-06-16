package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.dag.ProjectDagService;
import com.mythosforge.narrative.ChapterObligationsService;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.story.StoryContractEntity;
import com.mythosforge.story.StoryContractRepository;
import com.mythosforge.writer.WriterSseProxyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Day 7–9：SSE 调用 Writer LangGraph；生成结束仅写 {@code chapter_versions}
 * {@link ChapterVersionStatuses#PENDING_REVIEW}；定稿摘要与 commit 见 {@link ChapterReviewService}。
 */
@Service
public class ChapterGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ChapterGenerationService.class);

    private final ProjectRepository projectRepository;
    private final StoryContractRepository storyContractRepository;
    private final ChapterContractRepository chapterContractRepository;
    private final ChapterVersionRepository chapterVersionRepository;
    private final ChapterCommitRepository chapterCommitRepository;
    private final WriterSseProxyService writerSseProxyService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ChapterPrewritePlanService chapterPrewritePlanService;
    private final ChapterObligationsService chapterObligationsService;
    private final ProjectDagService projectDagService;

    public ChapterGenerationService(
            ProjectRepository projectRepository,
            StoryContractRepository storyContractRepository,
            ChapterContractRepository chapterContractRepository,
            ChapterVersionRepository chapterVersionRepository,
            ChapterCommitRepository chapterCommitRepository,
            WriterSseProxyService writerSseProxyService,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            ChapterPrewritePlanService chapterPrewritePlanService,
            ChapterObligationsService chapterObligationsService,
            ProjectDagService projectDagService
    ) {
        this.projectRepository = projectRepository;
        this.storyContractRepository = storyContractRepository;
        this.chapterContractRepository = chapterContractRepository;
        this.chapterVersionRepository = chapterVersionRepository;
        this.chapterCommitRepository = chapterCommitRepository;
        this.writerSseProxyService = writerSseProxyService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.chapterPrewritePlanService = chapterPrewritePlanService;
        this.chapterObligationsService = chapterObligationsService;
        this.projectDagService = projectDagService;
    }

    /**
     * 组装 Writer LangGraph 入口 JSON（SSE 与异步任务共用）。
     */
    public ObjectNode buildWriterPayload(String projectId, int chapterNo, ChapterGenerateBody generateBody) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String sid = p.getSelectedStoryContractId();
        if (sid == null || sid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先在初始化页选定一套 Story 快照");
        }
        StoryContractEntity story = storyContractRepository.findById(sid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "选中的初始化快照不存在"));
        JsonNode chapterJson = chapterContractRepository
                .findByStoryContractIdAndChapterNo(sid, chapterNo)
                .map(ChapterContractEntity::getRawJson)
                .orElse(null);
        if (chapterJson == null || chapterJson.isNull() || !chapterJson.isObject()) {
            chapterJson = objectMapper.createObjectNode();
        }

        ArrayNode historySummaries = objectMapper.createArrayNode();
        List<ChapterCommitEntity> priorCommits = chapterCommitRepository
                .findByProjectIdAndChapterNoLessThanAndStatusOrderByChapterNoAscVersionAsc(
                        projectId, chapterNo, "accepted");
        for (ChapterCommitEntity c : priorCommits) {
            JsonNode sum = c.getSummary();
            if (sum == null || sum.isNull()) {
                continue;
            }
            ObjectNode item = objectMapper.createObjectNode();
            item.put("chapterNo", c.getChapterNo());
            item.set("summary", sum);
            historySummaries.add(item);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("projectId", projectId);
        payload.put("chapterNo", chapterNo);
        payload.put("retryCount", 0);
        String userNotes = "";
        if (generateBody != null && generateBody.userRewriteNotes() != null) {
            userNotes = generateBody.userRewriteNotes().trim();
        }
        payload.put("userRewriteNotes", userNotes);
        String rewriteMode = "plot";
        if (generateBody != null && generateBody.rewriteMode() != null && !generateBody.rewriteMode().isBlank()) {
            rewriteMode = generateBody.rewriteMode().trim();
        }
        payload.put("rewriteMode", rewriteMode);
        JsonNode storyJson = story.getRawJson();
        ObjectNode storyPayload = storyJson != null && storyJson.isObject()
                ? (ObjectNode) storyJson.deepCopy()
                : objectMapper.createObjectNode();
        if (story.getAuthorIntent() != null && !story.getAuthorIntent().isBlank()) {
            storyPayload.put("authorIntent", story.getAuthorIntent().trim());
        }
        if (story.getNonNegotiables() != null && !story.getNonNegotiables().isNull()) {
            storyPayload.set("nonNegotiables", story.getNonNegotiables());
        }
        payload.set("storyContract", storyPayload);
        payload.set("chapterContract", chapterJson);
        payload.set("historySummaries", historySummaries);
        payload.set("recentSummaries", objectMapper.createArrayNode());
        String fanPreset = p.getFanSeriesPreset();
        if (fanPreset != null && !fanPreset.isBlank()) {
            payload.put("fanSeriesPreset", fanPreset.trim());
        }
        String plan = chapterPrewritePlanService.confirmedPlanSummaryText(projectId, chapterNo);
        if (plan != null && !plan.isBlank()) {
            payload.put("confirmedChapterPlanSummary", plan);
        }
        try {
            payload.set("chapterObligations", chapterObligationsService.buildChapterObligations(projectId, chapterNo, true));
        } catch (Exception ex) {
            log.warn("chapterObligations build failed project={} chapter={}: {}", projectId, chapterNo, ex.getMessage());
        }
        JsonNode dagDef = projectDagService.getActiveDagForWriter(projectId);
        if (dagDef != null && dagDef.isObject()) {
            payload.set("dagDefinition", dagDef);
        }
        return payload;
    }

    /** 须在返回 SseEmitter 之前同步调用，校验项目 / 选题 / 章纲存在。 */
    public void requireProjectStoryAndChapter(String projectId, int chapterNo) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String sid = p.getSelectedStoryContractId();
        if (sid == null || sid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先在初始化页选定一套 Story 快照");
        }
        StoryContractEntity story = storyContractRepository.findById(sid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "选中的初始化快照不存在"));
        if (!projectId.equals(story.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "快照不属于该项目");
        }
    }

    public void generateChapterStream(
            String projectId,
            int chapterNo,
            SseEmitter emitter,
            ChapterGenerateBody generateBody
    ) {
        ChapterGenerateBody b = generateBody != null ? generateBody : ChapterGenerateBody.empty();
        chapterPrewritePlanService.requireConfirmedPlanForGeneration(projectId, chapterNo);
        ObjectNode payload = buildWriterPayload(projectId, chapterNo, b);

        writerSseProxyService.proxySsePost(
                "/api/writer/chapters/generate",
                payload.toString(),
                emitter,
                (kind, data) -> {
                },
                (fin, em) -> persistPendingReview(projectId, chapterNo, fin, em)
        );
    }

    /** 将 Writer 成品写入 {@code chapter_versions}（SSE 与异步 Job 共用）。 */
    public ChapterVersionEntity persistPendingReviewVersion(String projectId, int chapterNo, JsonNode fin) {
        String text = fin.path("chapter_text").asText("");
        String styled = fin.path("styled_text").asText("");
        JsonNode scenePlan = fin.get("scene_plan");
        JsonNode critic = fin.get("critic_report");
        JsonNode tokenBudgetStatus = fin.get("token_budget_status");
        JsonNode llmUsageSummary = fin.get("llm_usage_summary");

        ChapterVersionEntity saved = transactionTemplate.execute(status -> {
            int nextVer = chapterVersionRepository.findMaxVersion(projectId, chapterNo) + 1;

            ChapterVersionEntity ver = new ChapterVersionEntity();
            ver.setId(UUID.randomUUID().toString().replace("-", ""));
            ver.setProjectId(projectId);
            ver.setChapterNo(chapterNo);
            ver.setVersion(nextVer);
            ver.setStatus(ChapterVersionStatuses.PENDING_REVIEW);
            ver.setScenePlanJson(scenePlan);
            ver.setChapterText(text.isBlank() ? null : text);
            ver.setStyledText(styled.isBlank() ? null : styled);
            ver.setTokenBudgetStatusJson(
                    tokenBudgetStatus == null || tokenBudgetStatus.isNull() ? null : tokenBudgetStatus
            );
            ver.setLlmUsageSummaryJson(
                    llmUsageSummary == null || llmUsageSummary.isNull() ? null : llmUsageSummary
            );
            ver.setCriticReportJson(critic);
            ver.setRewriteInstructionJson(null);
            return chapterVersionRepository.save(ver);
        });

        if (saved == null) {
            throw new IllegalStateException("persist chapter_version failed");
        }
        return saved;
    }

    private void persistPendingReview(
            String projectId,
            int chapterNo,
            JsonNode fin,
            SseEmitter emitter
    ) throws Exception {
        ChapterVersionEntity saved = persistPendingReviewVersion(projectId, chapterNo, fin);

        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("kind", "chapter_version_pending");
        ObjectNode data = objectMapper.createObjectNode();
        data.put("versionId", saved.getId());
        data.put("status", ChapterVersionStatuses.PENDING_REVIEW);
        data.put("chapterNo", chapterNo);
        data.put("projectId", projectId);
        data.put("aiAccepted", fin.path("accepted").asBoolean(false));
        JsonNode tbs = fin.get("token_budget_status");
        if (tbs != null && !tbs.isNull()) {
            data.set("tokenBudgetStatus", tbs);
        }
        JsonNode lus = fin.get("llm_usage_summary");
        if (lus != null && !lus.isNull()) {
            data.set("llmUsageSummary", lus);
        }
        envelope.set("data", data);
        emitter.send(SseEmitter.event().name("artifact").data(objectMapper.writeValueAsString(envelope)));
    }

    public ChapterVersionEntity importPolishedDraft(String projectId, int chapterNo, String chapterText, String styledText) {
        requireProjectStoryAndChapter(projectId, chapterNo);
        if (chapterText == null || chapterText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chapterText 不能为空");
        }
        ChapterVersionEntity saved = transactionTemplate.execute(status -> {
            int nextVer = chapterVersionRepository.findMaxVersion(projectId, chapterNo) + 1;
            ChapterVersionEntity ver = new ChapterVersionEntity();
            ver.setId(UUID.randomUUID().toString().replace("-", ""));
            ver.setProjectId(projectId);
            ver.setChapterNo(chapterNo);
            ver.setVersion(nextVer);
            ver.setStatus(ChapterVersionStatuses.PENDING_REVIEW);
            ver.setChapterText(chapterText);
            ver.setStyledText(styledText != null && !styledText.isBlank() ? styledText.trim() : null);
            ver.setScenePlanJson(null);
            ObjectNode critic = objectMapper.createObjectNode();
            critic.put("pass", true);
            critic.putArray("notes").add("external_polish_import");
            ver.setCriticReportJson(critic);
            ver.setTokenBudgetStatusJson(null);
            ver.setLlmUsageSummaryJson(null);
            ver.setRewriteInstructionJson(null);
            return chapterVersionRepository.save(ver);
        });
        if (saved == null) {
            throw new IllegalStateException("import draft failed");
        }
        return saved;
    }
}
