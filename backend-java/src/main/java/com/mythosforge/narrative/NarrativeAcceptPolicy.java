package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.project.Project;

/**
 * 定稿后与 Autopilot 相关的叙事策略（存于 projects.narrative_accept_policy_json）。
 *
 * @param autoResolveConfluenceOnAccept     定稿时是否将本章 target 汇合标为 resolved
 * @param autoCompleteAbsorbedStoryline     absorb 汇合 resolved 后是否将副线标 COMPLETED
 * @param autoAdvanceMilestones             定稿时是否推进本章到期里程碑
 * @param autoConsumeSubtextInWindow        定稿时是否将窗口内 pending 子文本标 consumed（不校验正文）
 * @param subtextWindowRadius               建议回收章 ±N 章视为「窗口内」
 * @param autopilotNarrativeHardCheck       LOG 仅打日志；SKIP_AUTO_ACCEPT 在汇合/逾期子文本时阻断自动接受
 */
public record NarrativeAcceptPolicy(
        boolean autoResolveConfluenceOnAccept,
        boolean autoCompleteAbsorbedStoryline,
        boolean autoAdvanceMilestones,
        boolean autoConsumeSubtextInWindow,
        int subtextWindowRadius,
        String autopilotNarrativeHardCheck
) {
    public static final String HARD_CHECK_LOG = "LOG";
    public static final String HARD_CHECK_SKIP_AUTO_ACCEPT = "SKIP_AUTO_ACCEPT";

    /** 未配置项目策略时的默认值；子文本窗口半径仍可由 application.yml 覆盖。 */
    public static final NarrativeAcceptPolicy DEFAULT = new NarrativeAcceptPolicy(
            true,
            true,
            true,
            false,
            1,
            HARD_CHECK_LOG
    );

    /** 从项目 JSON 解析策略；缺字段时回落到 {@link #DEFAULT}。 */
    public static NarrativeAcceptPolicy fromProject(Project project, ObjectMapper mapper, int defaultSubtextRadius) {
        if (project == null) {
            return withRadius(DEFAULT, defaultSubtextRadius);
        }
        JsonNode raw = project.getNarrativeAcceptPolicyJson();
        if (raw == null || raw.isNull() || !raw.isObject()) {
            return withRadius(DEFAULT, defaultSubtextRadius);
        }
        boolean resolve = raw.path("autoResolveConfluenceOnAccept").asBoolean(DEFAULT.autoResolveConfluenceOnAccept);
        boolean absorb = raw.path("autoCompleteAbsorbedStoryline").asBoolean(DEFAULT.autoCompleteAbsorbedStoryline);
        boolean milestones = raw.path("autoAdvanceMilestones").asBoolean(DEFAULT.autoAdvanceMilestones);
        boolean consume = raw.path("autoConsumeSubtextInWindow").asBoolean(DEFAULT.autoConsumeSubtextInWindow);
        int radius = raw.has("subtextWindowRadius")
                ? Math.max(0, raw.path("subtextWindowRadius").asInt(defaultSubtextRadius))
                : defaultSubtextRadius;
        String hard = raw.path("autopilotNarrativeHardCheck").asText(DEFAULT.autopilotNarrativeHardCheck);
        if (!HARD_CHECK_LOG.equals(hard) && !HARD_CHECK_SKIP_AUTO_ACCEPT.equals(hard)) {
            hard = DEFAULT.autopilotNarrativeHardCheck;
        }
        return new NarrativeAcceptPolicy(resolve, absorb, milestones, consume, radius, hard);
    }

    /** 序列化为可 PATCH 的 JSON 对象。 */
    public ObjectNode toJson(ObjectMapper mapper) {
        ObjectNode o = mapper.createObjectNode();
        o.put("autoResolveConfluenceOnAccept", autoResolveConfluenceOnAccept);
        o.put("autoCompleteAbsorbedStoryline", autoCompleteAbsorbedStoryline);
        o.put("autoAdvanceMilestones", autoAdvanceMilestones);
        o.put("autoConsumeSubtextInWindow", autoConsumeSubtextInWindow);
        o.put("subtextWindowRadius", subtextWindowRadius);
        o.put("autopilotNarrativeHardCheck", autopilotNarrativeHardCheck);
        return o;
    }

    private static NarrativeAcceptPolicy withRadius(NarrativeAcceptPolicy base, int radius) {
        return new NarrativeAcceptPolicy(
                base.autoResolveConfluenceOnAccept,
                base.autoCompleteAbsorbedStoryline,
                base.autoAdvanceMilestones,
                base.autoConsumeSubtextInWindow,
                radius,
                base.autopilotNarrativeHardCheck
        );
    }
}
