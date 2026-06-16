package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.chapter.dto.ChapterLatestCommitVectorDto;
import com.mythosforge.chapter.events.ChapterAcceptedEvent;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.story.StoryContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Day 8.5：人工裁定 accept（摘要 + commit + 导出）/ reject；
 * 定稿后向量同步状态、memory_summaries 双写、项目 narrative checkpoint。
 */
@Service
public class ChapterReviewService {

    private static final Logger log = LoggerFactory.getLogger(ChapterReviewService.class);

    private final ChapterVersionRepository chapterVersionRepository;
    private final ChapterCommitRepository chapterCommitRepository;
    private final ChapterContractRepository chapterContractRepository;
    private final GenerationJobRepository generationJobRepository;
    private final ProjectRepository projectRepository;
    private final StoryContractRepository storyContractRepository;
    private final MemorySummaryRepository memorySummaryRepository;
    private final ObjectMapper objectMapper;
    private final Path exportRoot;
    private final TransactionTemplate transactionTemplate;
    private final TaskExecutor applicationTaskExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final NarrativeMetricsService narrativeMetricsService;
    private final AftermathPipelineService aftermathPipelineService;

    public ChapterReviewService(
            ChapterVersionRepository chapterVersionRepository,
            ChapterCommitRepository chapterCommitRepository,
            ChapterContractRepository chapterContractRepository,
            GenerationJobRepository generationJobRepository,
            ProjectRepository projectRepository,
            StoryContractRepository storyContractRepository,
            MemorySummaryRepository memorySummaryRepository,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            @Qualifier("applicationTaskExecutor") TaskExecutor applicationTaskExecutor,
            ApplicationEventPublisher eventPublisher,
            NarrativeMetricsService narrativeMetricsService,
            AftermathPipelineService aftermathPipelineService,
            @Value("${mythosforge.export.root:exports}") String exportRoot
    ) {
        this.chapterVersionRepository = chapterVersionRepository;
        this.chapterCommitRepository = chapterCommitRepository;
        this.chapterContractRepository = chapterContractRepository;
        this.generationJobRepository = generationJobRepository;
        this.projectRepository = projectRepository;
        this.storyContractRepository = storyContractRepository;
        this.memorySummaryRepository = memorySummaryRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.eventPublisher = eventPublisher;
        this.narrativeMetricsService = narrativeMetricsService;
        this.aftermathPipelineService = aftermathPipelineService;
        this.exportRoot = Path.of(exportRoot).toAbsolutePath().normalize();
    }

    public Optional<ChapterLatestCommitVectorDto> findLatestAcceptedCommitVector(String projectId, int chapterNo) {
        return chapterCommitRepository
                .findFirstByProjectIdAndChapterNoAndStatusOrderByVersionDesc(projectId, chapterNo, "accepted")
                .map(this::toVectorDto);
    }

    private ChapterLatestCommitVectorDto toVectorDto(ChapterCommitEntity c) {
        String at = c.getVectorSyncAt() != null ? c.getVectorSyncAt().toString() : null;
        return new ChapterLatestCommitVectorDto(
                c.getId(),
                c.getChapterNo(),
                c.getVersion(),
                c.getVectorSyncStatus(),
                c.getVectorSyncError(),
                at,
                c.getVectorSyncAttempts(),
                c.getSummary()
        );
    }

    public void acceptVersion(String versionId) {
        ChapterVersionEntity ver = chapterVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!ChapterVersionStatuses.PENDING_REVIEW.equals(ver.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅 PENDING_REVIEW 版本可接受定稿");
        }
        String raw = ver.getChapterText();
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该版本无正文，无法接受");
        }
        String styled = ver.getStyledText();
        String bodyForPublish = (styled != null && !styled.isBlank()) ? styled : raw;

        JsonNode summary = aftermathPipelineService.runSyncAftermath(
                ver.getProjectId(),
                ver.getChapterNo(),
                bodyForPublish
        );

        Project p = projectRepository.findById(ver.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        String sid = p.getSelectedStoryContractId();
        if (sid == null || sid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目未选定 Story 快照");
        }
        if (!storyContractRepository.existsById(sid)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "选中的 Story 快照不存在");
        }
        ChapterContractEntity chapterRow = chapterContractRepository
                .findByStoryContractIdAndChapterNo(sid, ver.getChapterNo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "章纲不存在"));

        final String[] commitIdHolder = new String[1];
        transactionTemplate.executeWithoutResult(ts -> {
            ChapterVersionEntity fresh = chapterVersionRepository.findById(versionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (!ChapterVersionStatuses.PENDING_REVIEW.equals(fresh.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "版本状态已变更");
            }
            fresh.setStatus(ChapterVersionStatuses.ACCEPTED);
            chapterVersionRepository.save(fresh);

            String styledFresh = fresh.getStyledText();
            String rawFresh = fresh.getChapterText();
            String bodyText = (styledFresh != null && !styledFresh.isBlank()) ? styledFresh : rawFresh;
            if (bodyText == null || bodyText.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该版本无正文");
            }
            String relativePath = writeMarkdown(
                    fresh.getProjectId(),
                    fresh.getChapterNo(),
                    fresh.getVersion(),
                    bodyText
            );

            ChapterCommitEntity commit = new ChapterCommitEntity();
            commit.setId(UUID.randomUUID().toString().replace("-", ""));
            commit.setProjectId(fresh.getProjectId());
            commit.setChapterNo(fresh.getChapterNo());
            commit.setVersion(fresh.getVersion());
            commit.setStatus("accepted");
            commit.setChapterContractId(chapterRow.getId());
            commit.setFinalTextPath(relativePath);
            commit.setSummary(summary);
            commit.setVectorSyncStatus("PENDING");
            commit.setVectorSyncAttempts(0);
            chapterCommitRepository.save(commit);
            commitIdHolder[0] = commit.getId();

            memorySummaryRepository.deleteByProjectIdAndChapterNo(fresh.getProjectId(), fresh.getChapterNo());
            MemorySummaryEntity mem = buildMemorySummaryRow(commit.getId(), fresh.getProjectId(), fresh.getChapterNo(), summary);
            memorySummaryRepository.save(mem);

            Project proj = projectRepository.findById(fresh.getProjectId()).orElse(null);
            if (proj != null) {
                ObjectNode cp = objectMapper.createObjectNode();
                cp.put("lastAcceptedChapterNo", fresh.getChapterNo());
                cp.put("lastCommitId", commit.getId());
                cp.put("updatedAt", Instant.now().toString());
                proj.setNarrativeCheckpointJson(cp);
                if (proj.getNarrativePhase() == null || proj.getNarrativePhase().isBlank()) {
                    proj.setNarrativePhase("ACTIVE");
                }
                projectRepository.save(proj);
            }
        });

        final String syncProjectId = ver.getProjectId();
        final int syncChapterNo = ver.getChapterNo();
        final String syncText = bodyForPublish;
        final String syncCommitId = commitIdHolder[0];
        final String syncVersionId = versionId;
        final JsonNode syncSummary = summary;
        eventPublisher.publishEvent(new ChapterAcceptedEvent(this, syncProjectId, syncChapterNo, syncCommitId));
        applicationTaskExecutor.execute(() -> narrativeMetricsService.recordMetricsAsync(syncProjectId, syncChapterNo, syncCommitId, syncText));
        aftermathPipelineService.scheduleAsyncAfterAccept(
                syncProjectId,
                syncChapterNo,
                syncText,
                syncCommitId,
                syncVersionId,
                syncSummary
        );
    }

    private MemorySummaryEntity buildMemorySummaryRow(String commitId, String projectId, int chapterNo, JsonNode summary) {
        MemorySummaryEntity m = new MemorySummaryEntity();
        m.setId(UUID.randomUUID().toString().replace("-", ""));
        m.setProjectId(projectId);
        m.setChapterNo(chapterNo);
        m.setCommitId(commitId);
        m.setKeyEvents(summary.get("key_events"));
        m.setNewForeshadowing(summary.get("pending_foreshadowing"));
        ObjectNode cs = objectMapper.createObjectNode();
        cs.put("narrative", summary.path("character_state").asText(""));
        m.setCharacterStateChanges(cs);
        String st = summary.toString();
        if (st.length() > 12000) {
            st = st.substring(0, 12000);
        }
        m.setSummaryText(st);
        return m;
    }

    /** 幂等：再次 upsert Qdrant；用于 FAILED/SKIPPED 后人工重试。 */
    public void retryVectorSyncForCommit(String commitId) {
        ChapterCommitEntity c = chapterCommitRepository.findById(commitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!"accepted".equals(c.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅 accepted 的 commit 可重试向量同步");
        }
        String rel = c.getFinalTextPath();
        if (rel == null || rel.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "commit 缺少导出路径");
        }
        Path md = exportRoot.resolve(rel).normalize();
        if (!md.startsWith(exportRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法导出路径");
        }
        String text;
        try {
            text = Files.readString(md, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "读取定稿 Markdown 失败: " + e.getMessage());
        }
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "定稿文件为空");
        }
        transactionTemplate.executeWithoutResult(ts -> {
            ChapterCommitEntity fresh = chapterCommitRepository.findById(commitId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            fresh.setVectorSyncStatus("PENDING");
            chapterCommitRepository.save(fresh);
        });
        final String pid = c.getProjectId();
        final int ch = c.getChapterNo();
        applicationTaskExecutor.execute(() -> aftermathPipelineService.retryVectorSync(pid, ch, text, commitId));
    }

    public void rejectVersion(String versionId) {
        ChapterVersionEntity ver = chapterVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!ChapterVersionStatuses.PENDING_REVIEW.equals(ver.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅 PENDING_REVIEW 版本可标记拒绝");
        }
        ver.setStatus(ChapterVersionStatuses.REJECTED);
        chapterVersionRepository.save(ver);
    }

    /**
     * 删除章节版本：待审核 / 已退回直接删行；已定稿则同时删对应 {@code chapter_commits} 与导出 Markdown。
     */
    @Transactional
    public void deleteVersion(String versionId) {
        ChapterVersionEntity ver = chapterVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        generationJobRepository.deleteByChapterVersionId(versionId);
        String st = ver.getStatus();
        if (ChapterVersionStatuses.PENDING_REVIEW.equals(st) || ChapterVersionStatuses.REJECTED.equals(st)) {
            chapterVersionRepository.delete(ver);
            return;
        }
        if (ChapterVersionStatuses.ACCEPTED.equals(st)) {
            String projectId = ver.getProjectId();
            int chapterNo = ver.getChapterNo();
            int verNo = ver.getVersion();
            List<ChapterCommitEntity> commits =
                    chapterCommitRepository.findByProjectIdAndChapterNoAndVersion(projectId, chapterNo, verNo);
            for (ChapterCommitEntity c : commits) {
                deleteExportFileIfExists(c.getFinalTextPath());
                chapterCommitRepository.delete(c);
            }
            memorySummaryRepository.deleteByProjectIdAndChapterNo(projectId, chapterNo);
            chapterVersionRepository.delete(ver);
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持删除该状态: " + st);
    }

    private void deleteExportFileIfExists(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Path target = exportRoot.resolve(relativePath).normalize();
            if (!target.startsWith(exportRoot)) {
                log.warn("skip export delete outside root: {}", relativePath);
                return;
            }
            Files.deleteIfExists(target);
        } catch (Exception ex) {
            log.warn("export delete failed {}: {}", relativePath, ex.getMessage());
        }
    }

    private String writeMarkdown(String projectId, int chapterNo, int versionNo, String text) {
        try {
            Path dir = exportRoot.resolve(projectId);
            Files.createDirectories(dir);
            String filename = "chapter-" + chapterNo + "-v" + versionNo + ".md";
            Path file = dir.resolve(filename);
            Files.writeString(file, text, StandardCharsets.UTF_8);
            return projectId + "/" + filename;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "导出 Markdown 失败: " + ex.getMessage());
        }
    }
}
