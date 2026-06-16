package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.chapter.SubtextLedgerEntity;
import com.mythosforge.chapter.SubtextLedgerRepository;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.story.StoryContractEntity;
import com.mythosforge.story.StoryContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 章节定稿（accept）后按 {@link NarrativeAcceptPolicy} 回写 PostgreSQL 叙事真源。
 * <p>
 * 典型动作：标记本章汇合点为 resolved；absorb 时副线 COMPLETED；推进里程碑；
 * 更新主线 progressSummary；可选将窗口内子文本批量标为 consumed。返回审计 JSON 供日志。
 * </p>
 */
@Service
public class NarrativePostAcceptService {

    private static final Logger log = LoggerFactory.getLogger(NarrativePostAcceptService.class);

    private final ProjectRepository projectRepository;
    private final NarrativeStorylineRepository storylineRepository;
    private final NarrativeConfluenceRepository confluenceRepository;
    private final SubtextLedgerRepository subtextLedgerRepository;
    private final ChapterObligationsService chapterObligationsService;
    private final NarrativeDebtService narrativeDebtService;
    private final NarrativeCausalEdgeService narrativeCausalEdgeService;
    private final CharacterPsycheService characterPsycheService;
    private final StoryContractRepository storyContractRepository;
    private final ObjectMapper objectMapper;
    private final int defaultSubtextWindowRadius;

    public NarrativePostAcceptService(
            ProjectRepository projectRepository,
            NarrativeStorylineRepository storylineRepository,
            NarrativeConfluenceRepository confluenceRepository,
            SubtextLedgerRepository subtextLedgerRepository,
            ChapterObligationsService chapterObligationsService,
            NarrativeDebtService narrativeDebtService,
            NarrativeCausalEdgeService narrativeCausalEdgeService,
            CharacterPsycheService characterPsycheService,
            StoryContractRepository storyContractRepository,
            ObjectMapper objectMapper,
            @Value("${mythosforge.narrative.subtext-window-radius:1}") int defaultSubtextWindowRadius
    ) {
        this.projectRepository = projectRepository;
        this.storylineRepository = storylineRepository;
        this.confluenceRepository = confluenceRepository;
        this.subtextLedgerRepository = subtextLedgerRepository;
        this.chapterObligationsService = chapterObligationsService;
        this.narrativeDebtService = narrativeDebtService;
        this.narrativeCausalEdgeService = narrativeCausalEdgeService;
        this.characterPsycheService = characterPsycheService;
        this.storyContractRepository = storyContractRepository;
        this.objectMapper = objectMapper;
        this.defaultSubtextWindowRadius = Math.max(0, defaultSubtextWindowRadius);
    }

    /**
     * 在单事务内执行定稿后回写。
     *
     * @param summary 定稿摘要 JSON，用于截取 narrative 字段写入无父线故事线的 progressSummary
     * @return 含 resolvedConfluenceIds、consumedSubtextIds 的审计节点
     */
    @Transactional
    public ObjectNode applyAfterAccept(String projectId, int chapterNo, JsonNode summary) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return objectMapper.createObjectNode();
        }
        NarrativeAcceptPolicy policy = NarrativeAcceptPolicy.fromProject(project, objectMapper, defaultSubtextWindowRadius);
        ObjectNode audit = objectMapper.createObjectNode();
        ArrayNode resolvedIds = objectMapper.createArrayNode();
        ArrayNode consumedIds = objectMapper.createArrayNode();

        if (policy.autoResolveConfluenceOnAccept()) {
            for (NarrativeConfluenceEntity c : confluenceRepository.findByProjectIdAndTargetChapterAndResolvedIsFalse(
                    projectId,
                    chapterNo
            )) {
                c.setResolved(true);
                confluenceRepository.save(c);
                resolvedIds.add(c.getId());
                if (policy.autoCompleteAbsorbedStoryline() && "absorb".equalsIgnoreCase(c.getConfluenceType())) {
                    storylineRepository.findByProjectIdAndId(projectId, c.getSecondaryStorylineId())
                            .ifPresent(sec -> {
                                sec.setStatus("COMPLETED");
                                storylineRepository.save(sec);
                            });
                }
            }
        }

        String progressSnippet = extractProgressSnippet(summary);
        List<NarrativeStorylineEntity> lines = storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId);
        for (NarrativeStorylineEntity s : lines) {
            if (!"ACTIVE".equalsIgnoreCase(s.getStatus())) {
                continue;
            }
            if (!chapterInWindow(chapterNo, s)) {
                continue;
            }
            s.setLastActiveChapterNo(chapterNo);
            if (progressSnippet != null && s.getParentStorylineId() == null) {
                s.setProgressSummary(progressSnippet);
            }
            if (policy.autoAdvanceMilestones() && s.getMilestonesJson() != null && !s.getMilestonesJson().isNull()) {
                JsonNode before = s.getMilestonesJson();
                JsonNode after = NarrativeMilestoneHelper.advanceMilestonesForChapter(before, chapterNo, objectMapper);
                int completed = NarrativeMilestoneHelper.countNewlyCompleted(before, after);
                if (completed > 0) {
                    s.setMilestonesJson(after);
                    s.setCurrentMilestoneIndex(s.getCurrentMilestoneIndex() + completed);
                }
            }
            storylineRepository.save(s);
        }

        if (policy.autoConsumeSubtextInWindow()) {
            ObjectNode ob = chapterObligationsService.buildChapterObligations(projectId, chapterNo);
            JsonNode window = ob.get("dueSubtextInWindow");
            if (window != null && window.isArray()) {
                for (JsonNode item : window) {
                    String id = item.path("id").asText("");
                    if (id.isBlank()) {
                        continue;
                    }
                    subtextLedgerRepository.findById(id).ifPresent(st -> {
                        if ("pending".equals(st.getStatus())) {
                            st.setStatus("consumed");
                            st.setConsumedAtChapter(chapterNo);
                            subtextLedgerRepository.save(st);
                            consumedIds.add(id);
                        }
                    });
                }
            }
        }

        JsonNode storyJson = resolveStoryJson(project);
        narrativeDebtService.ingestForeshadowingFromSummary(
                projectId,
                chapterNo,
                summary != null ? summary.get("pending_foreshadowing") : null
        );
        narrativeCausalEdgeService.ingestFromChapterSummary(projectId, chapterNo, summary);
        characterPsycheService.captureFromAcceptSummary(projectId, chapterNo, storyJson, summary);
        for (JsonNode idNode : resolvedIds) {
            narrativeDebtService.resolveBySourceRef(projectId, "confluence:" + idNode.asText(), chapterNo);
        }
        for (JsonNode idNode : consumedIds) {
            narrativeDebtService.resolveBySourceRef(projectId, "subtext:" + idNode.asText(), chapterNo);
        }
        narrativeDebtService.syncFromProjectSources(projectId, chapterNo + 1);

        audit.set("resolvedConfluenceIds", resolvedIds);
        audit.set("consumedSubtextIds", consumedIds);
        log.info(
                "narrative post-accept project={} chapter={} resolved={} consumed={}",
                projectId,
                chapterNo,
                resolvedIds.size(),
                consumedIds.size()
        );
        return audit;
    }

    private static String extractProgressSnippet(JsonNode summary) {
        if (summary == null || summary.isNull()) {
            return null;
        }
        String narrative = summary.path("narrative").asText("");
        if (!narrative.isBlank()) {
            return narrative.length() > 500 ? narrative.substring(0, 500) : narrative;
        }
        String st = summary.toString();
        return st.length() > 500 ? st.substring(0, 500) : st;
    }

    private JsonNode resolveStoryJson(Project project) {
        if (project == null) {
            return objectMapper.createObjectNode();
        }
        String sid = project.getSelectedStoryContractId();
        if (sid == null || sid.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return storyContractRepository.findById(sid)
                .map(StoryContractEntity::getRawJson)
                .orElse(objectMapper.createObjectNode());
    }

    private static boolean chapterInWindow(int chapterNo, NarrativeStorylineEntity s) {
        if (s.getEstStartChapter() != null && chapterNo < s.getEstStartChapter()) {
            return false;
        }
        if (s.getEstEndChapter() != null && chapterNo > s.getEstEndChapter()) {
            return false;
        }
        return true;
    }
}
