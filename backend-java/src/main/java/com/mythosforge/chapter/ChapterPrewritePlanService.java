package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.chapter.dto.ChapterPrewritePlanResponse;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.story.StoryContractEntity;
import com.mythosforge.story.StoryContractRepository;
import com.mythosforge.writer.WriterHttpService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
public class ChapterPrewritePlanService {

    private final ProjectRepository projectRepository;
    private final StoryContractRepository storyContractRepository;
    private final ChapterContractRepository chapterContractRepository;
    private final ChapterPrewritePlanRepository chapterPrewritePlanRepository;
    private final ChapterCommitRepository chapterCommitRepository;
    private final ChapterVersionRepository chapterVersionRepository;
    private final GenerationJobRepository generationJobRepository;
    private final JdbcTemplate jdbcTemplate;
    private final WriterHttpService writerHttpService;
    private final ObjectMapper objectMapper;

    public ChapterPrewritePlanService(
            ProjectRepository projectRepository,
            StoryContractRepository storyContractRepository,
            ChapterContractRepository chapterContractRepository,
            ChapterPrewritePlanRepository chapterPrewritePlanRepository,
            ChapterCommitRepository chapterCommitRepository,
            ChapterVersionRepository chapterVersionRepository,
            GenerationJobRepository generationJobRepository,
            JdbcTemplate jdbcTemplate,
            WriterHttpService writerHttpService,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.storyContractRepository = storyContractRepository;
        this.chapterContractRepository = chapterContractRepository;
        this.chapterPrewritePlanRepository = chapterPrewritePlanRepository;
        this.chapterCommitRepository = chapterCommitRepository;
        this.chapterVersionRepository = chapterVersionRepository;
        this.generationJobRepository = generationJobRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.writerHttpService = writerHttpService;
        this.objectMapper = objectMapper;
    }

    private String requireStoryContractId(String projectId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String sid = p.getSelectedStoryContractId();
        if (sid == null || sid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先在初始化页选定 Story 快照");
        }
        if (!storyContractRepository.existsById(sid)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "选中的 Story 快照不存在");
        }
        return sid;
    }

    /** 摘要变更后：删掉本章未接受稿、排队任务与用量日志，避免 UI 仍显示旧生成统计。 */
    private void purgeDraftArtifactsForChapter(String projectId, int chapterNo) {
        chapterVersionRepository.deleteNonAcceptedByProjectIdAndChapterNo(
                projectId, chapterNo, ChapterVersionStatuses.ACCEPTED);
        generationJobRepository.deleteByProjectIdAndChapterNo(projectId, chapterNo);
        jdbcTemplate.update(
                "DELETE FROM llm_usage_log WHERE project_id = ? AND chapter_no = ?",
                projectId,
                chapterNo
        );
    }

    @Transactional(readOnly = true)
    public ChapterPrewritePlanResponse getPlan(String projectId, int chapterNo) {
        String sid = requireStoryContractId(projectId);
        JsonNode prev = null;
        if (chapterNo > 1) {
            prev = chapterCommitRepository
                    .findFirstByProjectIdAndChapterNoAndStatusOrderByVersionDesc(projectId, chapterNo - 1, "accepted")
                    .map(ChapterCommitEntity::getSummary)
                    .orElse(null);
        }
        Optional<ChapterPrewritePlanEntity> row =
                chapterPrewritePlanRepository.findByProjectIdAndStoryContractIdAndChapterNo(projectId, sid, chapterNo);
        if (row.isEmpty()) {
            return new ChapterPrewritePlanResponse(chapterNo, prev, "", false);
        }
        ChapterPrewritePlanEntity e = row.get();
        return new ChapterPrewritePlanResponse(chapterNo, prev, e.getPlanSummary(), e.isConfirmed());
    }

    @Transactional
    public ChapterPrewritePlanResponse savePlan(String projectId, int chapterNo, String planSummary) {
        String sid = requireStoryContractId(projectId);
        String text = planSummary != null ? planSummary : "";
        Optional<ChapterPrewritePlanEntity> existing =
                chapterPrewritePlanRepository.findByProjectIdAndStoryContractIdAndChapterNo(projectId, sid, chapterNo);
        String before = existing.map(ChapterPrewritePlanEntity::getPlanSummary).orElse(null);
        String normalizedBefore = before != null ? before : "";
        if (!text.equals(normalizedBefore)) {
            purgeDraftArtifactsForChapter(projectId, chapterNo);
        }
        ChapterPrewritePlanEntity e;
        if (existing.isPresent()) {
            e = existing.get();
            if (!text.equals(e.getPlanSummary())) {
                e.setConfirmed(false);
            }
            e.setPlanSummary(text);
        } else {
            e = new ChapterPrewritePlanEntity();
            e.setId(UUID.randomUUID().toString().replace("-", ""));
            e.setProjectId(projectId);
            e.setStoryContractId(sid);
            e.setChapterNo(chapterNo);
            e.setPlanSummary(text);
            e.setConfirmed(false);
        }
        chapterPrewritePlanRepository.save(e);
        JsonNode prev = chapterNo > 1
                ? chapterCommitRepository
                        .findFirstByProjectIdAndChapterNoAndStatusOrderByVersionDesc(projectId, chapterNo - 1, "accepted")
                        .map(ChapterCommitEntity::getSummary)
                        .orElse(null)
                : null;
        return new ChapterPrewritePlanResponse(chapterNo, prev, e.getPlanSummary(), e.isConfirmed());
    }

    @Transactional
    public ChapterPrewritePlanResponse confirmPlan(String projectId, int chapterNo) {
        String sid = requireStoryContractId(projectId);
        ChapterPrewritePlanEntity e = chapterPrewritePlanRepository
                .findByProjectIdAndStoryContractIdAndChapterNo(projectId, sid, chapterNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先填写本章摘要草稿并保存"));
        if (e.getPlanSummary() == null || e.getPlanSummary().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "本章摘要不能为空，无法确认");
        }
        e.setConfirmed(true);
        chapterPrewritePlanRepository.save(e);
        JsonNode prev = chapterNo > 1
                ? chapterCommitRepository
                        .findFirstByProjectIdAndChapterNoAndStatusOrderByVersionDesc(projectId, chapterNo - 1, "accepted")
                        .map(ChapterCommitEntity::getSummary)
                        .orElse(null)
                : null;
        return new ChapterPrewritePlanResponse(chapterNo, prev, e.getPlanSummary(), true);
    }

    @Transactional(readOnly = true)
    public void requireConfirmedPlanForGeneration(String projectId, int chapterNo) {
        String sid = requireStoryContractId(projectId);
        ChapterPrewritePlanEntity e = chapterPrewritePlanRepository
                .findByProjectIdAndStoryContractIdAndChapterNo(projectId, sid, chapterNo)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "请先在写作台填写「本章动笔前摘要」并点击确认，再生成正文"
                ));
        if (!e.isConfirmed() || e.getPlanSummary() == null || e.getPlanSummary().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "请先在写作台填写「本章动笔前摘要」并点击确认，再生成正文"
            );
        }
    }

    @Transactional(readOnly = true)
    public String confirmedPlanSummaryText(String projectId, int chapterNo) {
        String sid = requireStoryContractId(projectId);
        return chapterPrewritePlanRepository
                .findByProjectIdAndStoryContractIdAndChapterNo(projectId, sid, chapterNo)
                .filter(ChapterPrewritePlanEntity::isConfirmed)
                .map(ChapterPrewritePlanEntity::getPlanSummary)
                .orElse("");
    }

    /** AI 起草本章动笔前摘要（Writer JSON）；不自动确认。 */
    public String proposePlanSummaryAi(String projectId, int chapterNo) {
        String sid = requireStoryContractId(projectId);
        Project p = projectRepository.findById(projectId).orElseThrow();
        StoryContractEntity story = storyContractRepository.findById(sid).orElseThrow();
        JsonNode chContract = chapterContractRepository
                .findByStoryContractIdAndChapterNo(sid, chapterNo)
                .map(ChapterContractEntity::getRawJson)
                .filter(j -> j != null && !j.isNull())
                .orElse(objectMapper.createObjectNode());
        if (!chContract.isObject()) {
            chContract = objectMapper.createObjectNode();
        }
        JsonNode prev = null;
        if (chapterNo > 1) {
            prev = chapterCommitRepository
                    .findFirstByProjectIdAndChapterNoAndStatusOrderByVersionDesc(projectId, chapterNo - 1, "accepted")
                    .map(ChapterCommitEntity::getSummary)
                    .orElse(null);
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("projectId", projectId);
        body.put("chapterNo", chapterNo);
        body.set("storyContract", story.getRawJson() != null ? story.getRawJson() : objectMapper.createObjectNode());
        body.set("chapterContract", chContract);
        if (prev != null && !prev.isNull()) {
            body.set("prevChapterCommitSummary", prev);
        }
        String fan = p.getFanSeriesPreset();
        if (fan != null && !fan.isBlank()) {
            body.put("fanSeriesPreset", fan.trim());
        }
        JsonNode resp = writerHttpService.postJson("/api/writer/chapters/propose-plan-summary", body);
        String text = resp.path("planSummary").asText("").trim();
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Writer 未返回 planSummary");
        }
        return text;
    }
}
