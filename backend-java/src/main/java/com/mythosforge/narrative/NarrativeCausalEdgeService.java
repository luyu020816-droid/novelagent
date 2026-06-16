package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 未闭环因果链：定稿后从 key_events 抽取，注入 CAUSAL_CHAINS 槽。 */
@Service
public class NarrativeCausalEdgeService {

    private final NarrativeCausalEdgeRepository causalEdgeRepository;
    private final ObjectMapper objectMapper;

    public NarrativeCausalEdgeService(
            NarrativeCausalEdgeRepository causalEdgeRepository,
            ObjectMapper objectMapper
    ) {
        this.causalEdgeRepository = causalEdgeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<NarrativeCausalEdgeEntity> listOpen(String projectId) {
        return causalEdgeRepository.findByProjectIdAndStatusOrderByImportanceDescPlantedChapterAsc(
                projectId,
                "open"
        );
    }

    @Transactional(readOnly = true)
    public ArrayNode openEdgesJson(String projectId, int chapterNo) {
        ArrayNode arr = objectMapper.createArrayNode();
        for (NarrativeCausalEdgeEntity e : listOpen(projectId)) {
            arr.add(edgeNode(e, chapterNo));
        }
        return arr;
    }

    public String formatCausalChainsBlock(List<NarrativeCausalEdgeEntity> edges, int chapterNo) {
        if (edges.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder("【🔗 未闭环因果链 — 正文宜体现因果承接】\n");
        int n = 0;
        for (NarrativeCausalEdgeEntity e : edges) {
            if (n >= 10) {
                break;
            }
            b.append("- 因：").append(e.getCauseSummary());
            if (e.getEffectSummary() != null && !e.getEffectSummary().isBlank()) {
                b.append(" → 果：").append(e.getEffectSummary());
            } else {
                b.append(" → 果：（待写）");
            }
            if (e.getDueChapter() != null) {
                boolean overdue = e.getDueChapter() < chapterNo;
                b.append(overdue ? " [逾期]" : (e.getDueChapter() == chapterNo ? " [本章]" : ""));
            }
            b.append("\n");
            n++;
        }
        return b.toString().trim();
    }

    @Transactional
    public void ingestFromChapterSummary(String projectId, int chapterNo, JsonNode summary) {
        if (summary == null || !summary.isObject()) {
            return;
        }
        JsonNode events = summary.get("key_events");
        if (events == null || !events.isArray()) {
            return;
        }
        int idx = 0;
        for (JsonNode ev : events) {
            if (!ev.isTextual() || ev.asText("").isBlank()) {
                continue;
            }
            String cause = ev.asText().trim();
            if (cause.length() < 4) {
                continue;
            }
            String ref = "event:" + chapterNo + ":" + idx++;
            NarrativeCausalEdgeEntity e = new NarrativeCausalEdgeEntity();
            e.setId(UUID.randomUUID().toString().replace("-", ""));
            e.setProjectId(projectId);
            e.setCauseSummary(cause);
            e.setEffectSummary("（待后续章节兑现）");
            e.setPlantedChapter(chapterNo);
            e.setDueChapter(chapterNo + 3);
            e.setImportance(2);
            e.setStatus("open");
            e.setSourceChapter(chapterNo);
            causalEdgeRepository.save(e);
            if (idx >= 6) {
                break;
            }
        }
    }

    @Transactional
    public void resolveEdgesForChapter(String projectId, int chapterNo) {
        for (NarrativeCausalEdgeEntity e : listOpen(projectId)) {
            if (e.getDueChapter() != null && e.getDueChapter() <= chapterNo) {
                e.setStatus("resolved");
                e.setResolvedChapter(chapterNo);
                causalEdgeRepository.save(e);
            }
        }
    }

    private ObjectNode edgeNode(NarrativeCausalEdgeEntity e, int chapterNo) {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("id", e.getId());
        o.put("causeSummary", e.getCauseSummary());
        if (e.getEffectSummary() != null) {
            o.put("effectSummary", e.getEffectSummary());
        }
        o.put("plantedChapter", e.getPlantedChapter());
        if (e.getDueChapter() != null) {
            o.put("dueChapter", e.getDueChapter());
            o.put("overdue", e.getDueChapter() < chapterNo);
        }
        return o;
    }
}
