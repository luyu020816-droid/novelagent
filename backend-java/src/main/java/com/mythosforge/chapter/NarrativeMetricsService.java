package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.autopilot.AutopilotModes;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.writer.WriterHttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/** 定稿后：调用 Writer 估算张力/文风相似度并落库 chapter_narrative_metrics；越阈时可自动排队同章修写。 */
@Service
public class NarrativeMetricsService {

    private static final Logger log = LoggerFactory.getLogger(NarrativeMetricsService.class);

    private static final double TENSION_THRESHOLD = 3.0;
    private static final double STYLE_THRESHOLD = 0.55;

    private final WriterHttpService writerHttpService;
    private final ChapterNarrativeMetricsRepository chapterNarrativeMetricsRepository;
    private final GenerationJobRepository generationJobRepository;
    private final GenerationJobService generationJobService;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public NarrativeMetricsService(
            WriterHttpService writerHttpService,
            ChapterNarrativeMetricsRepository chapterNarrativeMetricsRepository,
            GenerationJobRepository generationJobRepository,
            GenerationJobService generationJobService,
            ProjectRepository projectRepository,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate
    ) {
        this.writerHttpService = writerHttpService;
        this.chapterNarrativeMetricsRepository = chapterNarrativeMetricsRepository;
        this.generationJobRepository = generationJobRepository;
        this.generationJobService = generationJobService;
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public void recordMetricsAsync(String projectId, int chapterNo, String commitId, String chapterText) {
        if (chapterText == null || chapterText.isBlank()) {
            return;
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("projectId", projectId);
        body.put("chapterNo", chapterNo);
        body.put("chapterText", chapterText.length() > 60000 ? chapterText.substring(0, 60000) : chapterText);
        double tension = Double.NaN;
        double style = Double.NaN;
        JsonNode raw = null;
        try {
            JsonNode resp = writerHttpService.postJson("/api/writer/chapters/narrative-metrics", body);
            tension = resp.path("tensionScore").asDouble(Double.NaN);
            style = resp.path("styleSimilarity").asDouble(Double.NaN);
            raw = resp.get("raw");
            if (raw == null || raw.isNull()) {
                raw = resp;
            }
        } catch (Exception e) {
            log.warn("narrative metrics failed project={} ch={}: {}", projectId, chapterNo, e.getMessage());
            return;
        }
        final double tensionFinal = tension;
        final double styleFinal = style;
        final JsonNode rawFinal = raw;
        transactionTemplate.executeWithoutResult(ts -> {
            ChapterNarrativeMetricsEntity row = new ChapterNarrativeMetricsEntity();
            row.setId(UUID.randomUUID().toString().replace("-", ""));
            row.setProjectId(projectId);
            row.setChapterNo(chapterNo);
            row.setCommitId(commitId);
            row.setTensionScore(Double.isNaN(tensionFinal) ? null : tensionFinal);
            row.setStyleSimilarity(Double.isNaN(styleFinal) ? null : styleFinal);
            row.setRawJson(rawFinal);
            chapterNarrativeMetricsRepository.save(row);
        });
        maybeEnqueueRewriteForMetrics(projectId, chapterNo, tension, Double.isNaN(style) ? null : style);
    }

    private void maybeEnqueueRewriteForMetrics(String projectId, int chapterNo, double tension, Double styleSim) {
        boolean badTension = !Double.isNaN(tension) && tension < TENSION_THRESHOLD;
        boolean badStyle = styleSim != null && styleSim < STYLE_THRESHOLD;
        if (!badTension && !badStyle) {
            return;
        }
        Project p = projectRepository.findById(projectId).orElse(null);
        if (p == null || p.getAutopilotPaused()) {
            return;
        }
        String mode = p.getAutopilotMode() != null ? p.getAutopilotMode() : AutopilotModes.MANUAL;
        if (AutopilotModes.MANUAL.equals(mode)) {
            return;
        }
        if (generationJobRepository.existsByProjectIdAndChapterNoAndStatusIn(
                projectId,
                chapterNo,
                List.of(GenerationJobStatuses.PENDING, GenerationJobStatuses.RUNNING)
        )) {
            return;
        }
        try {
            generationJobService.enqueue(
                    projectId,
                    chapterNo,
                    new ChapterGenerateBody(
                            "章后叙事指标未达标（张力/文风），系统自动发起修写。",
                            "anti_ai"
                    )
            );
        } catch (Exception e) {
            log.warn("metrics rewrite enqueue failed project={} ch={}: {}", projectId, chapterNo, e.getMessage());
        }
    }
}
