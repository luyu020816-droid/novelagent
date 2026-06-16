package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 当前 {@link StoryPhase} 对生成/编辑的约束，写入本章任务单 {@code phaseRules}。
 */
public record StoryPhasePolicy(
        StoryPhase phase,
        double progressRatio,
        boolean allowNewSubtext,
        boolean allowNewForeshadowing,
        String guidanceLine
) {
    public static StoryPhasePolicy forChapter(int chapterNo, int targetChapters) {
        int target = Math.max(1, targetChapters);
        double ratio = Math.min(1.0, (double) chapterNo / (double) target);
        StoryPhase phase = StoryPhase.fromProgress(ratio);
        String guidance = switch (phase) {
            case OPENING -> "开局期：可埋设悬念与子文本，铺陈世界观，汇合与里程碑适度推进。";
            case DEVELOPMENT -> "发展期：激化矛盾，可新增子文本与支线节拍，注意标记建议回收章。";
            case CONVERGENCE -> "收敛期：禁止新增子文本/新坑；优先回收逾期子文本、兑现汇合与里程碑，勿开新故事线。";
            case FINALE -> "终局期：禁止新增子文本；全力收束汇合、里程碑与核心悬念，避免引入新角色线。";
        };
        return new StoryPhasePolicy(
                phase,
                ratio,
                phase.allowsNewSubtext(),
                phase.allowsNewForeshadowing(),
                guidance
        );
    }

    public ObjectNode toRulesJson(ObjectMapper mapper) {
        ObjectNode o = mapper.createObjectNode();
        o.put("phase", phase.jsonValue());
        o.put("phaseDisplayName", phase.displayName());
        o.put("progressRatio", progressRatio);
        o.put("allowNewSubtext", allowNewSubtext);
        o.put("allowNewForeshadowing", allowNewForeshadowing);
        o.put("guidanceLine", guidanceLine);
        return o;
    }
}
