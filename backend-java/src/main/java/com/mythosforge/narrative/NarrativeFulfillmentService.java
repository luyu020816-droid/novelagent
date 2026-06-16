package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.writer.WriterHttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 对照本章任务单评估正文是否「履约」：先本地启发式（关键词），再尝试 Writer LLM 合并结果。
 * <p>
 * 报告写入 {@code chapter_versions.fulfillment_report_json}，供 Autopilot {@code CRITIC_PASS_AND_NARRATIVE} 读取。
 * </p>
 */
@Service
public class NarrativeFulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(NarrativeFulfillmentService.class);

    private final ChapterObligationsService chapterObligationsService;
    private final WriterHttpService writerHttpService;
    private final ObjectMapper objectMapper;

    public NarrativeFulfillmentService(
            ChapterObligationsService chapterObligationsService,
            WriterHttpService writerHttpService,
            ObjectMapper objectMapper
    ) {
        this.chapterObligationsService = chapterObligationsService;
        this.writerHttpService = writerHttpService;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成履约报告；Writer 不可用时仅返回启发式结果（{@code source=heuristic}）。
     */
    public ObjectNode evaluate(String projectId, int chapterNo, String chapterText) {
        ObjectNode obligations = chapterObligationsService.buildChapterObligations(projectId, chapterNo);
        ObjectNode heuristic = heuristicFulfillment(obligations, chapterText);
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("projectId", projectId);
            body.put("chapterNo", chapterNo);
            body.put("chapterText", chapterText);
            body.set("chapterObligations", obligations);
            JsonNode llm = writerHttpService.postJson("/api/writer/chapters/narrative-fulfillment", body);
            if (llm != null && llm.isObject()) {
                ObjectNode merged = heuristic.deepCopy();
                if (llm.has("overallPass")) {
                    merged.put("overallPass", llm.path("overallPass").asBoolean(heuristic.path("overallPass").asBoolean(true)));
                }
                if (llm.has("summaryLine")) {
                    merged.put("summaryLine", llm.path("summaryLine").asText());
                }
                merged.put("source", "hybrid");
                return merged;
            }
        } catch (Exception ex) {
            log.debug("narrative fulfillment LLM skipped: {}", ex.getMessage());
        }
        heuristic.put("source", "heuristic");
        return heuristic;
    }

    private ObjectNode heuristicFulfillment(ObjectNode obligations, String chapterText) {
        String text = chapterText == null ? "" : chapterText;
        String lower = text.toLowerCase();
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode confResults = objectMapper.createArrayNode();
        boolean confOk = true;
        JsonNode conf = obligations.get("dueConfluences");
        if (conf != null && conf.isArray()) {
            for (JsonNode c : conf) {
                ObjectNode row = objectMapper.createObjectNode();
                row.put("id", c.path("id").asText());
                String hint = c.path("contextSummary").asText("");
                if (hint.isBlank()) {
                    hint = c.path("notes").asText("");
                }
                boolean pass = hint.isBlank() || text.contains(hint) || lower.contains(hint.toLowerCase());
                row.put("pass", pass);
                row.put("note", pass ? "ok" : "未检测到汇合摘要/备注关键词");
                if (!pass) {
                    confOk = false;
                }
                confResults.add(row);
            }
        }
        root.set("confluenceFulfilled", confResults);

        ArrayNode subResults = objectMapper.createArrayNode();
        boolean subOk = true;
        JsonNode window = obligations.get("dueSubtextInWindow");
        if (window != null && window.isArray()) {
            for (JsonNode s : window) {
                ObjectNode row = objectMapper.createObjectNode();
                row.put("id", s.path("id").asText());
                String q = s.path("question").asText("");
                String probe = q.length() > 12 ? q.substring(0, 12) : q;
                boolean pass = probe.isBlank() || text.contains(probe);
                row.put("pass", pass);
                row.put("note", pass ? "ok" : "子文本疑问关键词未出现");
                if (!pass) {
                    subOk = false;
                }
                subResults.add(row);
            }
        }
        root.set("subtextAddressed", subResults);
        root.put("overallPass", confOk && subOk);
        root.put("summaryLine", confOk && subOk ? "启发式校验通过" : "启发式校验：部分任务单条目未在正文中体现");
        return root;
    }
}
