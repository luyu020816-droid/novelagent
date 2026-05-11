package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.JsonNode;

/** Day 15：从 Writer 成品 JSON 推导 ROI 辅助指标。 */
public final class GenerationRoiCalculator {

    private GenerationRoiCalculator() {}

    public static int trimmedOptionalCount(JsonNode tokenBudgetStatus) {
        if (tokenBudgetStatus == null || tokenBudgetStatus.isNull()) {
            return 0;
        }
        JsonNode dropped = tokenBudgetStatus.get("dropped_optional_categories");
        if (dropped == null || dropped.isNull()) {
            dropped = tokenBudgetStatus.get("droppedOptionalCategories");
        }
        if (dropped != null && dropped.isArray()) {
            return dropped.size();
        }
        return 0;
    }

    /**
     * 近似「重试沉没」：假设 ghostwriter+critic 为一轮；retry_count=R 表示共 R 轮额外重写，
     * 浪费占比 ≈ R/(1+R) ×（ghost+critic 本轮累计 tokens）。
     */
    public static long retryWasteTokens(JsonNode llmUsageSummary, int retryCount) {
        if (retryCount <= 0 || llmUsageSummary == null || llmUsageSummary.isNull()) {
            return 0;
        }
        JsonNode byNode = llmUsageSummary.get("by_node");
        if (byNode == null || byNode.isNull()) {
            byNode = llmUsageSummary.get("byNode");
        }
        if (byNode == null || !byNode.isObject()) {
            return 0;
        }
        long ghost = nodeTotalTokens(byNode.get("ghostwriter"));
        long critic = nodeTotalTokens(byNode.get("critic"));
        long cycle = ghost + critic;
        if (cycle <= 0) {
            return 0;
        }
        return Math.round((double) retryCount / (double) (1 + retryCount) * (double) cycle);
    }

    private static long nodeTotalTokens(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }
        if (node.hasNonNull("total_tokens")) {
            return node.get("total_tokens").asLong(0);
        }
        if (node.hasNonNull("totalTokens")) {
            return node.get("totalTokens").asLong(0);
        }
        return 0;
    }

    public static Long longOrNull(JsonNode parent, String snake, String camel) {
        if (parent == null || parent.isNull()) {
            return null;
        }
        if (parent.hasNonNull(snake)) {
            return parent.get(snake).asLong();
        }
        if (parent.hasNonNull(camel)) {
            return parent.get(camel).asLong();
        }
        return null;
    }
}
