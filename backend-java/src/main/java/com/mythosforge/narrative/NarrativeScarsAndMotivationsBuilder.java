package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.mythosforge.chapter.MemorySummaryEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PlotPilot SCARS_AND_MOTIVATIONS：伤疤与执念，来自 Story Contract + 近期章后人物状态。
 */
public final class NarrativeScarsAndMotivationsBuilder {

    private NarrativeScarsAndMotivationsBuilder() {}

    public static String build(JsonNode storyJson, List<MemorySummaryEntity> recentMemories, int maxChars) {
        StringBuilder b = new StringBuilder("【💔 角色伤疤与执念】\n");
        Set<String> lines = new LinkedHashSet<>();

        if (storyJson != null && storyJson.isObject()) {
            JsonNode pro = storyJson.get("protagonist");
            if (pro != null && pro.isObject()) {
                String name = text(pro, "name");
                if (name.isBlank()) {
                    name = "主角";
                }
                addIf(lines, name, "弱点", text(pro, "weakness"));
                addIf(lines, name, "秘密/隐患", text(pro, "secret"));
                addIf(lines, name, "执念", text(pro, "desire"));
                addIf(lines, name, "成长弧", text(pro, "growthArc"));
                if (lines.isEmpty()) {
                    addIf(lines, name, "执念", text(pro, "growth_arc"));
                }
            }
            JsonNode chars = storyJson.get("characters");
            if (chars != null && chars.isArray()) {
                for (JsonNode c : chars) {
                    if (!c.isObject()) {
                        continue;
                    }
                    String nm = text(c, "name");
                    if (nm.isBlank()) {
                        continue;
                    }
                    addIf(lines, nm, "钩子", text(c, "oneLineHook"));
                    addIf(lines, nm, "与主角", text(c, "relationshipToProtagonist"));
                }
            }
        }

        List<MemorySummaryEntity> mems = recentMemories != null ? recentMemories : List.of();
        int fromMem = 0;
        for (int i = mems.size() - 1; i >= 0 && fromMem < 4; i--) {
            MemorySummaryEntity m = mems.get(i);
            JsonNode cs = m.getCharacterStateChanges();
            if (cs != null && cs.has("narrative")) {
                String narrative = cs.get("narrative").asText("").trim();
                if (!narrative.isBlank()) {
                    lines.add("第" + m.getChapterNo() + "章后状态：" + truncate(narrative, 120));
                    fromMem++;
                }
            }
        }

        for (String line : lines) {
            b.append("- ").append(line).append("\n");
        }
        String out = b.toString().trim();
        if (out.equals("【💔 角色伤疤与执念】")) {
            return "";
        }
        return truncate(out, maxChars);
    }

    private static void addIf(Set<String> lines, String who, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        lines.add("【" + who + "】" + label + "：" + truncate(value, 100));
    }

    private static String text(JsonNode n, String key) {
        if (n == null || !n.has(key) || n.get(key).isNull()) {
            String snake = camelToSnake(key);
            if (n != null && n.has(snake) && !n.get(snake).isNull()) {
                return n.get(snake).asText("").trim();
            }
            return "";
        }
        return n.get(key).asText("").trim();
    }

    private static String camelToSnake(String key) {
        if ("growthArc".equals(key)) {
            return "growth_arc";
        }
        if ("oneLineHook".equals(key)) {
            return "one_line_hook";
        }
        if ("relationshipToProtagonist".equals(key)) {
            return "relationship_to_protagonist";
        }
        return key;
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }
}
