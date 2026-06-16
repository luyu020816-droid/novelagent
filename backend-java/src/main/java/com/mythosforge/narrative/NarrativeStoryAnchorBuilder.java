package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.mythosforge.project.Project;

/**
 * PlotPilot STORY_ANCHOR：全书主线锚点 ≤300 字。
 */
public final class NarrativeStoryAnchorBuilder {

    private NarrativeStoryAnchorBuilder() {}

    public static String build(JsonNode storyJson, Project project) {
        if (storyJson == null || storyJson.isNull() || !storyJson.isObject()) {
            return fallback(project);
        }
        StringBuilder b = new StringBuilder("【📖 全书主线锚点（绝不可忘）】\n");
        JsonNode pos = storyJson.get("positioning");
        if (pos != null && pos.isObject()) {
            appendLine(b, "体裁", pos, "genre");
            appendLine(b, "核心钩子", pos, "coreHook");
            appendLine(b, "基调", pos, "tone");
        }
        JsonNode pro = storyJson.get("protagonist");
        if (pro != null && pro.isObject()) {
            String name = text(pro, "name");
            String desire = text(pro, "desire");
            if (!name.isBlank()) {
                b.append("主角：").append(name);
                if (!desire.isBlank()) {
                    b.append(" — ").append(desire);
                }
                b.append("\n");
            }
            String weakness = text(pro, "weakness");
            if (!weakness.isBlank()) {
                b.append("内在弱点：").append(truncate(weakness, 80)).append("\n");
            }
        }
        String fv = text(storyJson, "firstVolumeDirection");
        if (fv.isBlank()) {
            fv = text(storyJson, "first_volume_direction");
        }
        if (!fv.isBlank()) {
            b.append("第一卷走向：").append(truncate(fv, 100)).append("\n");
        }
        if (project != null && project.getName() != null) {
            b.append("书名：").append(project.getName()).append("\n");
        }
        String out = b.toString().trim();
        return truncate(out, 300);
    }

    private static String fallback(Project project) {
        if (project == null || project.getName() == null) {
            return "";
        }
        return "【📖 全书主线锚点】\n书名：" + project.getName();
    }

    private static void appendLine(StringBuilder b, String label, JsonNode obj, String key) {
        String v = text(obj, key);
        if (!v.isBlank()) {
            b.append(label).append("：").append(truncate(v, 60)).append("\n");
        }
    }

    private static String text(JsonNode n, String key) {
        if (n == null || !n.has(key) || n.get(key).isNull()) {
            return "";
        }
        return n.get(key).asText("").trim();
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }
}
