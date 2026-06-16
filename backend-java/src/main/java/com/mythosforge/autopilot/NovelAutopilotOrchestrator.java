package com.mythosforge.autopilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.chapter.ChapterGenerateBody;
import com.mythosforge.chapter.ChapterNarrativeMetricsEntity;
import com.mythosforge.chapter.ChapterNarrativeMetricsRepository;
import com.mythosforge.chapter.ChapterReviewService;
import com.mythosforge.chapter.ChapterVersionEntity;
import com.mythosforge.chapter.ChapterVersionRepository;
import com.mythosforge.chapter.ChapterVersionStatuses;
import com.mythosforge.chapter.GenerationJobEntity;
import com.mythosforge.chapter.GenerationJobRepository;
import com.mythosforge.chapter.GenerationJobService;
import com.mythosforge.chapter.GenerationJobStatuses;
import com.mythosforge.narrative.ChapterObligationsService;
import com.mythosforge.narrative.NarrativeAutopilotGate;
import com.mythosforge.narrative.NarrativeFulfillmentService;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

/**
 * 全书自动驾驶编排：生成成功后的自动定稿、定稿后的自动排队下一章。
 */
@Service
public class NovelAutopilotOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(NovelAutopilotOrchestrator.class);

    private final GenerationJobRepository generationJobRepository;
    private final ChapterVersionRepository chapterVersionRepository;
    private final ProjectRepository projectRepository;
    private final ChapterReviewService chapterReviewService;
    private final GenerationJobService generationJobService;
    private final ChapterNarrativeMetricsRepository chapterNarrativeMetricsRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ChapterObligationsService chapterObligationsService;
    private final NarrativeFulfillmentService narrativeFulfillmentService;
    private final int defaultSubtextWindowRadius;

    public NovelAutopilotOrchestrator(
            GenerationJobRepository generationJobRepository,
            ChapterVersionRepository chapterVersionRepository,
            ProjectRepository projectRepository,
            ChapterReviewService chapterReviewService,
            GenerationJobService generationJobService,
            ChapterNarrativeMetricsRepository chapterNarrativeMetricsRepository,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            ChapterObligationsService chapterObligationsService,
            NarrativeFulfillmentService narrativeFulfillmentService,
            @Value("${mythosforge.narrative.subtext-window-radius:1}") int defaultSubtextWindowRadius
    ) {
        this.generationJobRepository = generationJobRepository;
        this.chapterVersionRepository = chapterVersionRepository;
        this.projectRepository = projectRepository;
        this.chapterReviewService = chapterReviewService;
        this.generationJobService = generationJobService;
        this.chapterNarrativeMetricsRepository = chapterNarrativeMetricsRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.chapterObligationsService = chapterObligationsService;
        this.narrativeFulfillmentService = narrativeFulfillmentService;
        this.defaultSubtextWindowRadius = Math.max(0, defaultSubtextWindowRadius);
    }

    public void onGenerationSucceeded(String jobId) {
        GenerationJobEntity job = generationJobRepository.findById(jobId).orElse(null);
        if (job == null || !GenerationJobStatuses.SUCCEEDED.equals(job.getStatus())) {
            return;
        }
        Project p = projectRepository.findById(job.getProjectId()).orElse(null);
        if (p == null || p.getAutopilotPaused()) {
            return;
        }
        if (AutopilotModes.MANUAL.equals(p.getAutopilotMode())) {
            return;
        }
        if (!AutopilotModes.FULL_UNATTENDED.equals(p.getAutopilotMode())) {
            return;
        }
        String verId = job.getChapterVersionId();
        if (verId == null || verId.isBlank()) {
            return;
        }
        String policy = p.getAutoAcceptPolicy() != null ? p.getAutoAcceptPolicy() : AutoAcceptPolicies.NEVER;
        if (AutoAcceptPolicies.NEVER.equals(policy)) {
            appendAction(p, "skip_auto_accept", "policy=NEVER");
            return;
        }
        ChapterVersionEntity ver = chapterVersionRepository.findById(verId).orElse(null);
        if (ver == null || !ChapterVersionStatuses.PENDING_REVIEW.equals(ver.getStatus())) {
            return;
        }
        if (!criticPasses(ver)) {
            appendAction(p, "skip_auto_accept", "critic_fail");
            return;
        }
        if (AutoAcceptPolicies.CRITIC_AND_METRICS.equals(policy) && !metricsGateAllows(p, job.getChapterNo())) {
            appendAction(p, "skip_auto_accept", "metrics_gate");
            return;
        }
        if (AutoAcceptPolicies.CRITIC_PASS_AND_NARRATIVE.equals(policy)) {
            ver = ensureFulfillmentReportSynced(verId, p.getId(), job.getChapterNo());
            if (ver == null) {
                appendAction(p, "skip_auto_accept", "narrative_fulfillment_unavailable");
                return;
            }
            if (!NarrativeAutopilotGate.allows(p, ver, chapterObligationsService, objectMapper, defaultSubtextWindowRadius)) {
                appendAction(p, "skip_auto_accept", "narrative_gate");
                return;
            }
        }
        logNarrativeHardChecks(p, job);
        try {
            chapterReviewService.acceptVersion(verId);
            appendAction(p, "auto_accept", "chapter=" + job.getChapterNo());
        } catch (Exception e) {
            log.warn("auto accept failed project={} version={}: {}", job.getProjectId(), verId, e.getMessage());
            pauseProject(p, "auto_accept_failed: " + e.getMessage());
        }
    }

    public void onChapterAccepted(String projectId, int chapterNo, String commitId) {
        Project p = projectRepository.findById(projectId).orElse(null);
        if (p == null || p.getAutopilotPaused()) {
            return;
        }
        String mode = p.getAutopilotMode() != null ? p.getAutopilotMode() : AutopilotModes.MANUAL;
        if (AutopilotModes.MANUAL.equals(mode)) {
            return;
        }
        int next = chapterNo + 1;
        if (next > p.getTargetChapters()) {
            appendAction(p, "autopilot_stop", "target_chapters_reached");
            return;
        }
        if (p.getAutopilotChaptersThisRun() >= p.getMaxAutoChaptersPerRun()) {
            appendAction(p, "autopilot_pause", "max_auto_chapters_per_run");
            pauseProject(p, "max_auto_chapters_per_run");
            return;
        }
        try {
            generationJobService.enqueue(projectId, next, ChapterGenerateBody.empty());
            transactionTemplate.executeWithoutResult(ts -> {
                Project fresh = projectRepository.findById(projectId).orElse(null);
                if (fresh != null) {
                    fresh.setAutopilotChaptersThisRun(fresh.getAutopilotChaptersThisRun() + 1);
                    projectRepository.save(fresh);
                }
            });
            appendAction(p, "enqueue_next", "chapter=" + next);
        } catch (Exception e) {
            log.warn("autopilot enqueue next failed project={} next={}: {}", projectId, next, e.getMessage());
            pauseProject(p, "enqueue_failed: " + e.getMessage());
        }
    }

    private boolean criticPasses(ChapterVersionEntity ver) {
        var critic = ver.getCriticReportJson();
        return critic != null && !critic.isNull() && critic.path("pass").asBoolean(false);
    }

    private boolean metricsGateAllows(Project p, int chapterNo) {
        if (chapterNo <= 1) {
            return true;
        }
        Optional<ChapterNarrativeMetricsEntity> prev =
                chapterNarrativeMetricsRepository.findFirstByProjectIdAndChapterNoOrderByCreatedAtDesc(
                        p.getId(),
                        chapterNo - 1
                );
        if (prev.isEmpty()) {
            return true;
        }
        Double t = prev.get().getTensionScore();
        if (t == null) {
            return true;
        }
        return t >= 3.0;
    }

    /**
     * 自动 accept 前同步写入履约报告，避免与异步 {@link com.mythosforge.chapter.NarrativeFulfillmentListener} 竞态。
     */
    private ChapterVersionEntity ensureFulfillmentReportSynced(String versionId, String projectId, int chapterNo) {
        ChapterVersionEntity ver = chapterVersionRepository.findById(versionId).orElse(null);
        if (ver == null) {
            return null;
        }
        String text = ver.getStyledText();
        if (text == null || text.isBlank()) {
            text = ver.getChapterText();
        }
        if (text == null || text.isBlank()) {
            return ver;
        }
        ObjectNode report = narrativeFulfillmentService.evaluate(projectId, chapterNo, text);
        transactionTemplate.executeWithoutResult(ts -> {
            chapterVersionRepository.findById(versionId).ifPresent(v -> {
                v.setFulfillmentReportJson(report);
                chapterVersionRepository.save(v);
            });
        });
        return chapterVersionRepository.findById(versionId).orElse(ver);
    }

    /** 非 CRITIC_PASS_AND_NARRATIVE 策略下仍记录汇合/逾期子文本警告（不阻断）。 */
    private void logNarrativeHardChecks(Project p, GenerationJobEntity job) {
        try {
            String pid = job.getProjectId();
            int ch = job.getChapterNo();
            if (chapterObligationsService.hasUnresolvedConfluenceThisChapter(pid, ch)) {
                log.warn("autopilot_hard_check: confluence_chapter project={} chapter={}", pid, ch);
            }
            if (chapterObligationsService.hasOverdueSubtext(pid, ch)) {
                log.warn("autopilot_hard_check: overdue_subtext project={} chapter={}", pid, ch);
            }
        } catch (Exception ex) {
            log.debug("autopilot narrative hard check skipped: {}", ex.getMessage());
        }
    }

    private void appendAction(Project p, String action, String detail) {
        transactionTemplate.executeWithoutResult(ts -> {
            Project fresh = projectRepository.findById(p.getId()).orElse(null);
            if (fresh == null) {
                return;
            }
            ObjectNode root = objectMapper.createObjectNode();
            root.put("action", action);
            root.put("detail", detail);
            root.put("ts", java.time.Instant.now().toString());
            fresh.setAutopilotLastActionJson(root);
            projectRepository.save(fresh);
        });
    }

    private void pauseProject(Project p, String reason) {
        transactionTemplate.executeWithoutResult(ts -> {
            Project fresh = projectRepository.findById(p.getId()).orElse(null);
            if (fresh == null) {
                return;
            }
            fresh.setAutopilotPaused(true);
            fresh.setAutopilotPauseReason(reason != null && reason.length() > 2000 ? reason.substring(0, 2000) : reason);
            fresh.setNarrativePhase("PAUSED_ERROR");
            projectRepository.save(fresh);
        });
    }
}
