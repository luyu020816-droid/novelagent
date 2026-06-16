package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.mythosforge.chapter.ChapterVersionEntity;
import com.mythosforge.project.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 自动驾驶 {@code CRITIC_PASS_AND_NARRATIVE} 的叙事闸门。
 * <p>
 * 汇合章在定稿前存在「未 resolved 汇合」属正常，不以该条件拦截；以履约报告、Critic 叙事维度、逾期子文本为准。
 * </p>
 */
public final class NarrativeAutopilotGate {

    private static final Logger log = LoggerFactory.getLogger(NarrativeAutopilotGate.class);

    private NarrativeAutopilotGate() {}

    public static boolean allows(
            Project project,
            ChapterVersionEntity version,
            ChapterObligationsService chapterObligationsService,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            int defaultSubtextWindowRadius
    ) {
        if (project == null || version == null) {
            return false;
        }
        String projectId = project.getId();
        int chapterNo = version.getChapterNo();
        NarrativeAcceptPolicy policy = NarrativeAcceptPolicy.fromProject(project, objectMapper, defaultSubtextWindowRadius);
        boolean strict = NarrativeAcceptPolicy.HARD_CHECK_SKIP_AUTO_ACCEPT.equals(policy.autopilotNarrativeHardCheck());

        if (chapterObligationsService.hasOverdueSubtext(projectId, chapterNo)) {
            log.warn("autopilot_hard_check: overdue_subtext project={} chapter={}", projectId, chapterNo);
            if (strict) {
                return false;
            }
        }

        JsonNode fulfillment = version.getFulfillmentReportJson();
        if (fulfillment != null && !fulfillment.isNull() && !fulfillment.path("overallPass").asBoolean(true)) {
            log.warn("autopilot_hard_check: fulfillment_fail project={} chapter={}", projectId, chapterNo);
            return false;
        }

        if (strict && !criticNarrativeDimensionOk(version.getCriticReportJson())) {
            log.warn("autopilot_hard_check: critic_narrative_obligations project={} chapter={}", projectId, chapterNo);
            return false;
        }

        if (chapterObligationsService.hasUnresolvedConfluenceThisChapter(projectId, chapterNo)) {
            log.info(
                    "autopilot_hard_check: confluence_chapter project={} chapter={} (expected before accept; gate uses fulfillment/critic)",
                    projectId,
                    chapterNo
            );
        }

        return true;
    }

    /** Critic dimensions 中 id=narrative_obligations 须 ok；无该维度时不额外拦截。 */
    public static boolean criticNarrativeDimensionOk(JsonNode critic) {
        if (critic == null || critic.isNull()) {
            return true;
        }
        JsonNode dimensions = critic.path("dimensions");
        if (!dimensions.isArray()) {
            return true;
        }
        boolean found = false;
        for (JsonNode dim : dimensions) {
            if (!"narrative_obligations".equals(dim.path("id").asText(""))) {
                continue;
            }
            found = true;
            if (!dim.path("ok").asBoolean(false)) {
                return false;
            }
        }
        return true;
    }
}
