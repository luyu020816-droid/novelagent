package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Set;

/**
 * milestones_json 最小 schema：非空时须为对象数组，每项含唯一 order，targetChapter 若存在须 ≥1。
 */
public final class MilestoneSchemaValidator {

    private MilestoneSchemaValidator() {}

    /** 不通过时抛 {@link IllegalArgumentException}；null 或空数组视为合法。 */
    public static void validate(JsonNode milestones) {
        if (milestones == null || milestones.isNull()) {
            return;
        }
        if (!milestones.isArray()) {
            throw new IllegalArgumentException("milestones 须为 JSON 数组");
        }
        Set<Integer> orders = new HashSet<>();
        for (JsonNode item : milestones) {
            if (!item.isObject()) {
                throw new IllegalArgumentException("milestones 每项须为对象");
            }
            if (!item.has("order")) {
                throw new IllegalArgumentException("milestones 每项须含 order");
            }
            int order = item.path("order").asInt(-1);
            if (order < 1) {
                throw new IllegalArgumentException("milestone order 须 ≥1");
            }
            if (!orders.add(order)) {
                throw new IllegalArgumentException("milestone order 重复: " + order);
            }
            if (item.has("targetChapter") && !item.path("targetChapter").isNull()) {
                int tc = item.path("targetChapter").asInt(0);
                if (tc < 1) {
                    throw new IllegalArgumentException("milestone targetChapter 须 ≥1");
                }
            }
        }
    }
}
