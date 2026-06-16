package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 故事线 milestones_json 的纯函数工具：推导本章应完成的节拍、定稿后标记 done、统计新增完成数。
 * <p>
 * 里程碑对象约定：{@code order}（≥1）、{@code status}（pending/done）、可选 {@code targetChapter}。
 * </p>
 */
public final class NarrativeMilestoneHelper {

    private NarrativeMilestoneHelper() {}

    /**
     * 返回本章应推进的里程碑副本列表：order &gt; currentIndex、非 done，且 targetChapter 为空或等于 chapterNo。
     */
    public static List<ObjectNode> dueMilestonesForChapter(JsonNode milestones, int currentIndex, int chapterNo) {
        List<ObjectNode> out = new ArrayList<>();
        if (milestones == null || !milestones.isArray()) {
            return out;
        }
        for (JsonNode m : milestones) {
            if (!m.isObject()) {
                continue;
            }
            int order = m.path("order").asInt(-1);
            if (order < 1 || order <= currentIndex) {
                continue;
            }
            String status = m.path("status").asText("pending");
            if ("done".equalsIgnoreCase(status)) {
                continue;
            }
            if (m.has("targetChapter") && !m.path("targetChapter").isNull()) {
                if (m.path("targetChapter").asInt() != chapterNo) {
                    continue;
                }
            }
            ObjectNode o = m.deepCopy();
            out.add((ObjectNode) o);
        }
        return out;
    }

    /**
     * 将 targetChapter 匹配本章（或无数目标章）且非 done 的里程碑标为 done；若无变更则返回原节点。
     */
    public static JsonNode advanceMilestonesForChapter(JsonNode milestones, int chapterNo, ObjectMapper mapper) {
        if (milestones == null || !milestones.isArray()) {
            return milestones;
        }
        ArrayNode arr = mapper.createArrayNode();
        int advanced = 0;
        for (JsonNode m : milestones) {
            ObjectNode copy = m.isObject() ? ((ObjectNode) m.deepCopy()) : mapper.createObjectNode();
            if (m.isObject()) {
                int order = m.path("order").asInt(-1);
                String status = m.path("status").asText("pending");
                boolean targetMatch = !m.has("targetChapter") || m.path("targetChapter").isNull()
                        || m.path("targetChapter").asInt() == chapterNo;
                if (!"done".equalsIgnoreCase(status) && targetMatch && order > 0) {
                    copy.put("status", "done");
                    advanced++;
                }
            }
            arr.add(copy);
        }
        if (advanced == 0) {
            return milestones;
        }
        return arr;
    }

    /** 按数组下标比较 status，统计新变为 done 的条数（用于递增 currentMilestoneIndex）。 */
    public static int countNewlyCompleted(JsonNode before, JsonNode after) {
        if (before == null || after == null || !before.isArray() || !after.isArray()) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < before.size() && i < after.size(); i++) {
            String b = before.get(i).path("status").asText("pending");
            String a = after.get(i).path("status").asText("pending");
            if (!"done".equalsIgnoreCase(b) && "done".equalsIgnoreCase(a)) {
                n++;
            }
        }
        return n;
    }
}
