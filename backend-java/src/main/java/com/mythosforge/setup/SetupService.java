package com.mythosforge.setup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.chapter.ChapterCommitRepository;
import com.mythosforge.chapter.ChapterVersionRepository;
import com.mythosforge.chapter.SubtextLedgerService;
import com.mythosforge.chapter.dto.SubtextLedgerCreateRequest;
import com.mythosforge.genre.GenreDecisionContract;
import com.mythosforge.genre.GenreDecisionContractRepository;
import com.mythosforge.genre.GenreService;
import com.mythosforge.genre.dto.GenreInterviewRequest;
import com.mythosforge.genre.dto.GenreInterviewResponse;
import com.mythosforge.genre.dto.GenreRecommendResponse;
import com.mythosforge.setup.dto.SetupGenreProposeRequest;
import com.mythosforge.setup.dto.SetupStoryProposeRequest;
import com.mythosforge.narrative.NarrativeBootstrapService;
import com.mythosforge.narrative.NarrativeDomainBridgeService;
import com.mythosforge.narrative.NarrativeStorylineRepository;
import com.mythosforge.narrative.NarrativeStructureService;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.setup.dto.SetupApplyRequest;
import com.mythosforge.setup.dto.SetupProposalResponse;
import com.mythosforge.setup.dto.SetupReviseRequest;
import com.mythosforge.setup.dto.SetupStatusResponse;
import com.mythosforge.story.StoryContractEntity;
import com.mythosforge.story.StoryContractRepository;
import com.mythosforge.story.StoryService;
import com.mythosforge.story.dto.StoryInitResponse;
import com.mythosforge.writer.WriterEngineClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SetupService {

    private final ProjectRepository projectRepository;
    private final SetupProposalRepository proposalRepository;
    private final GenreDecisionContractRepository genreRepository;
    private final StoryContractRepository storyContractRepository;
    private final NarrativeStorylineRepository storylineRepository;
    private final GenreService genreService;
    private final StoryService storyService;
    private final NarrativeBootstrapService narrativeBootstrapService;
    private final NarrativeDomainBridgeService narrativeDomainBridgeService;
    private final NarrativeStructureService narrativeStructureService;
    private final SubtextLedgerService subtextLedgerService;
    private final ChapterVersionRepository chapterVersionRepository;
    private final ChapterCommitRepository chapterCommitRepository;
    private final WriterEngineClient writerEngineClient;
    private final ObjectMapper objectMapper;

    public SetupService(
            ProjectRepository projectRepository,
            SetupProposalRepository proposalRepository,
            GenreDecisionContractRepository genreRepository,
            StoryContractRepository storyContractRepository,
            NarrativeStorylineRepository storylineRepository,
            GenreService genreService,
            StoryService storyService,
            NarrativeBootstrapService narrativeBootstrapService,
            NarrativeDomainBridgeService narrativeDomainBridgeService,
            NarrativeStructureService narrativeStructureService,
            SubtextLedgerService subtextLedgerService,
            ChapterVersionRepository chapterVersionRepository,
            ChapterCommitRepository chapterCommitRepository,
            WriterEngineClient writerEngineClient,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.proposalRepository = proposalRepository;
        this.genreRepository = genreRepository;
        this.storyContractRepository = storyContractRepository;
        this.storylineRepository = storylineRepository;
        this.genreService = genreService;
        this.storyService = storyService;
        this.narrativeBootstrapService = narrativeBootstrapService;
        this.narrativeDomainBridgeService = narrativeDomainBridgeService;
        this.narrativeStructureService = narrativeStructureService;
        this.subtextLedgerService = subtextLedgerService;
        this.chapterVersionRepository = chapterVersionRepository;
        this.chapterCommitRepository = chapterCommitRepository;
        this.writerEngineClient = writerEngineClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SetupStatusResponse getStatus(String projectId) {
        Project p = requireProject(projectId);
        boolean genreOk = hasText(p.getSelectedGenreContractId());
        boolean storyOk = hasText(p.getSelectedStoryContractId());
        int slCount = storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId).size();
        boolean narrativeOk = slCount > 0;
        boolean ready = genreOk && storyOk && narrativeOk;

        long draftVersions = chapterVersionRepository.countByProjectId(projectId);
        long acceptedCount = chapterCommitRepository.countByProjectIdAndStatus(projectId, "accepted");
        int currentCh = p.getCurrentChapter() != null ? p.getCurrentChapter() : 0;
        int maxAccepted = chapterCommitRepository.findMaxChapterNoByProjectIdAndStatus(projectId, "accepted");
        boolean writingStarted = draftVersions > 0 || acceptedCount > 0 || currentCh > 0;
        boolean setupLocked = writingStarted && ready;
        int resumeChapter = currentCh > 0 ? currentCh : (maxAccepted > 0 ? maxAccepted + 1 : 1);

        String current = !genreOk ? "genre" : !storyOk ? "story" : !narrativeOk ? "narrative" : "ready";
        String hint = switch (current) {
            case "genre" -> "请先生成并确认题材方案";
            case "story" -> "请生成并确认故事契约与第一卷大纲";
            case "narrative" -> "请生成并确认故事线、汇合与伏笔";
            default -> setupLocked
                    ? "创作进行中：Setup 仅可查看已定设定，请在工作台继续写章"
                    : "可以开始写章";
        };

        return new SetupStatusResponse(
                p.getSetupMode() != null ? p.getSetupMode() : "standard",
                current,
                genreOk,
                storyOk,
                narrativeOk,
                ready,
                hint,
                pendingId(projectId, "genre"),
                pendingId(projectId, "story"),
                pendingId(projectId, "narrative"),
                genrePreview(p.getSelectedGenreContractId()),
                storyPreview(p.getSelectedStoryContractId()),
                narrativeOk ? narrativeStructureService.exportDomainFromPg(projectId) : null,
                slCount,
                writingStarted,
                setupLocked,
                (int) acceptedCount,
                (int) draftVersions,
                resumeChapter
        );
    }

    private void assertSetupNotLocked(String projectId) {
        Project p = requireProject(projectId);
        boolean genreOk = hasText(p.getSelectedGenreContractId());
        boolean storyOk = hasText(p.getSelectedStoryContractId());
        int slCount = storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId).size();
        boolean ready = genreOk && storyOk && slCount > 0;
        long draftVersions = chapterVersionRepository.countByProjectId(projectId);
        long acceptedCount = chapterCommitRepository.countByProjectIdAndStatus(projectId, "accepted");
        int currentCh = p.getCurrentChapter() != null ? p.getCurrentChapter() : 0;
        boolean writingStarted = draftVersions > 0 || acceptedCount > 0 || currentCh > 0;
        if (writingStarted && ready) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "创作已开始，不可重新初始化题材或故事契约。请在工作台继续写作；微调故事线请用项目主页「故事结构」。"
            );
        }
    }

    @Transactional
    public void setSetupMode(String projectId, String mode) {
        Project p = requireProject(projectId);
        if (mode != null && !mode.isBlank()) {
            String m = mode.trim().toLowerCase();
            if (!m.equals("standard") && !m.equals("skill")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "setupMode 须为 standard 或 skill");
            }
            p.setSetupMode(m);
        }
        projectRepository.save(p);
    }

    /**
     * 按当前 Setup 进度顺序生成草案（均需人工确认采纳，不会自动 apply）。
     * 未确认题材 → 仅题材；已确认题材 → 故事（+ 若已有故事契约则同时结构）。
     */
    @Transactional
    public Map<String, String> proposeAll(String projectId, SetupGenreProposeRequest genreReq) {
        assertSetupNotLocked(projectId);
        Project p = requireProject(projectId);
        Map<String, String> out = new LinkedHashMap<>();
        if (!hasText(p.getSelectedGenreContractId())) {
            SetupGenreProposeRequest req = genreReq != null
                    ? genreReq
                    : new SetupGenreProposeRequest("番茄", "男频", null, null, null, "medium", null, false);
            SetupProposalResponse g = proposeGenre(projectId, req);
            out.put("genreProposalId", g.id());
            out.put("message", "已生成题材草案；确认采纳后可再次调用以生成故事与结构草案。");
            return out;
        }
        SetupProposalResponse story = proposeStory(projectId, new SetupStoryProposeRequest(null));
        out.put("storyProposalId", story.id());
        if (hasText(p.getSelectedStoryContractId())) {
            SetupProposalResponse narrative = proposeNarrative(projectId, false);
            out.put("narrativeProposalId", narrative.id());
            out.put(
                    "message",
                    "已生成故事与结构草案；请分别在向导中确认采纳（故事含 init-novel 章契约，结构为规则/LLM 故事线）。"
            );
        } else {
            out.put("message", "已生成故事草案（含第一卷章契约）；确认采纳后可再次调用以生成结构草案。");
        }
        return out;
    }

    @Transactional
    public SetupProposalResponse proposeGenre(String projectId, SetupGenreProposeRequest req) {
        assertSetupNotLocked(projectId);
        requireProject(projectId);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("targetPlatform", req.targetPlatform() != null ? req.targetPlatform() : "番茄");
        body.put("genderChannel", req.genderChannel() != null ? req.genderChannel() : "男频");
        body.set("preferredGenres", objectMapper.valueToTree(req.preferredGenres() != null ? req.preferredGenres() : List.of()));
        body.set("avoid", objectMapper.valueToTree(req.avoid() != null ? req.avoid() : List.of()));
        body.set("writingStrength", objectMapper.valueToTree(req.writingStrength() != null ? req.writingStrength() : List.of()));
        body.put("riskPreference", req.riskPreference() != null ? req.riskPreference() : "medium");
        body.put("projectId", projectId);
        if (req.storyHook() != null && !req.storyHook().isBlank()) {
            body.put("storyHook", req.storyHook().trim());
        }
        if (Boolean.TRUE.equals(req.uniqueDirection())) {
            body.put("uniqueDirection", true);
        }
        JsonNode contract;
        try {
            contract = writerEngineClient.postGenreRecommend(body);
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "writer genre/recommend: " + e.getResponseBodyAsString(),
                    e
            );
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("contract", contract);
        payload.put("source", Boolean.TRUE.equals(req.uniqueDirection()) ? "skill_unique" : "preference");
        if (req.storyHook() != null) {
            payload.put("storyHookText", req.storyHook());
        }
        return saveProposal(projectId, "genre", payload, "已生成题材草案，请确认后采纳。");
    }

    @Transactional
    public SetupProposalResponse reviseGenre(String projectId, SetupReviseRequest req) {
        assertSetupNotLocked(projectId);
        SetupProposalEntity prev = proposalRepository
                .findFirstByProjectIdAndStageAndStatusOrderByCreatedAtDesc(projectId, "genre", "pending")
                .orElse(null);
        SetupGenreProposeRequest base = new SetupGenreProposeRequest(
                "番茄", "男频", null, null, null, "medium",
                req.feedback(),
                false
        );
        if (prev != null && prev.getPayloadJson().has("contract")) {
            JsonNode c = prev.getPayloadJson().get("contract");
            JsonNode sel = c.path("selectedDirection");
            if (!sel.isMissingNode()) {
                base = new SetupGenreProposeRequest(
                        "番茄",
                        sel.path("channel").asText("男频"),
                        null,
                        null,
                        null,
                        "medium",
                        req.feedback(),
                        false
                );
            }
            prev.setStatus("discarded");
            proposalRepository.save(prev);
        }
        SetupProposalResponse proposal = proposeGenre(projectId, base);
        SetupProposalEntity saved = proposalRepository.findById(proposal.id()).orElseThrow();
        saved.setAssistantReply("已根据您的意见调整题材草案：\n" + req.feedback());
        proposalRepository.save(saved);
        return toResponse(saved, saved.getPayloadJson());
    }

    @Transactional
    public SetupProposalResponse reviseStory(String projectId, SetupReviseRequest req) {
        assertSetupNotLocked(projectId);
        SetupProposalEntity prev = proposalRepository
                .findFirstByProjectIdAndStageAndStatusOrderByCreatedAtDesc(projectId, "story", "pending")
                .orElse(null);
        if (prev != null) {
            prev.setStatus("discarded");
            proposalRepository.save(prev);
        }
        String notes = req.feedback();
        if (prev != null && prev.getPayloadJson().has("initBundle")) {
            notes = req.feedback() + "\n\n[上一版草案摘要]\n" + prev.getPayloadJson().get("initBundle").toString().substring(0, Math.min(2000, prev.getPayloadJson().get("initBundle").toString().length()));
        }
        return proposeStory(projectId, new SetupStoryProposeRequest(notes));
    }

    @Transactional
    public SetupProposalResponse proposeGenreFromInterview(String projectId, GenreInterviewResponse interview) {
        assertSetupNotLocked(projectId);
        requireProject(projectId);
        if (!"complete".equals(interview.status()) || interview.coreSettings() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采访尚未完成");
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("interviewFinalSummary", interview.finalSummary());
        payload.set("coreSettings", interview.coreSettings());
        payload.put("source", "story_hook");
        return saveProposal(projectId, "genre", payload, interview.replyToUser());
    }

    @Transactional
    public SetupProposalResponse applyGenre(String projectId, SetupApplyRequest body) {
        assertSetupNotLocked(projectId);
        requireProject(projectId);
        SetupProposalEntity prop = loadPendingProposal(projectId, "genre", body.proposalId());
        JsonNode payload = prop.getPayloadJson();
        GenreRecommendResponse saved;
        if (payload.has("contract")) {
            String source = payload.path("source").asText("preference");
            String hook = payload.path("storyHookText").asText(null);
            saved = genreService.persistGenreContract(projectId, payload.get("contract"), source, hook);
        } else if (payload.has("coreSettings")) {
            ObjectNode recommendBody = objectMapper.createObjectNode();
            recommendBody.put("targetPlatform", "自定义");
            recommendBody.put("genderChannel", "男频");
            recommendBody.put("uniqueDirection", true);
            recommendBody.put("storyHook", payload.path("interviewFinalSummary").asText(""));
            recommendBody.put("projectId", projectId);
            JsonNode contract = writerEngineClient.postGenreRecommend(recommendBody);
            saved = genreService.persistGenreContract(projectId, contract, "story_hook", payload.path("interviewFinalSummary").asText(null));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的题材提案");
        }
        genreService.maybeAutoSelectGenre(projectId, saved.contractId());
        markApplied(prop);
        ObjectNode applied = objectMapper.createObjectNode();
        applied.put("contractId", saved.contractId());
        applied.set("contract", saved.contract());
        return toResponse(prop, applied);
    }

    @Transactional
    public SetupProposalResponse proposeStory(String projectId, SetupStoryProposeRequest req) {
        assertSetupNotLocked(projectId);
        String wizardNotes = req != null ? req.wizardNotes() : null;
        Project project = requireProject(projectId);
        String gid = project.getSelectedGenreContractId();
        if (!hasText(gid)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先确认题材方案");
        }
        GenreDecisionContract genre = genreRepository.findById(gid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "题材方案不存在"));
        ObjectNode body = objectMapper.createObjectNode();
        body.put("projectId", projectId);
        body.set("genreDecision", genre.getRawJson());
        if (project.getFanSeriesPreset() != null && !project.getFanSeriesPreset().isBlank()) {
            body.put("fanSeriesPreset", project.getFanSeriesPreset());
        }
        if (wizardNotes != null && !wizardNotes.isBlank()) {
            body.put("wizardNotes", wizardNotes.trim());
        }
        JsonNode root;
        try {
            root = writerEngineClient.postInitNovel(body);
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "writer init-novel: " + e.getResponseBodyAsString(), e);
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("initBundle", root);
        return saveProposal(projectId, "story", payload, "已生成故事契约与第一卷大纲草案，请确认后采纳。");
    }

    @Transactional
    public SetupProposalResponse applyStory(String projectId, SetupApplyRequest body) {
        assertSetupNotLocked(projectId);
        requireProject(projectId);
        SetupProposalEntity prop = loadPendingProposal(projectId, "story", body.proposalId());
        JsonNode bundle = prop.getPayloadJson().get("initBundle");
        if (bundle == null || bundle.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的故事提案");
        }
        StoryInitResponse saved = storyService.persistInitFromWriterRoot(projectId, bundle);
        storyService.selectStoryBundleForProject(projectId, saved.storyContractId());
        markApplied(prop);
        ObjectNode applied = objectMapper.createObjectNode();
        applied.put("storyContractId", saved.storyContractId());
        applied.put("novelSeedContractId", saved.novelSeedContractId());
        applied.put("firstVolumeOutline", saved.firstVolumeOutline());
        return toResponse(prop, applied);
    }

    @Transactional
    public SetupProposalResponse proposeNarrative(String projectId, boolean useLlm) {
        assertSetupNotLocked(projectId);
        Project p = requireProject(projectId);
        StoryContractEntity story = loadSelectedStory(p);
        GenreDecisionContract genre = loadSelectedGenre(p);
        int target = p.getTargetChapters() != null && p.getTargetChapters() > 0 ? p.getTargetChapters() : 100;
        String hook = genre != null ? genre.getStoryHookText() : null;

        ObjectNode domain;
        if (useLlm) {
            ObjectNode req = objectMapper.createObjectNode();
            req.put("projectId", projectId);
            req.set("genreDecision", genre != null ? genre.getRawJson() : objectMapper.createObjectNode());
            req.set("storyContract", story.getRawJson());
            req.put("firstVolumeOutline", story.getFirstVolumeOutline() != null ? story.getFirstVolumeOutline() : "");
            req.put("targetChapters", target);
            if (p.getFanSeriesPreset() != null) {
                req.put("writerSkillId", p.getFanSeriesPreset());
            }
            try {
                domain = (ObjectNode) writerEngineClient.postNarrativeSetupPropose(req);
            } catch (RestClientResponseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "writer narrative propose: " + e.getResponseBodyAsString(), e);
            }
        } else {
            domain = narrativeBootstrapService.buildNarrativeProposalDomain(
                    story.getRawJson(),
                    hook,
                    story.getFirstVolumeOutline(),
                    target,
                    p.getName()
            );
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("narrativeDomain", domain);
        payload.put("generator", useLlm ? "llm" : "rules");
        return saveProposal(projectId, "narrative", payload, "已生成故事结构草案，请确认后采纳。");
    }

    @Transactional
    public SetupProposalResponse reviseNarrative(String projectId, SetupReviseRequest req) {
        assertSetupNotLocked(projectId);
        Project p = requireProject(projectId);
        StoryContractEntity story = loadSelectedStory(p);
        GenreDecisionContract genre = loadSelectedGenre(p);
        SetupProposalEntity prev = proposalRepository
                .findFirstByProjectIdAndStageAndStatusOrderByCreatedAtDesc(projectId, "narrative", "pending")
                .orElse(null);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("projectId", projectId);
        body.set("genreDecision", genre != null ? genre.getRawJson() : objectMapper.createObjectNode());
        body.set("storyContract", story.getRawJson());
        body.put("firstVolumeOutline", story.getFirstVolumeOutline() != null ? story.getFirstVolumeOutline() : "");
        body.put("targetChapters", p.getTargetChapters() != null ? p.getTargetChapters() : 100);
        body.put("userFeedback", req.feedback());
        if (prev != null && prev.getPayloadJson().has("narrativeDomain")) {
            body.set("previousProposal", prev.getPayloadJson().get("narrativeDomain"));
        }
        if (req.writerSkillId() != null && !req.writerSkillId().isBlank()) {
            body.put("writerSkillId", req.writerSkillId());
        } else if (p.getFanSeriesPreset() != null) {
            body.put("writerSkillId", p.getFanSeriesPreset());
        }
        JsonNode resp;
        try {
            resp = writerEngineClient.postNarrativeSetupRevise(body);
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "writer narrative revise: " + e.getResponseBodyAsString(), e);
        }
        if (prev != null) {
            prev.setStatus("discarded");
            proposalRepository.save(prev);
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("narrativeDomain", resp.get("narrativeDomain"));
        String reply = resp.path("assistantReply").asText("已根据您的意见修订故事结构草案。");
        return saveProposal(projectId, "narrative", payload, reply);
    }

    @Transactional
    public SetupProposalResponse applyNarrative(String projectId, SetupApplyRequest body) {
        assertSetupNotLocked(projectId);
        requireProject(projectId);
        SetupProposalEntity prop = loadPendingProposal(projectId, "narrative", body.proposalId());
        JsonNode domain = prop.getPayloadJson().get("narrativeDomain");
        if (domain == null || !domain.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的故事结构提案");
        }
        boolean replace = body.replaceExisting() == null || Boolean.TRUE.equals(body.replaceExisting());
        if (replace && !storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId).isEmpty()) {
            narrativeStructureService.clearProjectNarrativeStructure(projectId);
        }
        narrativeDomainBridgeService.importFromDomainJson(projectId, domain);
        applySubtextSeeds(projectId, domain);
        markApplied(prop);
        ObjectNode applied = narrativeStructureService.exportDomainFromPg(projectId);
        return toResponse(prop, applied);
    }

    @Transactional
    public SetupProposalResponse discardProposal(String projectId, String proposalId) {
        SetupProposalEntity prop = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!projectId.equals(prop.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        prop.setStatus("discarded");
        proposalRepository.save(prop);
        return toResponse(prop, prop.getPayloadJson());
    }

    @Transactional(readOnly = true)
    public SetupProposalResponse getProposal(String projectId, String proposalId) {
        SetupProposalEntity prop = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!projectId.equals(prop.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return toResponse(prop, prop.getPayloadJson());
    }

    private void applySubtextSeeds(String projectId, JsonNode domain) {
        JsonNode seeds = domain.get("subtextSeeds");
        if (seeds == null || !seeds.isArray()) {
            return;
        }
        for (JsonNode s : seeds) {
            if (!s.isObject()) {
                continue;
            }
            int ch = s.path("chapterNo").asInt(1);
            String q = s.path("question").asText("").trim();
            if (q.isBlank()) {
                continue;
            }
            Integer sug = s.has("suggestedResolveChapter") && !s.get("suggestedResolveChapter").isNull()
                    ? s.get("suggestedResolveChapter").asInt()
                    : null;
            subtextLedgerService.create(
                    projectId,
                    new SubtextLedgerCreateRequest(
                            ch,
                            s.path("characterRef").asText(null),
                            q,
                            sug,
                            s.path("importance").asText("medium")
                    )
            );
        }
    }

    private SetupProposalResponse saveProposal(String projectId, String stage, ObjectNode payload, String reply) {
        discardPending(projectId, stage);
        SetupProposalEntity e = new SetupProposalEntity();
        e.setId(UUID.randomUUID().toString().replace("-", ""));
        e.setProjectId(projectId);
        e.setStage(stage);
        e.setStatus("pending");
        e.setPayloadJson(payload);
        e.setAssistantReply(reply);
        e.setBaseVersion((int) proposalRepository.findByProjectIdAndStageOrderByCreatedAtDesc(projectId, stage).size());
        proposalRepository.save(e);
        return toResponse(e, payload);
    }

    private void discardPending(String projectId, String stage) {
        for (SetupProposalEntity p : proposalRepository.findByProjectIdAndStageOrderByCreatedAtDesc(projectId, stage)) {
            if ("pending".equals(p.getStatus())) {
                p.setStatus("discarded");
                proposalRepository.save(p);
            }
        }
    }

    private SetupProposalEntity loadPendingProposal(String projectId, String stage, String proposalId) {
        if (proposalId != null && !proposalId.isBlank()) {
            SetupProposalEntity p = proposalRepository.findById(proposalId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (!projectId.equals(p.getProjectId()) || !stage.equals(p.getStage())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            if (!"pending".equals(p.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "提案已处理");
            }
            return p;
        }
        return proposalRepository
                .findFirstByProjectIdAndStageAndStatusOrderByCreatedAtDesc(projectId, stage, "pending")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有待确认的" + stage + "提案"));
    }

    private void markApplied(SetupProposalEntity prop) {
        prop.setStatus("applied");
        proposalRepository.save(prop);
    }

    private String pendingId(String projectId, String stage) {
        return proposalRepository
                .findFirstByProjectIdAndStageAndStatusOrderByCreatedAtDesc(projectId, stage, "pending")
                .map(SetupProposalEntity::getId)
                .orElse(null);
    }

    private JsonNode genrePreview(String contractId) {
        if (!hasText(contractId)) {
            return null;
        }
        return genreRepository.findById(contractId).map(GenreDecisionContract::getRawJson).orElse(null);
    }

    private JsonNode storyPreview(String contractId) {
        if (!hasText(contractId)) {
            return null;
        }
        return storyContractRepository.findById(contractId).map(StoryContractEntity::getRawJson).orElse(null);
    }

    private StoryContractEntity loadSelectedStory(Project p) {
        String sid = p.getSelectedStoryContractId();
        if (!hasText(sid)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先确认故事契约");
        }
        StoryContractEntity story = storyContractRepository.findById(sid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "故事契约不存在"));
        if (!p.getId().equals(story.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "故事契约不属于该项目");
        }
        return story;
    }

    private GenreDecisionContract loadSelectedGenre(Project p) {
        String gid = p.getSelectedGenreContractId();
        if (!hasText(gid)) {
            return null;
        }
        return genreRepository.findById(gid).orElse(null);
    }

    private Project requireProject(String projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private SetupProposalResponse toResponse(SetupProposalEntity e, JsonNode payload) {
        return new SetupProposalResponse(
                e.getId(),
                e.getProjectId(),
                e.getStage(),
                e.getStatus(),
                payload != null ? payload : e.getPayloadJson(),
                e.getAssistantReply(),
                e.getBaseVersion(),
                e.getCreatedAt()
        );
    }
}
