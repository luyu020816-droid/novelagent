package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将 PG 故事线/汇合点格式化为 Curator 可读的短行（PlotPilot storyline slot 精简版）。
 */
public final class NarrativePromptLinesBuilder {

    private NarrativePromptLinesBuilder() {}

    public static List<String> build(
            int chapterNo,
            List<NarrativeStorylineEntity> activeLines,
            List<NarrativeConfluenceEntity> dueConfluences,
            Map<String, NarrativeStorylineEntity> lineById
    ) {
        List<String> lines = new ArrayList<>();
        if (activeLines.isEmpty() && dueConfluences.isEmpty()) {
            return lines;
        }
        lines.add("━━━ 故事线上下文（本章）━━━");

        List<NarrativeStorylineEntity> sortedActive = new ArrayList<>(activeLines);
        sortedActive.sort((a, b) -> Integer.compare(roleOrder(a.getStorylineRole()), roleOrder(b.getStorylineRole())));

        for (NarrativeStorylineEntity sl : sortedActive) {
            lines.addAll(formatStorylineBlock(chapterNo, sl, dueConfluences));
        }

        for (NarrativeConfluenceEntity c : dueConfluences) {
            if (lineAlreadyHasConfluenceHint(lines, c.getId())) {
                continue;
            }
            lines.add(formatConfluenceOnlyBlock(chapterNo, c, lineById));
        }
        return lines;
    }

    private static List<String> formatStorylineBlock(
            int chapterNo,
            NarrativeStorylineEntity sl,
            List<NarrativeConfluenceEntity> allConfluences
    ) {
        List<String> out = new ArrayList<>();
        NarrativeConfluenceEntity near = nearestUnresolvedConfluence(sl.getId(), chapterNo, allConfluences);
        String roleLabel = roleLabel(sl.getStorylineRole());
        String name = sl.getTitle() != null ? sl.getTitle() : sl.getStorylineKey();

        if (isDarkRevealEarly(sl, near, chapterNo)) {
            out.add(String.format("● [暗线 ◎ 第%d章揭露] 「%s」", near.getTargetChapter(), name));
            if (near.getPreRevealHint() != null && !near.getPreRevealHint().isBlank()) {
                out.add("  " + near.getPreRevealHint().trim());
            }
            appendBehaviorGuards(out, near.getBehaviorGuards());
            return out;
        }

        String suffix = "";
        if (near != null) {
            int dist = near.getTargetChapter() - chapterNo;
            suffix = String.format(" ↘ 第%d章汇合(%s)", near.getTargetChapter(), near.getConfluenceType());
            if (dist == 0) {
                suffix += " 【本章汇合章】";
            }
        }
        out.add(String.format("● [%s] 「%s」%s", roleLabel, name, suffix));

        if (sl.getProgressSummary() != null && !sl.getProgressSummary().isBlank()) {
            out.add("  当前进度：" + truncate(sl.getProgressSummary(), 200));
        }
        if (near != null) {
            int dist = near.getTargetChapter() - chapterNo;
            if (dist <= 2 && near.getContextSummary() != null && !near.getContextSummary().isBlank()) {
                out.add("  ⚠️ 距汇合 " + dist + " 章：" + truncate(near.getContextSummary(), 120));
            } else if (dist <= 8 && near.getContextSummary() != null) {
                out.add("  预期汇合：" + truncate(near.getContextSummary(), 80));
            }
        }
        return out;
    }

    private static String formatConfluenceOnlyBlock(
            int chapterNo,
            NarrativeConfluenceEntity c,
            Map<String, NarrativeStorylineEntity> lineById
    ) {
        String pTitle = titleOf(lineById.get(c.getPrimaryStorylineId()));
        String sTitle = titleOf(lineById.get(c.getSecondaryStorylineId()));
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("● [汇合章] %s × %s → 第%d章 (%s)", pTitle, sTitle, c.getTargetChapter(), c.getConfluenceType()));
        if (c.getContextSummary() != null && !c.getContextSummary().isBlank()) {
            sb.append("：").append(truncate(c.getContextSummary(), 100));
        }
        return sb.toString();
    }

    private static NarrativeConfluenceEntity nearestUnresolvedConfluence(
            String storylineId,
            int chapterNo,
            List<NarrativeConfluenceEntity> all
    ) {
        NarrativeConfluenceEntity best = null;
        int minDist = Integer.MAX_VALUE;
        for (NarrativeConfluenceEntity c : all) {
            if (c.isResolved()) {
                continue;
            }
            boolean involved = storylineId.equals(c.getPrimaryStorylineId())
                    || storylineId.equals(c.getSecondaryStorylineId());
            if (!involved) {
                continue;
            }
            int dist = c.getTargetChapter() - chapterNo;
            if (dist >= 0 && dist < minDist) {
                minDist = dist;
                best = c;
            }
        }
        return best;
    }

    private static boolean isDarkRevealEarly(NarrativeStorylineEntity sl, NarrativeConfluenceEntity near, int chapterNo) {
        if (near == null) {
            return false;
        }
        if (!"DARK".equalsIgnoreCase(normalizeRole(sl.getStorylineRole()))) {
            return false;
        }
        if (!"reveal".equalsIgnoreCase(near.getConfluenceType())) {
            return false;
        }
        return near.getTargetChapter() - chapterNo > 2;
    }

    private static void appendBehaviorGuards(List<String> out, JsonNode guards) {
        if (guards == null || guards.isNull()) {
            return;
        }
        if (guards.isArray()) {
            for (JsonNode g : guards) {
                if (g.isTextual() && !g.asText().isBlank()) {
                    out.add("  禁忌：" + g.asText().trim());
                }
            }
        }
    }

    private static int roleOrder(String role) {
        return switch (normalizeRole(role)) {
            case "MAIN" -> 0;
            case "SUB" -> 1;
            case "DARK" -> 2;
            default -> 3;
        };
    }

    private static String roleLabel(String role) {
        return switch (normalizeRole(role)) {
            case "MAIN" -> "主线";
            case "DARK" -> "暗线";
            default -> "支线";
        };
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "SUB";
        }
        return role.trim().toUpperCase();
    }

    private static String titleOf(NarrativeStorylineEntity sl) {
        if (sl == null) {
            return "?";
        }
        if (sl.getTitle() != null && !sl.getTitle().isBlank()) {
            return sl.getTitle();
        }
        return sl.getStorylineKey();
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    private static boolean lineAlreadyHasConfluenceHint(List<String> lines, String confluenceId) {
        return false;
    }
}
