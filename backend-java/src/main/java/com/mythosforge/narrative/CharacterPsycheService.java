package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.chapter.MemorySummaryEntity;
import com.mythosforge.chapter.MemorySummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 按章人物 psyche 快照：定稿后写入，供后续 SCARS 槽与读模型使用。 */
@Service
public class CharacterPsycheService {

    private final CharacterPsycheSnapshotRepository snapshotRepository;
    private final MemorySummaryRepository memorySummaryRepository;
    private final ObjectMapper objectMapper;

    public CharacterPsycheService(
            CharacterPsycheSnapshotRepository snapshotRepository,
            MemorySummaryRepository memorySummaryRepository,
            ObjectMapper objectMapper
    ) {
        this.snapshotRepository = snapshotRepository;
        this.memorySummaryRepository = memorySummaryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void captureFromAcceptSummary(String projectId, int chapterNo, JsonNode storyJson, JsonNode summary) {
        if (summary == null || !summary.isObject()) {
            return;
        }
        String narrative = summary.path("character_state").asText("").trim();
        if (narrative.isBlank()) {
            return;
        }
        String protagonistName = "主角";
        if (storyJson != null && storyJson.isObject()) {
            JsonNode pro = storyJson.get("protagonist");
            if (pro != null && pro.has("name")) {
                protagonistName = pro.get("name").asText("主角").trim();
            }
        }
        CharacterPsycheSnapshotEntity snap = snapshotRepository
                .findByProjectIdAndChapterNoAndCharacterRef(projectId, chapterNo, protagonistName)
                .orElseGet(CharacterPsycheSnapshotEntity::new);
        if (snap.getId() == null) {
            snap.setId(UUID.randomUUID().toString().replace("-", ""));
            snap.setProjectId(projectId);
            snap.setChapterNo(chapterNo);
            snap.setCharacterRef(protagonistName);
        }
        snap.setEmotionalState(truncate(narrative, 500));
        ObjectNode masks = objectMapper.createObjectNode();
        masks.put("public_mask", "章后摘要");
        snap.setMasksJson(masks);
        if (storyJson != null && storyJson.isObject()) {
            JsonNode pro = storyJson.get("protagonist");
            if (pro != null && pro.isObject()) {
                snap.setScarsText(text(pro, "weakness"));
                snap.setMotivationsText(text(pro, "desire"));
            }
        }
        ObjectNode full = objectMapper.createObjectNode();
        full.put("narrative", narrative);
        snap.setSnapshotJson(full);
        snapshotRepository.save(snap);
    }

    @Transactional(readOnly = true)
    public List<MemorySummaryEntity> recentMemoriesForScars(String projectId, int upToChapter) {
        List<MemorySummaryEntity> all = memorySummaryRepository.findByProjectIdOrderByChapterNoAsc(projectId);
        List<MemorySummaryEntity> out = new ArrayList<>();
        for (MemorySummaryEntity m : all) {
            if (m.getChapterNo() <= upToChapter) {
                out.add(m);
            }
        }
        return out;
    }

    private static String text(JsonNode n, String key) {
        if (n == null || !n.has(key)) {
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
