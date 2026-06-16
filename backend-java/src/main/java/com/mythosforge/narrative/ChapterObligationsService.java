package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.chapter.SubtextLedgerEntity;
import com.mythosforge.chapter.SubtextLedgerRepository;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据 PostgreSQL 叙事真源组装「本章任务单」JSON。
 * <p>
 * 产出字段包括：活跃故事线、本章到期里程碑、未 resolved 汇合点、子文本（精确章 / 窗口 / 逾期）及一行中文摘要。
 * 该 JSON 在章生成前注入 Writer（Curator），定稿后也可写入 Neo4j 章节快照。
 * </p>
 */
@Service
public class ChapterObligationsService {

    private final NarrativeStorylineRepository storylineRepository;
    private final NarrativeConfluenceRepository confluenceRepository;
    private final SubtextLedgerRepository subtextLedgerRepository;
    private final ProjectRepository projectRepository;
    private final NarrativeContextSlotsService narrativeContextSlotsService;
    private final ObjectMapper objectMapper;
    private final int defaultSubtextWindowRadius;

    public ChapterObligationsService(
            NarrativeStorylineRepository storylineRepository,
            NarrativeConfluenceRepository confluenceRepository,
            SubtextLedgerRepository subtextLedgerRepository,
            ProjectRepository projectRepository,
            NarrativeContextSlotsService narrativeContextSlotsService,
            ObjectMapper objectMapper,
            @Value("${mythosforge.narrative.subtext-window-radius:1}") int defaultSubtextWindowRadius
    ) {
        this.storylineRepository = storylineRepository;
        this.confluenceRepository = confluenceRepository;
        this.subtextLedgerRepository = subtextLedgerRepository;
        this.projectRepository = projectRepository;
        this.narrativeContextSlotsService = narrativeContextSlotsService;
        this.objectMapper = objectMapper;
        this.defaultSubtextWindowRadius = Math.max(0, defaultSubtextWindowRadius);
    }

    /**
     * 构建指定章节的完整任务单；子文本窗口半径来自项目 {@code narrative_accept_policy_json} 或全局配置。
     */
    public ObjectNode buildChapterObligations(String projectId, int chapterNo) {
        return buildChapterObligations(projectId, chapterNo, false);
    }

    /**
     * @param syncProjectPhase 为 true 时将计算出的 {@link StoryPhase} 写入 {@code projects.narrative_phase}（生成/定稿路径用）
     */
    public ObjectNode buildChapterObligations(String projectId, int chapterNo, boolean syncProjectPhase) {
        Project project = projectRepository.findById(projectId).orElse(null);
        NarrativeAcceptPolicy policy = NarrativeAcceptPolicy.fromProject(project, objectMapper, defaultSubtextWindowRadius);
        int windowRadius = policy.subtextWindowRadius();

        ObjectNode root = objectMapper.createObjectNode();
        root.put("projectId", projectId);
        root.put("chapterNo", chapterNo);
        root.put("subtextWindowRadius", windowRadius);

        int targetChapters = project != null && project.getTargetChapters() != null && project.getTargetChapters() > 0
                ? project.getTargetChapters()
                : 100;
        StoryPhasePolicy phasePolicy = StoryPhasePolicy.forChapter(chapterNo, targetChapters);
        root.put("storyPhase", phasePolicy.phase().jsonValue());
        root.put("progressRatio", phasePolicy.progressRatio());
        root.set("phaseRules", phasePolicy.toRulesJson(objectMapper));
        if (syncProjectPhase && project != null) {
            project.setNarrativePhase(phasePolicy.phase().jsonValue());
            projectRepository.save(project);
        }

        Map<String, NarrativeStorylineEntity> lineById = new HashMap<>();
        List<NarrativeStorylineEntity> lines = storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId);
        List<NarrativeStorylineEntity> activeEntities = new ArrayList<>();
        for (NarrativeStorylineEntity s : lines) {
            lineById.put(s.getId(), s);
        }

        ArrayNode activeLines = objectMapper.createArrayNode();
        ArrayNode dueMilestonesAll = objectMapper.createArrayNode();
        for (NarrativeStorylineEntity s : lines) {
            if (!"ACTIVE".equalsIgnoreCase(s.getStatus())) {
                continue;
            }
            if (!chapterInStorylineWindow(chapterNo, s)) {
                continue;
            }
            ObjectNode o = objectMapper.createObjectNode();
            o.put("id", s.getId());
            o.put("key", s.getStorylineKey());
            o.put("title", s.getTitle());
            o.put("storylineRole", s.getStorylineRole() != null ? s.getStorylineRole() : "SUB");
            o.put("currentMilestoneIndex", s.getCurrentMilestoneIndex());
            activeEntities.add(s);
            if (s.getProgressSummary() != null && !s.getProgressSummary().isBlank()) {
                o.put("progressSummary", s.getProgressSummary());
            }
            if (s.getMilestonesJson() != null && !s.getMilestonesJson().isNull()) {
                o.set("milestones", s.getMilestonesJson());
            }
            List<ObjectNode> due = NarrativeMilestoneHelper.dueMilestonesForChapter(
                    s.getMilestonesJson(),
                    s.getCurrentMilestoneIndex(),
                    chapterNo
            );
            if (!due.isEmpty()) {
                ArrayNode dueArr = objectMapper.createArrayNode();
                due.forEach(dueArr::add);
                o.set("dueMilestonesThisChapter", dueArr);
                due.forEach(dueMilestonesAll::add);
            }
            activeLines.add(o);
        }
        root.set("activeStorylines", activeLines);
        root.set("dueMilestonesThisChapter", dueMilestonesAll);

        List<NarrativeConfluenceEntity> dueConfluenceEntities =
                confluenceRepository.findByProjectIdAndTargetChapterAndResolvedIsFalse(projectId, chapterNo);
        ArrayNode conf = objectMapper.createArrayNode();
        for (NarrativeConfluenceEntity c : dueConfluenceEntities) {
            conf.add(confluenceNode(c, lineById));
        }
        root.set("dueConfluences", conf);

        List<String> promptLines = NarrativePromptLinesBuilder.build(
                chapterNo,
                activeEntities,
                dueConfluenceEntities,
                lineById
        );
        ArrayNode pl = objectMapper.createArrayNode();
        for (String line : promptLines) {
            pl.add(line);
        }
        root.set("narrativePromptLines", pl);

        List<SubtextLedgerEntity> pending = subtextLedgerRepository.findByProjectIdAndStatusOrderByChapterNoAsc(
                projectId,
                "pending"
        );
        ArrayNode exactSub = objectMapper.createArrayNode();
        ArrayNode windowSub = objectMapper.createArrayNode();
        ArrayNode overdueSub = objectMapper.createArrayNode();
        for (SubtextLedgerEntity st : pending) {
            Integer sug = st.getSuggestedResolveChapter();
            if (sug != null && sug == chapterNo) {
                exactSub.add(subtextNode(st));
            }
            if (sug != null) {
                if (sug < chapterNo) {
                    overdueSub.add(subtextNode(st));
                } else if (Math.abs(sug - chapterNo) <= windowRadius) {
                    windowSub.add(subtextNode(st));
                }
            }
        }
        root.set("dueSubtextSuggestedThisChapter", exactSub);
        root.set("dueSubtextInWindow", windowSub);
        root.set("overdueSubtext", overdueSub);

        StringBuilder summary = new StringBuilder();
        if (conf.size() > 0) {
            summary.append("本章为汇合章（").append(conf.size()).append(" 处未 resolved）；");
        }
        if (overdueSub.size() > 0) {
            summary.append("逾期子文本 ").append(overdueSub.size()).append(" 条；");
        }
        if (windowSub.size() > 0) {
            summary.append("窗口内子文本 ").append(windowSub.size()).append(" 条；");
        }
        if (dueMilestonesAll.size() > 0) {
            summary.append("待完成里程碑 ").append(dueMilestonesAll.size()).append(" 条；");
        }
        if (activeLines.size() > 0) {
            summary.append("活跃故事线 ").append(activeLines.size()).append(" 条。");
        }
        summary.append("[").append(phasePolicy.phase().displayName()).append("] ");
        summary.append(phasePolicy.guidanceLine());
        root.put("summaryLine", summary.toString());
        root.put("continuityBrief", buildContinuityBrief(chapterNo, conf, overdueSub, windowSub, exactSub, dueMilestonesAll, phasePolicy));
        narrativeContextSlotsService.enrichChapterObligations(root, projectId, chapterNo);
        return root;
    }

    /**
     * PlotPilot 式连续性反哺：Writer Curator 注入 {@code continuity_brief}，与 {@code previously_on}（前章摘要）互补。
     */
    private String buildContinuityBrief(
            int chapterNo,
            ArrayNode dueConfluences,
            ArrayNode overdueSubtext,
            ArrayNode windowSubtext,
            ArrayNode exactSubtext,
            ArrayNode dueMilestones,
            StoryPhasePolicy phasePolicy
    ) {
        StringBuilder b = new StringBuilder();
        b.append("【全书阶段】").append(phasePolicy.phase().displayName())
                .append("（进度 ").append(Math.round(phasePolicy.progressRatio() * 100)).append("%）\n");
        b.append(phasePolicy.guidanceLine()).append("\n");
        if (dueConfluences.size() > 0) {
            b.append("\n【本章汇合须兑现】\n");
            for (int i = 0; i < dueConfluences.size(); i++) {
                appendConfluenceBrief(b, dueConfluences.get(i), chapterNo);
            }
        }
        if (overdueSubtext.size() > 0) {
            b.append("\n【逾期子文本 — 优先回收】\n");
            for (int i = 0; i < overdueSubtext.size(); i++) {
                appendSubtextBrief(b, overdueSubtext.get(i));
            }
        }
        if (exactSubtext.size() > 0) {
            b.append("\n【建议本章回收的子文本】\n");
            for (int i = 0; i < exactSubtext.size(); i++) {
                appendSubtextBrief(b, exactSubtext.get(i));
            }
        } else if (windowSubtext.size() > 0) {
            b.append("\n【窗口内子文本 — 宜推进或铺垫回收】\n");
            for (int i = 0; i < windowSubtext.size(); i++) {
                appendSubtextBrief(b, windowSubtext.get(i));
            }
        }
        if (dueMilestones.size() > 0) {
            b.append("\n【待完成里程碑】\n");
            for (int i = 0; i < dueMilestones.size(); i++) {
                var m = dueMilestones.get(i);
                if (m != null && m.isObject()) {
                    String title = m.has("title") ? m.get("title").asText("") : "";
                    String beat = m.has("beat") ? m.get("beat").asText("") : "";
                    if (!title.isBlank() || !beat.isBlank()) {
                        b.append("- ").append(title.isBlank() ? beat : title);
                        if (!beat.isBlank() && !title.isBlank()) {
                            b.append("：").append(beat);
                        }
                        b.append("\n");
                    }
                }
            }
        }
        return b.toString().trim();
    }

    private static void appendConfluenceBrief(StringBuilder b, com.fasterxml.jackson.databind.JsonNode c, int chapterNo) {
        if (c == null || !c.isObject()) {
            return;
        }
        String p = c.has("primaryStorylineTitle") ? c.get("primaryStorylineTitle").asText("")
                : c.has("primaryStorylineKey") ? c.get("primaryStorylineKey").asText("") : "?";
        String s = c.has("secondaryStorylineTitle") ? c.get("secondaryStorylineTitle").asText("")
                : c.has("secondaryStorylineKey") ? c.get("secondaryStorylineKey").asText("") : "?";
        String type = c.has("confluenceType") ? c.get("confluenceType").asText("merge") : "merge";
        b.append("- ").append(p).append(" × ").append(s).append("（").append(type).append("）");
        if (c.has("contextSummary") && !c.get("contextSummary").asText("").isBlank()) {
            b.append("：").append(c.get("contextSummary").asText(""));
        }
        b.append("\n");
    }

    private static void appendSubtextBrief(StringBuilder b, com.fasterxml.jackson.databind.JsonNode st) {
        if (st == null || !st.isObject()) {
            return;
        }
        int planted = st.has("plantedChapter") ? st.get("plantedChapter").asInt(0) : 0;
        String q = st.has("question") ? st.get("question").asText("") : "";
        if (q.isBlank()) {
            return;
        }
        b.append("- 第").append(planted).append("章埋设：").append(q);
        if (st.has("suggestedResolveChapter") && !st.get("suggestedResolveChapter").isNull()) {
            b.append("（建议第").append(st.get("suggestedResolveChapter").asInt()).append("章回收）");
        }
        b.append("\n");
    }

    private ObjectNode confluenceNode(NarrativeConfluenceEntity c, Map<String, NarrativeStorylineEntity> lineById) {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("id", c.getId());
        o.put("primaryStorylineId", c.getPrimaryStorylineId());
        o.put("secondaryStorylineId", c.getSecondaryStorylineId());
        enrichStorylineLabels(o, "primary", c.getPrimaryStorylineId(), lineById);
        enrichStorylineLabels(o, "secondary", c.getSecondaryStorylineId(), lineById);
        o.put("targetChapter", c.getTargetChapter());
        o.put("confluenceType", c.getConfluenceType());
        if (c.getNotes() != null) {
            o.put("notes", c.getNotes());
        }
        if (c.getContextSummary() != null) {
            o.put("contextSummary", c.getContextSummary());
        }
        if (c.getPreRevealHint() != null) {
            o.put("preRevealHint", c.getPreRevealHint());
        }
        if (c.getBehaviorGuards() != null && !c.getBehaviorGuards().isNull()) {
            o.set("behaviorGuards", c.getBehaviorGuards());
        }
        return o;
    }

    private void enrichStorylineLabels(
            ObjectNode o,
            String prefix,
            String id,
            Map<String, NarrativeStorylineEntity> lineById
    ) {
        NarrativeStorylineEntity sl = lineById.get(id);
        if (sl != null) {
            o.put(prefix + "StorylineKey", sl.getStorylineKey());
            o.put(prefix + "StorylineTitle", sl.getTitle());
        }
    }

    private ObjectNode subtextNode(SubtextLedgerEntity st) {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("id", st.getId());
        o.put("plantedChapter", st.getChapterNo());
        o.put("question", st.getQuestion());
        if (st.getCharacterRef() != null) {
            o.put("characterRef", st.getCharacterRef());
        }
        if (st.getSuggestedResolveChapter() != null) {
            o.put("suggestedResolveChapter", st.getSuggestedResolveChapter());
        }
        return o;
    }

    /** 本章是否存在 targetChapter 匹配且尚未 resolved 的汇合点（Autopilot 硬闸门用）。 */
    public boolean hasUnresolvedConfluenceThisChapter(String projectId, int chapterNo) {
        return !confluenceRepository.findByProjectIdAndTargetChapterAndResolvedIsFalse(projectId, chapterNo).isEmpty();
    }

    /** 是否存在建议在本章回收且仍为 pending 的子文本。 */
    public boolean hasDueSubtextSuggestedThisChapter(String projectId, int chapterNo) {
        return !subtextLedgerRepository
                .findByProjectIdAndStatusAndSuggestedResolveChapterOrderByChapterNoAsc(projectId, "pending", chapterNo)
                .isEmpty();
    }

    /** 是否存在 suggestedResolveChapter 早于当前章、仍为 pending 的子文本。 */
    public boolean hasOverdueSubtext(String projectId, int chapterNo) {
        for (SubtextLedgerEntity st : subtextLedgerRepository.findByProjectIdAndStatusOrderByChapterNoAsc(projectId, "pending")) {
            Integer sug = st.getSuggestedResolveChapter();
            if (sug != null && sug < chapterNo) {
                return true;
            }
        }
        return false;
    }

    private static boolean chapterInStorylineWindow(int chapterNo, NarrativeStorylineEntity s) {
        if (s.getEstStartChapter() != null && chapterNo < s.getEstStartChapter()) {
            return false;
        }
        if (s.getEstEndChapter() != null && chapterNo > s.getEstEndChapter()) {
            return false;
        }
        return true;
    }
}
