package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.narrative.ChapterObligationsService;
import com.mythosforge.narrative.NarrativeFulfillmentService;
import com.mythosforge.narrative.NarrativePostAcceptService;
import com.mythosforge.narrative.NarrativeStructureService;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.writer.WriterHttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * 定稿后一条管收口：Writer aftermath 同步 + 向量 / 叙事 / 履约异步链路。
 */
@Service
public class AftermathPipelineService {

    private static final Logger log = LoggerFactory.getLogger(AftermathPipelineService.class);

    private final WriterHttpService writerHttpService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final TaskExecutor applicationTaskExecutor;
    private final ChapterVersionRepository chapterVersionRepository;
    private final ChapterCommitRepository chapterCommitRepository;
    private final ProjectRepository projectRepository;
    private final NarrativePostAcceptService narrativePostAcceptService;
    private final NarrativeFulfillmentService narrativeFulfillmentService;
    private final NarrativeStructureService narrativeStructureService;
    private final ChapterObligationsService chapterObligationsService;

    public AftermathPipelineService(
            WriterHttpService writerHttpService,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            @Qualifier("applicationTaskExecutor") TaskExecutor applicationTaskExecutor,
            ChapterVersionRepository chapterVersionRepository,
            ChapterCommitRepository chapterCommitRepository,
            ProjectRepository projectRepository,
            NarrativePostAcceptService narrativePostAcceptService,
            NarrativeFulfillmentService narrativeFulfillmentService,
            NarrativeStructureService narrativeStructureService,
            ChapterObligationsService chapterObligationsService
    ) {
        this.writerHttpService = writerHttpService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.chapterVersionRepository = chapterVersionRepository;
        this.chapterCommitRepository = chapterCommitRepository;
        this.projectRepository = projectRepository;
        this.narrativePostAcceptService = narrativePostAcceptService;
        this.narrativeFulfillmentService = narrativeFulfillmentService;
        this.narrativeStructureService = narrativeStructureService;
        this.chapterObligationsService = chapterObligationsService;
    }

    /** 同步：Writer 摘要 + Lore 抽取。 */
    public JsonNode runSyncAftermath(String projectId, int chapterNo, String chapterText) {
        ObjectNode sumBody = objectMapper.createObjectNode();
        sumBody.put("projectId", projectId);
        sumBody.put("chapterNo", chapterNo);
        sumBody.put("chapterText", chapterText);
        log.info("[AftermathPipeline] sync aftermath start project={} chapter={}", projectId, chapterNo);
        JsonNode sumResp = writerHttpService.postJson("/api/writer/chapters/aftermath", sumBody);
        JsonNode summary = sumResp.get("summary");
        if (summary == null || summary.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Writer aftermath 未返回 summary");
        }
        log.info("[AftermathPipeline] sync aftermath ok project={} chapter={}", projectId, chapterNo);
        return summary;
    }

    /** 异步：向量同步、叙事回写、履约评估。 */
    public void scheduleAsyncAfterAccept(
            String projectId,
            int chapterNo,
            String chapterText,
            String commitId,
            String versionId,
            JsonNode summary
    ) {
        applicationTaskExecutor.execute(() -> runVectorSyncAsync(projectId, chapterNo, chapterText, commitId));
        applicationTaskExecutor.execute(() -> runNarrativePostAcceptAsync(projectId, chapterNo, summary));
        applicationTaskExecutor.execute(() -> runFulfillmentAsync(versionId, projectId, chapterNo, chapterText));
    }

    private void runNarrativePostAcceptAsync(String projectId, int chapterNo, JsonNode summary) {
        log.info("[AftermathPipeline] async narrative post-accept start project={} chapter={}", projectId, chapterNo);
        try {
            narrativePostAcceptService.applyAfterAccept(projectId, chapterNo, summary);
            syncNarrativeChapterContextNeo4j(projectId, chapterNo);
            syncNarrativeStructureNeo4j(projectId);
            log.info("[AftermathPipeline] async narrative post-accept ok project={} chapter={}", projectId, chapterNo);
        } catch (Exception ex) {
            log.warn("[AftermathPipeline] async narrative post-accept failed project={} chapter={}: {}", projectId, chapterNo, ex.getMessage());
        }
    }

    private void runFulfillmentAsync(String versionId, String projectId, int chapterNo, String text) {
        try {
            ObjectNode report = narrativeFulfillmentService.evaluate(projectId, chapterNo, text);
            transactionTemplate.executeWithoutResult(ts -> {
                chapterVersionRepository.findById(versionId).ifPresent(v -> {
                    v.setFulfillmentReportJson(report);
                    chapterVersionRepository.save(v);
                });
            });
        } catch (Exception ex) {
            log.warn("fulfillment async failed version={}: {}", versionId, ex.getMessage());
        }
    }

    private void syncNarrativeStructureNeo4j(String projectId) {
        try {
            ObjectNode exported = narrativeStructureService.exportDomainFromPg(projectId);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("projectId", projectId);
            body.set("storylines", exported.get("storylines"));
            body.set("confluences", exported.get("confluences"));
            writerHttpService.postJson("/api/writer/lore/narrative-structure-sync", body);
        } catch (Exception ex) {
            log.warn("neo4j narrative structure sync failed project={}: {}", projectId, ex.getMessage());
        }
    }

    private void syncNarrativeChapterContextNeo4j(String projectId, int chapterNo) {
        try {
            ObjectNode ob = chapterObligationsService.buildChapterObligations(projectId, chapterNo, true);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("projectId", projectId);
            body.put("chapterNo", chapterNo);
            body.set("chapterObligations", ob);
            writerHttpService.postJson("/api/writer/lore/narrative-chapter-context", body);
        } catch (Exception ex) {
            log.warn(
                    "neo4j narrative chapter context sync failed project={} chapter={}: {}",
                    projectId,
                    chapterNo,
                    ex.getMessage()
            );
        }
    }

    /** 人工重试向量同步。 */
    public void retryVectorSync(String projectId, int chapterNo, String chapterText, String commitId) {
        applicationTaskExecutor.execute(() -> runVectorSyncAsync(projectId, chapterNo, chapterText, commitId));
    }

    private void runVectorSyncAsync(String projectId, int chapterNo, String chapterText, String commitId) {
        log.info("[AftermathPipeline] async vector sync start project={} chapter={} commit={}", projectId, chapterNo, commitId);
        try {
            ObjectNode syncBody = objectMapper.createObjectNode();
            syncBody.put("projectId", projectId);
            syncBody.put("chapterNo", chapterNo);
            syncBody.put("chapterText", chapterText);
            writerHttpService.postJson("/api/writer/knowledge/sync", syncBody);
            markCommitVectorSync(commitId, "OK", null);
            log.info("[AftermathPipeline] async vector sync ok project={} chapter={} commit={}", projectId, chapterNo, commitId);
        } catch (RestClientResponseException ex) {
            String msg = ex.getStatusCode() + " " + safeMsg(ex.getMessage());
            if (ex.getStatusCode().value() == 503) {
                markCommitVectorSync(commitId, "SKIPPED", msg);
            } else {
                markCommitVectorSync(commitId, "FAILED", msg);
            }
            log.warn("[AftermathPipeline] async vector sync failed project={} chapter={} commit={}: {}", projectId, chapterNo, commitId, msg);
        } catch (Exception ex) {
            String msg = safeMsg(ex.getMessage());
            markCommitVectorSync(commitId, "FAILED", msg);
            log.warn("vector sync failed project={} chapter={} commit={}: {}", projectId, chapterNo, commitId, msg);
        }
    }

    private static String safeMsg(String m) {
        if (m == null) {
            return "";
        }
        return m.length() > 2000 ? m.substring(0, 2000) : m;
    }

    private void markCommitVectorSync(String commitId, String status, String error) {
        transactionTemplate.executeWithoutResult(ts -> {
            ChapterCommitEntity c = chapterCommitRepository.findById(commitId).orElse(null);
            if (c == null) {
                return;
            }
            c.setVectorSyncStatus(status);
            c.setVectorSyncError(error);
            c.setVectorSyncAt(Instant.now());
            c.setVectorSyncAttempts(c.getVectorSyncAttempts() + 1);
            chapterCommitRepository.save(c);
            if ("FAILED".equals(status) && c.getProjectId() != null) {
                Project proj = projectRepository.findById(c.getProjectId()).orElse(null);
                if (proj != null && proj.isPauseOnVectorSyncFailed()) {
                    proj.setAutopilotPaused(true);
                    String shortErr = error != null && error.length() > 800 ? error.substring(0, 800) : error;
                    proj.setAutopilotPauseReason("vector_sync_failed: " + (shortErr != null ? shortErr : ""));
                    proj.setNarrativePhase("PAUSED_ERROR");
                    projectRepository.save(proj);
                }
            }
        });
    }
}
