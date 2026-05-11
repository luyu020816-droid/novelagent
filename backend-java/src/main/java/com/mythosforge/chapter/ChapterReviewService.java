package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.story.StoryContractRepository;
import com.mythosforge.writer.WriterHttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Day 8.5：人工裁定 accept（摘要 + commit + 导出）/ reject。
 */
@Service
public class ChapterReviewService {

    private static final Logger log = LoggerFactory.getLogger(ChapterReviewService.class);

    private final ChapterVersionRepository chapterVersionRepository;
    private final ChapterCommitRepository chapterCommitRepository;
    private final ChapterContractRepository chapterContractRepository;
    private final ProjectRepository projectRepository;
    private final StoryContractRepository storyContractRepository;
    private final WriterHttpService writerHttpService;
    private final ObjectMapper objectMapper;
    private final Path exportRoot;
    private final TransactionTemplate transactionTemplate;
    private final TaskExecutor applicationTaskExecutor;

    public ChapterReviewService(
            ChapterVersionRepository chapterVersionRepository,
            ChapterCommitRepository chapterCommitRepository,
            ChapterContractRepository chapterContractRepository,
            ProjectRepository projectRepository,
            StoryContractRepository storyContractRepository,
            WriterHttpService writerHttpService,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            @Qualifier("applicationTaskExecutor") TaskExecutor applicationTaskExecutor,
            @Value("${mythosforge.export.root:exports}") String exportRoot
    ) {
        this.chapterVersionRepository = chapterVersionRepository;
        this.chapterCommitRepository = chapterCommitRepository;
        this.chapterContractRepository = chapterContractRepository;
        this.projectRepository = projectRepository;
        this.storyContractRepository = storyContractRepository;
        this.writerHttpService = writerHttpService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.exportRoot = Path.of(exportRoot).toAbsolutePath().normalize();
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

        ObjectNode sumBody = objectMapper.createObjectNode();
        sumBody.put("projectId", ver.getProjectId());
        sumBody.put("chapterNo", ver.getChapterNo());
        sumBody.put("chapterText", bodyForPublish);
        JsonNode sumResp = writerHttpService.postJson("/api/writer/chapters/summarize", sumBody);
        JsonNode summary = sumResp.get("summary");
        if (summary == null || summary.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Writer summarize 未返回 summary");
        }

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
            chapterCommitRepository.save(commit);
        });

        final String syncProjectId = ver.getProjectId();
        final int syncChapterNo = ver.getChapterNo();
        final String syncText = bodyForPublish;
        applicationTaskExecutor.execute(() -> {
            try {
                ObjectNode syncBody = objectMapper.createObjectNode();
                syncBody.put("projectId", syncProjectId);
                syncBody.put("chapterNo", syncChapterNo);
                syncBody.put("chapterText", syncText);
                writerHttpService.postJson("/api/writer/knowledge/sync", syncBody);
            } catch (Exception ex) {
                log.warn("async knowledge sync failed project={} chapter={}: {}", syncProjectId, syncChapterNo, ex.getMessage());
            }
        });
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
     * 删除待审核草稿（物理删除版本行）。已定稿/已退回不可删。
     */
    public void deletePendingVersion(String versionId) {
        ChapterVersionEntity ver = chapterVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!ChapterVersionStatuses.PENDING_REVIEW.equals(ver.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅待审核草稿可删除");
        }
        chapterVersionRepository.delete(ver);
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
