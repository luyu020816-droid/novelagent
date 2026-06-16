package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.chapter.SubtextLedgerEntity;
import com.mythosforge.chapter.SubtextLedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 叙事债务：从子文本/汇合/定稿摘要同步，并格式化为 PlotPilot 式 DEBT_DUE 块。
 */
@Service
public class NarrativeDebtService {

    private final NarrativeDebtRepository debtRepository;
    private final SubtextLedgerRepository subtextLedgerRepository;
    private final NarrativeConfluenceRepository confluenceRepository;
    private final NarrativeStorylineRepository storylineRepository;
    private final ObjectMapper objectMapper;

    public NarrativeDebtService(
            NarrativeDebtRepository debtRepository,
            SubtextLedgerRepository subtextLedgerRepository,
            NarrativeConfluenceRepository confluenceRepository,
            NarrativeStorylineRepository storylineRepository,
            ObjectMapper objectMapper
    ) {
        this.debtRepository = debtRepository;
        this.subtextLedgerRepository = subtextLedgerRepository;
        this.confluenceRepository = confluenceRepository;
        this.storylineRepository = storylineRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void syncFromProjectSources(String projectId, int referenceChapter) {
        for (SubtextLedgerEntity st : subtextLedgerRepository.findByProjectIdAndStatusOrderByChapterNoAsc(
                projectId,
                "pending"
        )) {
            upsertSubtextDebt(projectId, st, referenceChapter);
        }
        for (NarrativeConfluenceEntity c : confluenceRepository.findByProjectIdOrderByTargetChapterAscCreatedAtAsc(
                projectId
        )) {
            if (!c.isResolved() && c.getTargetChapter() < referenceChapter) {
                upsertConfluenceDebt(projectId, c);
            }
        }
        for (NarrativeStorylineEntity sl : storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(
                projectId
        )) {
            if ("ACTIVE".equalsIgnoreCase(sl.getStatus())
                    && sl.getEstEndChapter() != null
                    && sl.getEstEndChapter() < referenceChapter) {
                upsertStorylineDebt(projectId, sl);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<NarrativeDebtEntity> listOpenDebts(String projectId) {
        return debtRepository.findByProjectIdAndStatusOrderByImportanceDescPlantedChapterAsc(projectId, "open");
    }

    @Transactional(readOnly = true)
    public ArrayNode openDebtsJson(String projectId, int chapterNo) {
        ArrayNode arr = objectMapper.createArrayNode();
        for (NarrativeDebtEntity d : listOpenDebts(projectId)) {
            arr.add(debtNode(d, chapterNo));
        }
        return arr;
    }

    public String formatDebtDueBlock(List<NarrativeDebtEntity> debts, int chapterNo) {
        if (debts.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder("【📋 叙事备忘 — 到期/逾期债务（宜在本章或近章回应）】\n");
        int n = 0;
        for (NarrativeDebtEntity d : debts) {
            if (n >= 12) {
                b.append("…（另有 ").append(debts.size() - n).append(" 条未列出）\n");
                break;
            }
            boolean overdue = d.getDueChapter() != null && d.getDueChapter() < chapterNo;
            boolean dueNow = d.getDueChapter() != null && d.getDueChapter() == chapterNo;
            String tag = overdue ? "逾期" : (dueNow ? "本章" : "待办");
            b.append("- [").append(tag).append("][").append(d.getDebtType()).append("] ");
            b.append(d.getDescription());
            if (d.getDueChapter() != null) {
                b.append("（建议第").append(d.getDueChapter()).append("章）");
            }
            b.append("\n");
            n++;
        }
        return b.toString().trim();
    }

    @Transactional
    public void ingestForeshadowingFromSummary(String projectId, int chapterNo, JsonNode pendingForeshadowing) {
        if (pendingForeshadowing == null || pendingForeshadowing.isNull()) {
            return;
        }
        if (pendingForeshadowing.isArray()) {
            for (JsonNode item : pendingForeshadowing) {
                ingestOneForeshadow(projectId, chapterNo, item);
            }
        }
    }

    @Transactional
    public void resolveBySourceRef(String projectId, String sourceRef, int resolvedChapter) {
        debtRepository.findByProjectIdAndSourceRef(projectId, sourceRef).ifPresent(d -> {
            d.setStatus("resolved");
            d.setResolvedChapter(resolvedChapter);
            debtRepository.save(d);
        });
    }

    private void ingestOneForeshadow(String projectId, int chapterNo, JsonNode item) {
        String text;
        Integer due = null;
        int importance = 2;
        if (item.isTextual()) {
            text = item.asText("").trim();
        } else if (item.isObject()) {
            text = item.path("text").asText(item.path("description").asText("")).trim();
            if (item.has("suggested_resolve_chapter") && !item.get("suggested_resolve_chapter").isNull()) {
                due = item.get("suggested_resolve_chapter").asInt();
            } else if (item.has("suggestedResolveChapter") && !item.get("suggestedResolveChapter").isNull()) {
                due = item.get("suggestedResolveChapter").asInt();
            }
            if (item.has("importance") && item.get("importance").canConvertToInt()) {
                importance = Math.min(4, Math.max(1, item.get("importance").asInt()));
            }
            if (item.path("abandoned").asBoolean(false)) {
                return;
            }
        } else {
            return;
        }
        if (text.isBlank()) {
            return;
        }
        String ref = "foreshadow:" + chapterNo + ":" + text.hashCode();
        NarrativeDebtEntity e = debtRepository.findByProjectIdAndSourceRef(projectId, ref).orElseGet(NarrativeDebtEntity::new);
        if (e.getId() == null) {
            e.setId(UUID.randomUUID().toString().replace("-", ""));
            e.setProjectId(projectId);
            e.setSourceRef(ref);
        }
        e.setDebtType("foreshadowing");
        e.setDescription(text);
        e.setPlantedChapter(chapterNo);
        e.setDueChapter(due);
        e.setImportance(importance);
        e.setStatus("open");
        e.setContext("定稿摘要 pending_foreshadowing");
        debtRepository.save(e);
    }

    private void upsertSubtextDebt(String projectId, SubtextLedgerEntity st, int referenceChapter) {
        String ref = "subtext:" + st.getId();
        NarrativeDebtEntity e = debtRepository.findByProjectIdAndSourceRef(projectId, ref).orElseGet(NarrativeDebtEntity::new);
        if (e.getId() == null) {
            e.setId(UUID.randomUUID().toString().replace("-", ""));
            e.setProjectId(projectId);
            e.setSourceRef(ref);
        }
        e.setDebtType("subtext");
        e.setDescription(st.getQuestion());
        e.setPlantedChapter(st.getChapterNo());
        e.setDueChapter(st.getSuggestedResolveChapter());
        e.setImportance("high".equalsIgnoreCase(st.getImportance()) ? 3 : 2);
        e.setStatus("pending".equalsIgnoreCase(st.getStatus()) ? "open" : "resolved");
        if (st.getCharacterRef() != null) {
            e.setInvolvedEntities(objectMapper.createArrayNode().add(st.getCharacterRef()));
        }
        e.setContext("子文本账本");
        if (st.getSuggestedResolveChapter() != null && st.getSuggestedResolveChapter() < referenceChapter) {
            e.setImportance(Math.min(4, e.getImportance() + 1));
        }
        debtRepository.save(e);
    }

    private void upsertConfluenceDebt(String projectId, NarrativeConfluenceEntity c) {
        String ref = "confluence:" + c.getId();
        NarrativeDebtEntity e = debtRepository.findByProjectIdAndSourceRef(projectId, ref).orElseGet(NarrativeDebtEntity::new);
        if (e.getId() == null) {
            e.setId(UUID.randomUUID().toString().replace("-", ""));
            e.setProjectId(projectId);
            e.setSourceRef(ref);
        }
        e.setDebtType("storyline");
        e.setDescription("汇合逾期未 resolved：第" + c.getTargetChapter() + "章 " + c.getConfluenceType());
        e.setPlantedChapter(c.getTargetChapter());
        e.setDueChapter(c.getTargetChapter());
        e.setImportance(4);
        e.setStatus("open");
        if (c.getContextSummary() != null) {
            e.setContext(c.getContextSummary());
        }
        debtRepository.save(e);
    }

    private void upsertStorylineDebt(String projectId, NarrativeStorylineEntity sl) {
        String ref = "storyline:" + sl.getId();
        NarrativeDebtEntity e = debtRepository.findByProjectIdAndSourceRef(projectId, ref).orElseGet(NarrativeDebtEntity::new);
        if (e.getId() == null) {
            e.setId(UUID.randomUUID().toString().replace("-", ""));
            e.setProjectId(projectId);
            e.setSourceRef(ref);
        }
        e.setDebtType("storyline");
        e.setDescription("故事线逾预计结束章仍未收束：" + (sl.getTitle() != null ? sl.getTitle() : sl.getStorylineKey()));
        e.setPlantedChapter(sl.getEstStartChapter() != null ? sl.getEstStartChapter() : 1);
        e.setDueChapter(sl.getEstEndChapter());
        e.setImportance(3);
        e.setStatus("open");
        debtRepository.save(e);
    }

    private ObjectNode debtNode(NarrativeDebtEntity d, int chapterNo) {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("id", d.getId());
        o.put("debtType", d.getDebtType());
        o.put("description", d.getDescription());
        o.put("plantedChapter", d.getPlantedChapter());
        if (d.getDueChapter() != null) {
            o.put("dueChapter", d.getDueChapter());
        }
        o.put("importance", d.getImportance());
        boolean overdue = d.getDueChapter() != null && d.getDueChapter() < chapterNo;
        o.put("overdue", overdue);
        if (d.getContext() != null) {
            o.put("context", d.getContext());
        }
        return o;
    }
}
