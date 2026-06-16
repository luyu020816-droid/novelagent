package com.mythosforge.genre;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.genre.dto.GenreContractDetailResponse;
import com.mythosforge.genre.dto.GenreContractUpdateRequest;
import com.mythosforge.genre.dto.GenreInterviewChatTurn;
import com.mythosforge.genre.dto.GenreInterviewRequest;
import com.mythosforge.genre.dto.GenreInterviewResponse;
import com.mythosforge.genre.dto.GenreRecommendRequest;
import com.mythosforge.genre.dto.GenreRecommendResponse;
import com.mythosforge.genre.dto.GenreStoryHookStreamRequest;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.writer.WriterEngineClient;
import com.mythosforge.writer.WriterSseProxyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * 题材领域逻辑：调 Writer（阻塞 JSON 或经 {@link WriterSseProxyService} SSE）、解析产物写入 {@code genre_decision_contracts}、
 * 采访完成写 {@code novel_seed_contracts}、单条合同合并更新与项目选题联动删除。
 */
@Service
public class GenreService {

    private final ProjectRepository projectRepository;
    private final GenreDecisionContractRepository genreDecisionContractRepository;
    private final WriterEngineClient writerEngineClient;
    private final WriterSseProxyService writerSseProxyService;
    private final StorylineInterviewPersistence storylineInterviewPersistence;
    private final ObjectMapper objectMapper;

    public GenreService(
            ProjectRepository projectRepository,
            GenreDecisionContractRepository genreDecisionContractRepository,
            WriterEngineClient writerEngineClient,
            WriterSseProxyService writerSseProxyService,
            StorylineInterviewPersistence storylineInterviewPersistence,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.genreDecisionContractRepository = genreDecisionContractRepository;
        this.writerEngineClient = writerEngineClient;
        this.writerSseProxyService = writerSseProxyService;
        this.storylineInterviewPersistence = storylineInterviewPersistence;
        this.objectMapper = objectMapper;
    }

    /**
     * 路径 B：互动采访。asking 仅透传文案；complete 时组装 Novel Seed 形 JSON 并写入 novel_seed_contracts。
     */
    public GenreInterviewResponse interview(String projectId, GenreInterviewRequest req) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<GenreInterviewChatTurn> turns = req.chatHistory();
        boolean skillMode = req.writerSkillId() != null && !req.writerSkillId().isBlank();
        if (turns == null || turns.isEmpty()) {
            if (!skillMode) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "chatHistory must not be empty unless writerSkillId is set"
                );
            }
        } else {
            for (GenreInterviewChatTurn t : turns) {
                String r = t.role().trim().toLowerCase();
                if (!r.equals("user") && !r.equals("assistant") && !r.equals("system")) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "chatHistory.role must be user, assistant, or system"
                    );
                }
            }
        }

        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode hist = objectMapper.createArrayNode();
        if (turns != null) {
            for (GenreInterviewChatTurn t : turns) {
                ObjectNode turn = objectMapper.createObjectNode();
                turn.put("role", t.role().trim());
                turn.put("content", t.content());
                hist.add(turn);
            }
        }
        body.set("chatHistory", hist);
        body.put("projectId", projectId);
        if (skillMode) {
            body.put("writerSkillId", req.writerSkillId().trim());
        }

        JsonNode resp;
        try {
            resp = writerEngineClient.postGenreInterview(body);
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "writer-python genre/interview HTTP " + e.getStatusCode().value() + ": "
                            + e.getResponseBodyAsString(),
                    e
            );
        }

        String status = resp.path("status").asText("").trim();
        String replyToUser = interviewNodeText(resp, "replyToUser", "reply_to_user");

        if ("asking".equals(status)) {
            return new GenreInterviewResponse("asking", replyToUser, null, null, null);
        }
        if ("complete".equals(status)) {
            String finalSummary = interviewNodeText(resp, "finalSummary", "final_summary");
            JsonNode core = interviewNodeObject(resp, "coreSettings", "core_settings");
            if (finalSummary.isBlank() || core == null || !core.isObject()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Writer interview complete but missing finalSummary or coreSettings object"
                );
            }
            JsonNode payload = assembleStorylineNovelSeedJson(finalSummary, core);
            String novelSeedId = storylineInterviewPersistence.saveNovelSeedSnapshot(projectId, payload);
            return new GenreInterviewResponse("complete", replyToUser, finalSummary, core, novelSeedId);
        }

        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Writer interview unknown status: " + status);
    }

    @Transactional(readOnly = true)
    public GenreContractDetailResponse getGenreContract(String projectId, String contractId) {
        GenreDecisionContract row = loadGenreContractOwned(projectId, contractId);
        return new GenreContractDetailResponse(
                row.getId(),
                row.getProjectId(),
                row.getCreatedAt(),
                row.getSource(),
                row.getStoryHookText(),
                row.getRawJson()
        );
    }

    @Transactional
    public GenreContractDetailResponse updateGenreContract(
            String projectId,
            String contractId,
            GenreContractUpdateRequest req
    ) {
        GenreDecisionContract row = loadGenreContractOwned(projectId, contractId);
        JsonNode merged = mergeGenreContractUpdate(row.getRawJson(), req.rawJson(), req.selectedDirection());
        assertManualEditContractShape(merged);
        applyDenormalizedGenreColumns(row, merged);
        row.setRawJson(merged);
        genreDecisionContractRepository.save(row);
        return getGenreContract(projectId, contractId);
    }

    @Transactional
    public void deleteGenreContract(String projectId, String contractId) {
        GenreDecisionContract row = loadGenreContractOwned(projectId, contractId);
        genreDecisionContractRepository.delete(row);
        projectRepository.findById(projectId).ifPresent(p -> {
            if (contractId.equals(p.getSelectedGenreContractId())) {
                p.setSelectedGenreContractId(null);
                projectRepository.save(p);
            }
        });
    }

    private GenreDecisionContract loadGenreContractOwned(String projectId, String contractId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        GenreDecisionContract row = genreDecisionContractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!projectId.equals(row.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return row;
    }

    private JsonNode mergeGenreContractUpdate(JsonNode existingRaw, JsonNode newRaw, JsonNode newSelected) {
        boolean hasRaw = newRaw != null && !newRaw.isNull();
        boolean hasSel = newSelected != null && !newSelected.isNull();
        if (!hasRaw && !hasSel) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provide rawJson and/or selectedDirection"
            );
        }
        if (hasRaw) {
            JsonNode copy = newRaw.deepCopy();
            if (!(copy instanceof ObjectNode obj)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rawJson must be a JSON object");
            }
            if (hasSel) {
                obj.set("selectedDirection", newSelected);
                obj.set("selected_direction", newSelected);
            }
            return obj;
        }
        JsonNode baseCopy = existingRaw.deepCopy();
        if (!(baseCopy instanceof ObjectNode obj)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Existing raw_json must be an object");
        }
        obj.set("selectedDirection", newSelected);
        obj.set("selected_direction", newSelected);
        return obj;
    }

    private void assertManualEditContractShape(JsonNode contract) {
        JsonNode selected = pick(contract, "selectedDirection", "selected_direction");
        JsonNode rankings = pick(contract, "candidateRankings", "candidate_rankings");
        if (selected == null || selected.isNull() || selected.isMissingNode()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Contract JSON must include selectedDirection / selected_direction"
            );
        }
        if (rankings == null || rankings.isNull() || rankings.isMissingNode() || !rankings.isArray()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Contract JSON must include candidateRankings / candidate_rankings array"
            );
        }
    }

    private void applyDenormalizedGenreColumns(GenreDecisionContract row, JsonNode contract) {
        JsonNode selected = pick(contract, "selectedDirection", "selected_direction");
        JsonNode rankings = pick(contract, "candidateRankings", "candidate_rankings");
        JsonNode risks = optionalArray(contract, "riskNotes", "risk_notes");
        row.setSelectedDirection(selected);
        row.setCandidateRankings(rankings);
        row.setRiskNotes(risks);
    }

    private static String interviewNodeText(JsonNode root, String camel, String snake) {
        if (root.has(camel) && !root.get(camel).isNull()) {
            return root.get(camel).asText("");
        }
        if (root.has(snake) && !root.get(snake).isNull()) {
            return root.get(snake).asText("");
        }
        return "";
    }

    private JsonNode interviewNodeObject(JsonNode root, String camel, String snake) {
        JsonNode n = null;
        if (root.has(camel) && !root.get(camel).isNull()) {
            n = root.get(camel);
        } else if (root.has(snake) && !root.get(snake).isNull()) {
            n = root.get(snake);
        }
        return n;
    }

    private JsonNode assembleStorylineNovelSeedJson(String finalSummary, JsonNode core) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("source", "storyline_interview_complete");
        root.put("interviewFinalSummary", finalSummary);
        root.set("coreSettings", core);

        ArrayNode titles = objectMapper.createArrayNode();
        titles.add("（采访待定标题）");
        root.set("titleCandidates", titles);

        root.put("targetReader", coreTextField(core, "target_reader", "网文读者"));
        root.put("coreSellingPoint", finalSummary);
        root.put("protagonistArchetype", coreTextField(core, "protagonist", "待定主角"));
        root.put("goldenFinger", coreTextField(core, "golden_finger_or_edge", "无系统金手指；情节与人物关系驱动"));
        root.set("commercialPayoffs", objectMapper.createArrayNode());
        root.put("openingConflict", coreTextField(core, "core_conflict", "待定开篇冲突"));
        root.put("tone", coreTextField(core, "tone", "待定基调"));
        return root;
    }

    private String coreTextField(JsonNode core, String key, String defaultVal) {
        if (core != null && core.has(key) && core.get(key).isTextual()) {
            String s = core.get(key).asText().trim();
            if (!s.isBlank()) {
                return s;
            }
        }
        return defaultVal;
    }

    @Transactional
    public GenreRecommendResponse recommend(String projectId, GenreRecommendRequest req) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        ObjectNode body = objectMapper.valueToTree(req);
        body.put("projectId", projectId);

        JsonNode contract;
        try {
            contract = writerEngineClient.postGenreRecommend(body);
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "writer-python genre/recommend HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                    e
            );
        }

        GenreRecommendResponse saved = materializeGenre(projectId, contract, "preference", null);
        maybeAutoSelectGenre(projectId, saved.contractId());
        return saved;
    }

    /**
     * 同步阶段校验（须在返回 {@link SseEmitter} 之前调用），否则异步阶段无法再返回 404。
     */
    public void requireProjectForGenreStream(String projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /**
     * SSE：透传 Writer（须在控制器返回 emitter 之后的线程中调用，否则会缓冲到整段结束才写出）。
     */
    public void recommendStream(String projectId, GenreRecommendRequest req, SseEmitter emitter) {
        ObjectNode body = objectMapper.valueToTree(req);
        body.put("projectId", projectId);

        try {
            String json = objectMapper.writeValueAsString(body);
            writerSseProxyService.proxySsePost("/api/writer/genre/recommend/stream", json, emitter, (kind, data) -> {
                if ("GenreDecisionContract".equals(kind)) {
                    try {
                        GenreRecommendResponse saved = persistGenreContract(projectId, data, "preference", null);
                        maybeAutoSelectGenre(projectId, saved.contractId());
                        ObjectNode p = objectMapper.createObjectNode();
                        p.put("contractId", saved.contractId());
                        p.set("contract", saved.contract());
                        emitter.send(SseEmitter.event().name("persisted").data(objectMapper.writeValueAsString(p)));
                    } catch (Exception ex) {
                        try {
                            ObjectNode err = objectMapper.createObjectNode();
                            err.put("message", ex.getMessage());
                            emitter.send(SseEmitter.event().name("persist_error").data(objectMapper.writeValueAsString(err)));
                        } catch (Exception ignored) {
                            // ignore
                        }
                    }
                }
            });
        } catch (JsonProcessingException e) {
            try {
                ObjectNode err = objectMapper.createObjectNode();
                err.put("message", e.getOriginalMessage() != null ? e.getOriginalMessage() : e.getMessage());
                emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(err)));
            } catch (Exception ignored) {
                // ignore
            }
            emitter.complete();
        }
    }

    /**
     * 基于一两句故事线走同一套 Writer 题材流水线（请求体中带 storyHook）。
     */
    public void recommendFromStoryHookStream(String projectId, GenreStoryHookStreamRequest req, SseEmitter emitter) {
        String hook = req.storyHook().trim();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("projectId", projectId);
        body.put("targetPlatform", req.targetPlatform());
        body.put("genderChannel", req.genderChannel());
        body.put("riskPreference", req.riskPreference());
        body.put("storyHook", hook);
        body.putArray("preferredGenres");
        body.putArray("avoid");
        body.putArray("writingStrength");
        if (Boolean.TRUE.equals(req.uniqueDirection())) {
            body.put("uniqueDirection", true);
        }

        try {
            String json = objectMapper.writeValueAsString(body);
            writerSseProxyService.proxySsePost("/api/writer/genre/recommend/stream", json, emitter, (kind, data) -> {
                if ("GenreDecisionContract".equals(kind)) {
                    try {
                        GenreRecommendResponse saved = persistGenreContract(
                                projectId,
                                data,
                                Boolean.TRUE.equals(req.uniqueDirection()) ? "skill_unique" : "story_hook",
                                hook
                        );
                        maybeAutoSelectGenre(projectId, saved.contractId());
                        ObjectNode p = objectMapper.createObjectNode();
                        p.put("contractId", saved.contractId());
                        p.set("contract", saved.contract());
                        emitter.send(SseEmitter.event().name("persisted").data(objectMapper.writeValueAsString(p)));
                    } catch (Exception ex) {
                        try {
                            ObjectNode err = objectMapper.createObjectNode();
                            err.put("message", ex.getMessage());
                            emitter.send(SseEmitter.event().name("persist_error").data(objectMapper.writeValueAsString(err)));
                        } catch (Exception ignored) {
                            // ignore
                        }
                    }
                }
            });
        } catch (JsonProcessingException e) {
            try {
                ObjectNode err = objectMapper.createObjectNode();
                err.put("message", e.getOriginalMessage() != null ? e.getOriginalMessage() : e.getMessage());
                emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(err)));
            } catch (Exception ignored) {
                // ignore
            }
            emitter.complete();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GenreRecommendResponse persistGenreContract(String projectId, JsonNode contract, String source, String storyHookText) {
        return materializeGenre(projectId, contract, source, storyHookText);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void maybeAutoSelectGenre(String projectId, String contractId) {
        projectRepository.findById(projectId).ifPresent(p -> {
            if (p.getSelectedGenreContractId() == null || p.getSelectedGenreContractId().isBlank()) {
                p.setSelectedGenreContractId(contractId);
                projectRepository.save(p);
            }
        });
    }

    private GenreRecommendResponse materializeGenre(String projectId, JsonNode contract, String source, String storyHookText) {
        JsonNode selected = pick(contract, "selectedDirection", "selected_direction");
        JsonNode rankings = pick(contract, "candidateRankings", "candidate_rankings");
        JsonNode risks = optionalArray(contract, "riskNotes", "risk_notes");

        if (selected == null || selected.isNull() || selected.isMissingNode()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Writer contract missing selectedDirection / selected_direction"
            );
        }
        if (rankings == null || rankings.isNull() || rankings.isMissingNode() || !rankings.isArray()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Writer contract missing candidateRankings / candidate_rankings array"
            );
        }

        GenreDecisionContract row = new GenreDecisionContract();
        row.setId(UUID.randomUUID().toString().replace("-", ""));
        row.setProjectId(projectId);
        row.setSelectedDirection(selected);
        row.setCandidateRankings(rankings);
        row.setRiskNotes(risks);
        row.setRawJson(contract);
        row.setSource(source != null && !source.isBlank() ? source : "preference");
        row.setStoryHookText(storyHookText);

        genreDecisionContractRepository.save(row);

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.set("selectedDirection", selected);
        normalized.set("candidateRankings", rankings);
        normalized.set("riskNotes", risks);
        normalized.set(
                "recommendedCoreHook",
                pickText(contract, "recommendedCoreHook", "recommended_core_hook")
        );
        return new GenreRecommendResponse(row.getId(), normalized);
    }

    private JsonNode pick(JsonNode root, String camel, String snake) {
        if (root != null && root.has(camel)) {
            JsonNode n = root.get(camel);
            if (!n.isNull()) {
                return n;
            }
        }
        if (root != null && root.has(snake)) {
            JsonNode n = root.get(snake);
            if (!n.isNull()) {
                return n;
            }
        }
        return null;
    }

    private JsonNode optionalArray(JsonNode root, String camel, String snake) {
        JsonNode n = pick(root, camel, snake);
        if (n == null || n.isMissingNode() || n.isNull()) {
            return objectMapper.createArrayNode();
        }
        return n;
    }

    private JsonNode pickText(JsonNode root, String camel, String snake) {
        JsonNode n = pick(root, camel, snake);
        if (n == null || n.isMissingNode() || n.isNull()) {
            return objectMapper.nullNode();
        }
        return n;
    }
}
