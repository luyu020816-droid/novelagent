package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.narrative.dto.NarrativeValidationResult;
import com.mythosforge.narrative.dto.ConfluenceUpsertBody;
import com.mythosforge.narrative.dto.NarrativeConfluenceResponse;
import com.mythosforge.narrative.dto.NarrativeStorylineResponse;
import com.mythosforge.narrative.dto.StorylineUpsertBody;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * 叙事结构真源的应用服务：故事线/汇合点 CRUD、任务单预览、校验与 PG→JSON 导出。
 * <p>
 * 所有写操作经 {@link NarrativeValidationService} 断言；删除故事线会级联清理引用它的汇合点并解绑子线 parent。
 * </p>
 */
@Service
public class NarrativeStructureService {

    private final ProjectRepository projectRepository;
    private final NarrativeStorylineRepository storylineRepository;
    private final NarrativeConfluenceRepository confluenceRepository;
    private final ChapterObligationsService chapterObligationsService;
    private final NarrativeValidationService narrativeValidationService;
    private final ObjectMapper objectMapper;
    private final NarrativeDomainBridgeService narrativeDomainBridgeService;
    private final NarrativePhaseGuard narrativePhaseGuard;

    public NarrativeStructureService(
            ProjectRepository projectRepository,
            NarrativeStorylineRepository storylineRepository,
            NarrativeConfluenceRepository confluenceRepository,
            ChapterObligationsService chapterObligationsService,
            NarrativeValidationService narrativeValidationService,
            ObjectMapper objectMapper,
            @Lazy NarrativeDomainBridgeService narrativeDomainBridgeService,
            NarrativePhaseGuard narrativePhaseGuard
    ) {
        this.projectRepository = projectRepository;
        this.storylineRepository = storylineRepository;
        this.confluenceRepository = confluenceRepository;
        this.chapterObligationsService = chapterObligationsService;
        this.narrativeValidationService = narrativeValidationService;
        this.objectMapper = objectMapper;
        this.narrativeDomainBridgeService = narrativeDomainBridgeService;
        this.narrativePhaseGuard = narrativePhaseGuard;
    }

    private void mirrorDomainJson(String projectId) {
        narrativeDomainBridgeService.refreshDomainJsonMirror(projectId);
    }

    private void requireProject(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public List<NarrativeStorylineResponse> listStorylines(String projectId) {
        requireProject(projectId);
        return storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId).stream()
                .map(NarrativeStorylineResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NarrativeConfluenceResponse> listConfluences(String projectId) {
        requireProject(projectId);
        return confluenceRepository.findByProjectIdOrderByTargetChapterAscCreatedAtAsc(projectId).stream()
                .map(NarrativeConfluenceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ObjectNode chapterObligationsPreview(String projectId, int chapterNo) {
        requireProject(projectId);
        return chapterObligationsService.buildChapterObligations(projectId, chapterNo);
    }

    @Transactional(readOnly = true)
    public NarrativeValidationResult validateProject(String projectId) {
        requireProject(projectId);
        return narrativeValidationService.validateProjectStructure(projectId);
    }

    /**
     * 导出 PG 表为 JSON 快照，供迁移旧 narrative_domain_json 或 Neo4j 全量同步。
     */
    @Transactional(readOnly = true)
    public ObjectNode exportDomainFromPg(String projectId) {
        requireProject(projectId);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("_source", "postgresql");
        root.put("projectId", projectId);
        ArrayNode sl = objectMapper.createArrayNode();
        for (NarrativeStorylineResponse s : listStorylines(projectId)) {
            sl.add(objectMapper.valueToTree(s));
        }
        ArrayNode cf = objectMapper.createArrayNode();
        for (NarrativeConfluenceResponse c : listConfluences(projectId)) {
            cf.add(objectMapper.valueToTree(c));
        }
        root.set("storylines", sl);
        root.set("confluences", cf);
        return root;
    }

    @Transactional
    public NarrativeStorylineResponse createStoryline(String projectId, StorylineUpsertBody body) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            narrativeValidationService.assertStorylineUpsert(projectId, body, null);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        int refChapter = body.estStartChapter() != null && body.estStartChapter() > 0
                ? body.estStartChapter()
                : Math.max(1, project.getCurrentChapter() != null ? project.getCurrentChapter() + 1 : 1);
        narrativePhaseGuard.assertAllowNewStoryline(project, refChapter);
        if (body == null || body.storylineKey() == null || body.storylineKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storylineKey 必填");
        }
        if (body.title() == null || body.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title 必填");
        }
        String key = body.storylineKey().trim();
        if (storylineRepository.existsByProjectIdAndStorylineKey(projectId, key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "storylineKey 已存在");
        }
        List<NarrativeStorylineEntity> all = storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId);
        if (body.parentStorylineId() != null && !body.parentStorylineId().isBlank()) {
            ensureStorylineInProject(all, body.parentStorylineId());
            try {
                NarrativeStructureValidator.assertNoParentCycle(all, null, body.parentStorylineId().trim());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
            }
        }
        int maxSort = all.stream().mapToInt(NarrativeStorylineEntity::getSortOrder).max().orElse(0);
        NarrativeStorylineEntity e = new NarrativeStorylineEntity();
        e.setId(UUID.randomUUID().toString().replace("-", ""));
        e.setProjectId(projectId);
        e.setStorylineKey(key);
        e.setTitle(body.title().trim());
        e.setParentStorylineId(trimOrNull(body.parentStorylineId()));
        e.setStorylineRole(normalizeStorylineRole(body.storylineRole()));
        e.setStatus(normalizeStatus(body.status()));
        e.setEstStartChapter(body.estStartChapter());
        e.setEstEndChapter(body.estEndChapter());
        if (body.milestonesJson() != null && !body.milestonesJson().isNull()) {
            e.setMilestonesJson(body.milestonesJson());
        }
        if (body.progressSummary() != null) {
            e.setProgressSummary(trimOrNull(body.progressSummary()));
        }
        if (body.currentMilestoneIndex() != null) {
            e.setCurrentMilestoneIndex(Math.max(0, body.currentMilestoneIndex()));
        }
        e.setLastActiveChapterNo(body.lastActiveChapterNo());
        e.setSortOrder(body.sortOrder() != null ? body.sortOrder() : maxSort + 1);
        validateEstRange(e);
        NarrativeStorylineResponse created = NarrativeStorylineResponse.from(storylineRepository.save(e));
        mirrorDomainJson(projectId);
        return created;
    }

    @Transactional
    public NarrativeStorylineResponse updateStoryline(String projectId, String storylineId, StorylineUpsertBody body) {
        requireProject(projectId);
        try {
            narrativeValidationService.assertStorylineUpsert(projectId, body, storylineId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        NarrativeStorylineEntity e = storylineRepository.findByProjectIdAndId(projectId, storylineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<NarrativeStorylineEntity> all = storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId);
        if (body.parentStorylineId() != null && !body.parentStorylineId().isBlank()) {
            ensureStorylineInProject(all, body.parentStorylineId());
            try {
                NarrativeStructureValidator.assertNoParentCycle(all, e.getId(), body.parentStorylineId().trim());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
            }
            e.setParentStorylineId(body.parentStorylineId().trim());
        } else if (body.parentStorylineId() != null) {
            e.setParentStorylineId(null);
        }
        if (body.title() != null && !body.title().isBlank()) {
            e.setTitle(body.title().trim());
        }
        if (body.storylineRole() != null && !body.storylineRole().isBlank()) {
            e.setStorylineRole(normalizeStorylineRole(body.storylineRole()));
        }
        if (body.status() != null && !body.status().isBlank()) {
            e.setStatus(normalizeStatus(body.status()));
        }
        if (body.estStartChapter() != null) {
            e.setEstStartChapter(body.estStartChapter());
        }
        if (body.estEndChapter() != null) {
            e.setEstEndChapter(body.estEndChapter());
        }
        if (body.milestonesJson() != null) {
            e.setMilestonesJson(body.milestonesJson().isNull() ? null : body.milestonesJson());
        }
        if (body.currentMilestoneIndex() != null) {
            e.setCurrentMilestoneIndex(Math.max(0, body.currentMilestoneIndex()));
        }
        if (body.lastActiveChapterNo() != null) {
            e.setLastActiveChapterNo(body.lastActiveChapterNo());
        }
        if (body.sortOrder() != null) {
            e.setSortOrder(body.sortOrder());
        }
        if (body.progressSummary() != null) {
            e.setProgressSummary(trimOrNull(body.progressSummary()));
        }
        validateEstRange(e);
        NarrativeStorylineResponse updated = NarrativeStorylineResponse.from(storylineRepository.save(e));
        mirrorDomainJson(projectId);
        return updated;
    }

    @Transactional
    public void clearProjectNarrativeStructure(String projectId) {
        requireProject(projectId);
        for (NarrativeConfluenceEntity c : confluenceRepository.findByProjectIdOrderByTargetChapterAscCreatedAtAsc(projectId)) {
            confluenceRepository.delete(c);
        }
        for (NarrativeStorylineEntity s : storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId)) {
            storylineRepository.delete(s);
        }
        mirrorDomainJson(projectId);
    }

    @Transactional
    public void deleteStoryline(String projectId, String storylineId) {
        requireProject(projectId);
        NarrativeStorylineEntity e = storylineRepository.findByProjectIdAndId(projectId, storylineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        confluenceRepository.deleteAllReferencingStoryline(projectId, storylineId);
        for (NarrativeStorylineEntity child : storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId)) {
            if (storylineId.equals(child.getParentStorylineId())) {
                child.setParentStorylineId(null);
                storylineRepository.save(child);
            }
        }
        storylineRepository.delete(e);
        mirrorDomainJson(projectId);
    }

    @Transactional
    public NarrativeConfluenceResponse createConfluence(String projectId, ConfluenceUpsertBody body) {
        requireProject(projectId);
        try {
            narrativeValidationService.assertConfluenceUpsert(projectId, body);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        if (body == null || body.primaryStorylineId() == null || body.secondaryStorylineId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "primaryStorylineId / secondaryStorylineId 必填");
        }
        if (body.primaryStorylineId().equals(body.secondaryStorylineId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "两条故事线须不同");
        }
        if (body.targetChapter() == null || body.targetChapter() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetChapter 须 ≥1");
        }
        List<NarrativeStorylineEntity> all = storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId);
        ensureStorylineInProject(all, body.primaryStorylineId());
        ensureStorylineInProject(all, body.secondaryStorylineId());
        NarrativeConfluenceEntity c = new NarrativeConfluenceEntity();
        c.setId(UUID.randomUUID().toString().replace("-", ""));
        c.setProjectId(projectId);
        c.setPrimaryStorylineId(body.primaryStorylineId().trim());
        c.setSecondaryStorylineId(body.secondaryStorylineId().trim());
        c.setTargetChapter(body.targetChapter());
        c.setConfluenceType(normalizeConfType(body.confluenceType()));
        c.setResolved(false);
        c.setNotes(trimOrNull(body.notes()));
        c.setContextSummary(trimOrNull(body.contextSummary()));
        c.setPreRevealHint(trimOrNull(body.preRevealHint()));
        if (body.behaviorGuards() != null && !body.behaviorGuards().isNull()) {
            c.setBehaviorGuards(body.behaviorGuards());
        }
        NarrativeConfluenceResponse created = NarrativeConfluenceResponse.from(confluenceRepository.save(c));
        mirrorDomainJson(projectId);
        return created;
    }

    /** 手动标记汇合点 resolved（定稿自动 resolved 见 {@link NarrativePostAcceptService}）。 */
    @Transactional
    public NarrativeConfluenceResponse resolveConfluence(String projectId, String confluenceId, boolean resolved) {
        requireProject(projectId);
        NarrativeConfluenceEntity c = confluenceRepository.findById(confluenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!projectId.equals(c.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        c.setResolved(resolved);
        NarrativeConfluenceResponse resolvedRow = NarrativeConfluenceResponse.from(confluenceRepository.save(c));
        mirrorDomainJson(projectId);
        return resolvedRow;
    }

    @Transactional
    public void deleteConfluence(String projectId, String confluenceId) {
        requireProject(projectId);
        NarrativeConfluenceEntity c = confluenceRepository.findById(confluenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!projectId.equals(c.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        confluenceRepository.delete(c);
        mirrorDomainJson(projectId);
    }

    private static void ensureStorylineInProject(List<NarrativeStorylineEntity> all, String id) {
        boolean ok = all.stream().anyMatch(s -> s.getId().equals(id));
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "故事线不存在: " + id);
        }
    }

    private static String trimOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private static String normalizeStatus(String s) {
        if (s == null || s.isBlank()) {
            return "ACTIVE";
        }
        return s.trim().toUpperCase();
    }

    private static String normalizeStorylineRole(String s) {
        if (s == null || s.isBlank()) {
            return "SUB";
        }
        String r = s.trim().toUpperCase();
        if (!"MAIN".equals(r) && !"SUB".equals(r) && !"DARK".equals(r)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storylineRole 须为 MAIN | SUB | DARK");
        }
        return r;
    }

    private static String normalizeConfType(String s) {
        if (s == null || s.isBlank()) {
            return "intersect";
        }
        return s.trim().toLowerCase();
    }

    private static void validateEstRange(NarrativeStorylineEntity e) {
        if (e.getEstStartChapter() != null && e.getEstEndChapter() != null
                && e.getEstEndChapter() < e.getEstStartChapter()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estEndChapter 不得早于 estStartChapter");
        }
    }
}
