package com.mythosforge.story;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.chapter.ChapterContractEntity;
import com.mythosforge.chapter.ChapterContractRepository;
import com.mythosforge.chapter.ChapterPrewritePlanRepository;
import com.mythosforge.genre.GenreDecisionContract;
import com.mythosforge.genre.GenreDecisionContractRepository;
import com.mythosforge.narrative.NarrativeBootstrapService;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.story.dto.StoryInitResponse;
import com.mythosforge.writer.WriterEngineClient;
import com.mythosforge.writer.WriterSseProxyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 初始化流水线：解析题材 → 调 Writer init-novel（阻塞或 SSE）→ 落库 novel_seed / story_contract / chapter_contracts（默认 20 章），
 * 并维护 {@link com.mythosforge.project.Project#setSelectedStoryContractId 当前选中快照}。
 */
@Service
public class StoryService {

    private final ProjectRepository projectRepository;
    private final GenreDecisionContractRepository genreDecisionContractRepository;
    private final NovelSeedContractRepository novelSeedContractRepository;
    private final StoryContractRepository storyContractRepository;
    private final ChapterContractRepository chapterContractRepository;
    private final ChapterPrewritePlanRepository chapterPrewritePlanRepository;
    private final WriterEngineClient writerEngineClient;
    private final WriterSseProxyService writerSseProxyService;
    private final NarrativeBootstrapService narrativeBootstrapService;
    private final ObjectMapper objectMapper;

    public StoryService(
            ProjectRepository projectRepository,
            GenreDecisionContractRepository genreDecisionContractRepository,
            NovelSeedContractRepository novelSeedContractRepository,
            StoryContractRepository storyContractRepository,
            ChapterContractRepository chapterContractRepository,
            ChapterPrewritePlanRepository chapterPrewritePlanRepository,
            WriterEngineClient writerEngineClient,
            WriterSseProxyService writerSseProxyService,
            NarrativeBootstrapService narrativeBootstrapService,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.genreDecisionContractRepository = genreDecisionContractRepository;
        this.novelSeedContractRepository = novelSeedContractRepository;
        this.storyContractRepository = storyContractRepository;
        this.chapterContractRepository = chapterContractRepository;
        this.chapterPrewritePlanRepository = chapterPrewritePlanRepository;
        this.writerEngineClient = writerEngineClient;
        this.writerSseProxyService = writerSseProxyService;
        this.narrativeBootstrapService = narrativeBootstrapService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void selectStoryBundleForProject(String projectId, String storyContractId) {
        selectStoryBundleOnProject(projectId, storyContractId);
    }

    private GenreDecisionContract resolveGenreForInit(Project project) {
        String projectId = project.getId();
        String sel = project.getSelectedGenreContractId();
        if (sel != null && !sel.isBlank()) {
            GenreDecisionContract g = genreDecisionContractRepository.findById(sel)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "当前选中的题材方案不存在，请在项目详情重新选择"
                    ));
            if (!projectId.equals(g.getProjectId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "题材方案不属于该项目");
            }
            return g;
        }
        return genreDecisionContractRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "尚未生成题材方案；请先在项目详情完成题材推荐或故事线生成"
                ));
    }

    private GenreDecisionContract resolveGenreForInit(String projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return resolveGenreForInit(project);
    }

    private static void appendFanSeriesPreset(ObjectNode body, Project project) {
        String fp = project.getFanSeriesPreset();
        if (fp != null && !fp.isBlank()) {
            body.put("fanSeriesPreset", fp.trim());
        }
    }

    private static void appendWizardNotes(ObjectNode body, String wizardNotes) {
        if (wizardNotes != null && !wizardNotes.isBlank()) {
            body.put("wizardNotes", wizardNotes.trim());
        }
    }

    @Transactional
    public StoryInitResponse init(String projectId, String wizardNotes) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        GenreDecisionContract genre = resolveGenreForInit(project);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("projectId", projectId);
        body.set("genreDecision", genre.getRawJson());
        appendFanSeriesPreset(body, project);
        appendWizardNotes(body, wizardNotes);

        JsonNode root;
        try {
            root = writerEngineClient.postInitNovel(body);
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "writer-python init-novel HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                    e
            );
        }

        StoryInitResponse saved = materializeInit(projectId, root);
        selectStoryBundleOnProject(projectId, saved.storyContractId());
        return saved;
    }

    /** 须在返回 {@link SseEmitter} 之前同步调用。 */
    public void requireProjectAndGenreForInitStream(String projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        resolveGenreForInit(projectId);
    }

    public void initStream(String projectId, String wizardNotes, SseEmitter emitter) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        GenreDecisionContract genre = resolveGenreForInit(project);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("projectId", projectId);
        body.set("genreDecision", genre.getRawJson());
        appendFanSeriesPreset(body, project);
        appendWizardNotes(body, wizardNotes);

        try {
            String json = objectMapper.writeValueAsString(body);
            writerSseProxyService.proxySsePost("/api/writer/init-novel/stream", json, emitter, (kind, data) -> {
                if ("InitNovelBundle".equals(kind)) {
                    try {
                        StoryInitResponse saved = persistInitFromWriterRoot(projectId, data);
                        selectStoryBundleOnProject(projectId, saved.storyContractId());
                        ObjectNode p = objectMapper.createObjectNode();
                        p.put("novelSeedContractId", saved.novelSeedContractId());
                        p.put("storyContractId", saved.storyContractId());
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
    public StoryInitResponse persistInitFromWriterRoot(String projectId, JsonNode root) {
        return materializeInit(projectId, root);
    }

    @Transactional(readOnly = true)
    public Optional<StoryInitResponse> loadSelectedStoryBundle(String projectId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String sid = p.getSelectedStoryContractId();
        if (sid == null || sid.isBlank()) {
            return Optional.empty();
        }
        StoryContractEntity story = storyContractRepository.findById(sid).orElse(null);
        if (story == null || !projectId.equals(story.getProjectId())) {
            return Optional.empty();
        }
        String seedId = story.getNovelSeedContractId();
        NovelSeedContract seedRow;
        if (seedId != null && !seedId.isBlank()) {
            seedRow = novelSeedContractRepository.findById(seedId).orElse(null);
        } else {
            seedRow = novelSeedContractRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId).orElse(null);
        }
        if (seedRow == null) {
            return Optional.empty();
        }
        List<ChapterContractEntity> chapters = chapterContractRepository.findByStoryContractIdOrderByChapterNoAsc(sid);
        ArrayNode chapterArray = objectMapper.createArrayNode();
        for (ChapterContractEntity ch : chapters) {
            chapterArray.add(ch.getRawJson());
        }
        String outline = story.getFirstVolumeOutline() != null ? story.getFirstVolumeOutline() : "";
        return Optional.of(new StoryInitResponse(
                seedRow.getId(),
                story.getId(),
                seedRow.getRawJson(),
                story.getRawJson(),
                outline,
                chapterArray,
                story.getAuthorIntent(),
                story.getNonNegotiables()
        ));
    }

    private void selectStoryBundleOnProject(String projectId, String storyContractId) {
        projectRepository.findById(projectId).ifPresent(p -> {
            p.setSelectedStoryContractId(storyContractId);
            projectRepository.save(p);
        });
    }

    private StoryInitResponse materializeInit(String projectId, JsonNode root) {
        JsonNode novelSeed = root.get("novelSeed");
        JsonNode storyContract = root.get("storyContract");
        if (novelSeed == null || novelSeed.isNull() || novelSeed.isMissingNode()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Writer response missing novelSeed");
        }
        if (storyContract == null || storyContract.isNull() || storyContract.isMissingNode()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Writer response missing storyContract");
        }

        JsonNode chapterContractsNode = root.get("chapterContracts");
        if (chapterContractsNode == null || chapterContractsNode.isNull()) {
            chapterContractsNode = objectMapper.createArrayNode();
        }
        if (!chapterContractsNode.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Writer response chapterContracts must be an array");
        }
        JsonNode firstVolNode = root.get("firstVolumeOutline");
        String firstVolumeOutline = "";
        if (firstVolNode != null && !firstVolNode.isNull() && firstVolNode.isTextual()) {
            firstVolumeOutline = firstVolNode.asText();
        }

        String novelSeedId = UUID.randomUUID().toString().replace("-", "");
        String storyContractId = UUID.randomUUID().toString().replace("-", "");

        NovelSeedContract seedRow = new NovelSeedContract();
        seedRow.setId(novelSeedId);
        seedRow.setProjectId(projectId);
        seedRow.setRawJson(novelSeed);
        novelSeedContractRepository.save(seedRow);

        StoryContractEntity storyRow = new StoryContractEntity();
        storyRow.setId(storyContractId);
        storyRow.setProjectId(projectId);
        storyRow.setNovelSeedContractId(novelSeedId);
        storyRow.setVersion(1);
        storyRow.setRawJson(storyContract);
        storyRow.setFirstVolumeOutline(firstVolumeOutline);
        storyContractRepository.save(storyRow);

        List<ChapterContractEntity> chapterRows = new ArrayList<>();
        Set<Integer> seenChapterNos = new HashSet<>();
        for (JsonNode ch : chapterContractsNode) {
            if (ch == null || !ch.isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid chapter contract entry in array");
            }
            JsonNode noNode = ch.get("chapterNo");
            if (noNode == null || !noNode.isNumber()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Chapter contract missing chapterNo");
            }
            int chapterNo = noNode.asInt();
            if (!seenChapterNos.add(chapterNo)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Duplicate chapterNo in chapterContracts");
            }
            String titleHint = "";
            JsonNode hintNode = ch.get("titleHint");
            if (hintNode != null && hintNode.isTextual()) {
                titleHint = hintNode.asText();
            }
            if (titleHint.length() > 255) {
                titleHint = titleHint.substring(0, 255);
            }
            ChapterContractEntity row = new ChapterContractEntity();
            row.setId(UUID.randomUUID().toString().replace("-", ""));
            row.setProjectId(projectId);
            row.setStoryContractId(storyContractId);
            row.setChapterNo(chapterNo);
            row.setTitleHint(titleHint);
            row.setRawJson(ch);
            chapterRows.add(row);
        }
        chapterContractRepository.saveAll(chapterRows);

        Project project = projectRepository.findById(projectId).orElse(null);
        GenreDecisionContract genre = null;
        if (project != null && project.getSelectedGenreContractId() != null) {
            genre = genreDecisionContractRepository.findById(project.getSelectedGenreContractId()).orElse(null);
        }
        narrativeBootstrapService.bootstrapAfterStoryInit(projectId, storyContract, genre, firstVolumeOutline);

        return new StoryInitResponse(
                novelSeedId,
                storyContractId,
                novelSeed,
                storyContract,
                firstVolumeOutline,
                chapterContractsNode,
                null,
                null
        );
    }

    /** 更新当前选中快照的作者治理字段（注入 Writer story_canon）。 */
    @Transactional
    public void updateSelectedGovernance(String projectId, String authorIntent, JsonNode nonNegotiables, String styleGuideMd) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String sid = p.getSelectedStoryContractId();
        if (sid == null || sid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目未选定初始化快照");
        }
        StoryContractEntity story = storyContractRepository.findById(sid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "快照不存在"));
        if (!projectId.equals(story.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "快照不属于该项目");
        }
        story.setAuthorIntent(authorIntent != null ? authorIntent : "");
        story.setNonNegotiables(nonNegotiables != null && !nonNegotiables.isNull() ? nonNegotiables : objectMapper.createArrayNode());
        if (styleGuideMd != null) {
            JsonNode raw = story.getRawJson();
            ObjectNode root =
                    raw != null && raw.isObject()
                            ? (ObjectNode) raw.deepCopy()
                            : objectMapper.createObjectNode();
            ObjectNode fp =
                    root.has("styleFingerprint") && root.get("styleFingerprint").isObject()
                            ? (ObjectNode) root.get("styleFingerprint").deepCopy()
                            : objectMapper.createObjectNode();
            fp.put("styleGuideMd", styleGuideMd);
            root.set("styleFingerprint", fp);
            story.setRawJson(root);
        }
        storyContractRepository.save(story);
    }

    /** 在现有作者意图后追加一行（全书用语/风格类长期约束），不改动 nonNegotiables。 */
    @Transactional
    public void appendSelectedAuthorIntentLine(String projectId, String line) {
        String l = line != null ? line.trim() : "";
        if (l.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "追加内容不能为空");
        }
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String sid = p.getSelectedStoryContractId();
        if (sid == null || sid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目未选定初始化快照");
        }
        StoryContractEntity story = storyContractRepository.findById(sid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "快照不存在"));
        if (!projectId.equals(story.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "快照不属于该项目");
        }
        String cur = story.getAuthorIntent() != null ? story.getAuthorIntent() : "";
        String sep = cur.isBlank() ? "" : "\n\n";
        story.setAuthorIntent((cur + sep + "【全局】" + l).trim());
        storyContractRepository.save(story);
    }

    @Transactional
    public void updateSelectedFirstVolumeOutline(String projectId, String outline) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String sid = p.getSelectedStoryContractId();
        if (sid == null || sid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目未选定初始化快照");
        }
        StoryContractEntity story = storyContractRepository.findById(sid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "快照不存在"));
        if (!projectId.equals(story.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "快照不属于该项目");
        }
        story.setFirstVolumeOutline(outline != null ? outline : "");
        storyContractRepository.save(story);
    }

    /** 删除一条已保存的初始化快照；若为当前选中，会先清空项目的选中快照再删除。级联删除该快照下的章纲与动笔前摘要行。 */
    @Transactional
    public void deleteStoryContract(String projectId, String storyContractId) {
        if (storyContractId == null || storyContractId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storyContractId 不能为空");
        }
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        StoryContractEntity s = storyContractRepository.findById(storyContractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!projectId.equals(s.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "快照不属于该项目");
        }
        if (storyContractId.equals(p.getSelectedStoryContractId())) {
            p.setSelectedStoryContractId(null);
            projectRepository.save(p);
        }
        chapterPrewritePlanRepository.deleteByStoryContractId(storyContractId);
        chapterContractRepository.deleteByStoryContractId(storyContractId);
        storyContractRepository.deleteById(storyContractId);
    }
}
