package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.narrative.dto.ConfluenceUpsertBody;
import com.mythosforge.narrative.dto.StorylineUpsertBody;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PostgreSQL 叙事真源与 {@code projects.narrative_domain_json} 双向同步，避免双轨脱节。
 */
@Service
public class NarrativeDomainBridgeService {

    private static final Logger log = LoggerFactory.getLogger(NarrativeDomainBridgeService.class);

    private final ProjectRepository projectRepository;
    private final NarrativeStorylineRepository storylineRepository;
    private final NarrativeConfluenceRepository confluenceRepository;
    private final NarrativeStructureService narrativeStructureService;
    private final ObjectMapper objectMapper;

    private static final ThreadLocal<Boolean> SUPPRESS_JSON_MIRROR = ThreadLocal.withInitial(() -> false);

    public NarrativeDomainBridgeService(
            ProjectRepository projectRepository,
            NarrativeStorylineRepository storylineRepository,
            NarrativeConfluenceRepository confluenceRepository,
            NarrativeStructureService narrativeStructureService,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.storylineRepository = storylineRepository;
        this.confluenceRepository = confluenceRepository;
        this.narrativeStructureService = narrativeStructureService;
        this.objectMapper = objectMapper;
    }

    /**
     * 将 PG 表导出并写入项目的 narrative_domain_json（生成侧仍以 PG 为准，JSON 为镜像快照）。
     */
    @Transactional
    public ObjectNode syncDomainJsonToProject(String projectId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
        ObjectNode exported = narrativeStructureService.exportDomainFromPg(projectId);
        p.setNarrativeDomainJson(exported);
        projectRepository.save(p);
        log.debug("narrative domain json synced from PG project={}", projectId);
        return exported;
    }

    /**
     * 从 narrative_domain_json 对象导入故事线/汇合点到 PG（按 storylineKey 与端点对 upsert）。
     */
    @Transactional
    public NarrativeDomainImportResult importFromDomainJson(String projectId, JsonNode domain) {
        if (domain == null || domain.isNull() || !domain.isObject()) {
            throw new IllegalArgumentException("叙事域须为 JSON 对象");
        }
        projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在"));

        Map<String, String> jsonIdToPgId = new HashMap<>();
        int[] storylinesUpserted = {0};
        int[] confluencesCreated = {0};

        NarrativeDomainImportResult[] result = new NarrativeDomainImportResult[1];
        runWithoutJsonMirror(() -> {
        JsonNode slArr = domain.get("storylines");
        if (slArr != null && slArr.isArray()) {
            for (JsonNode sl : slArr) {
                if (!sl.isObject()) {
                    continue;
                }
                String key = firstText(sl, "storylineKey", "key");
                if (key == null || key.isBlank()) {
                    continue;
                }
                String title = firstText(sl, "title");
                if (title == null || title.isBlank()) {
                    title = key;
                }
                String parentRef = firstText(sl, "parentStorylineId", "parentId", "parentStorylineKey");
                String parentPgId = resolveStorylineRef(projectId, jsonIdToPgId, parentRef);

                StorylineUpsertBody body = new StorylineUpsertBody(
                        key.trim(),
                        title.trim(),
                        parentPgId,
                        firstText(sl, "storylineRole", "role"),
                        firstText(sl, "status"),
                        intOrNull(sl, "estStartChapter"),
                        intOrNull(sl, "estEndChapter"),
                        sl.has("milestonesJson") ? sl.get("milestonesJson") : sl.get("milestones"),
                        intOrNull(sl, "currentMilestoneIndex"),
                        intOrNull(sl, "lastActiveChapterNo"),
                        intOrNull(sl, "sortOrder"),
                        firstText(sl, "progressSummary")
                );

                Optional<NarrativeStorylineEntity> existing =
                        storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId).stream()
                                .filter(e -> key.equals(e.getStorylineKey()))
                                .findFirst();

                String pgId;
                if (existing.isPresent()) {
                    pgId = narrativeStructureService.updateStoryline(projectId, existing.get().getId(), body).id();
                } else {
                    pgId = narrativeStructureService.createStoryline(projectId, body).id();
                }
                storylinesUpserted[0]++;
                String jsonId = sl.path("id").asText("");
                if (!jsonId.isBlank()) {
                    jsonIdToPgId.put(jsonId, pgId);
                }
                jsonIdToPgId.put(key.trim(), pgId);
            }
        }

        JsonNode cfArr = domain.get("confluences");
        if (cfArr != null && cfArr.isArray()) {
            List<NarrativeConfluenceEntity> existing =
                    confluenceRepository.findByProjectIdOrderByTargetChapterAscCreatedAtAsc(projectId);
            for (JsonNode cf : cfArr) {
                if (!cf.isObject()) {
                    continue;
                }
                String primaryRef = firstText(cf, "primaryStorylineId", "primaryId", "primaryStorylineKey");
                String secondaryRef = firstText(cf, "secondaryStorylineId", "secondaryId", "secondaryStorylineKey");
                Integer target = intOrNull(cf, "targetChapter");
                if (primaryRef == null || secondaryRef == null || target == null || target < 1) {
                    continue;
                }
                String primaryId = resolveStorylineRef(projectId, jsonIdToPgId, primaryRef);
                String secondaryId = resolveStorylineRef(projectId, jsonIdToPgId, secondaryRef);
                if (primaryId == null || secondaryId == null) {
                    continue;
                }
                boolean already = existing.stream().anyMatch(
                        c -> primaryId.equals(c.getPrimaryStorylineId())
                                && secondaryId.equals(c.getSecondaryStorylineId())
                                && target == c.getTargetChapter()
                );
                if (already) {
                    continue;
                }
                ConfluenceUpsertBody body = new ConfluenceUpsertBody(
                        primaryId,
                        secondaryId,
                        target,
                        firstText(cf, "confluenceType", "mergeType"),
                        firstText(cf, "notes"),
                        firstText(cf, "contextSummary"),
                        firstText(cf, "preRevealHint"),
                        cf.has("behaviorGuards") ? cf.get("behaviorGuards") : null
                );
                narrativeStructureService.createConfluence(projectId, body);
                confluencesCreated[0]++;
            }
        }
        result[0] = new NarrativeDomainImportResult(storylinesUpserted[0], confluencesCreated[0], null);
        });
        ObjectNode snapshot = syncDomainJsonToProject(projectId);
        return new NarrativeDomainImportResult(
                result[0].storylinesUpserted(),
                result[0].confluencesCreated(),
                snapshot
        );
    }

    /** PG 结构变更后刷新 JSON 镜像（不触发二次 import）。 */
    public void refreshDomainJsonMirror(String projectId) {
        if (Boolean.TRUE.equals(SUPPRESS_JSON_MIRROR.get())) {
            return;
        }
        try {
            syncDomainJsonToProject(projectId);
        } catch (Exception ex) {
            log.warn("refresh narrative_domain_json failed project={}: {}", projectId, ex.getMessage());
        }
    }

    static void runWithoutJsonMirror(Runnable action) {
        SUPPRESS_JSON_MIRROR.set(true);
        try {
            action.run();
        } finally {
            SUPPRESS_JSON_MIRROR.remove();
        }
    }

    private String resolveStorylineRef(String projectId, Map<String, String> jsonIdToPgId, String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String trimmed = ref.trim();
        if (jsonIdToPgId.containsKey(trimmed)) {
            return jsonIdToPgId.get(trimmed);
        }
        return storylineRepository.findByProjectIdAndId(projectId, trimmed)
                .map(NarrativeStorylineEntity::getId)
                .orElseGet(() -> storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId).stream()
                        .filter(s -> trimmed.equals(s.getStorylineKey()))
                        .map(NarrativeStorylineEntity::getId)
                        .findFirst()
                        .orElse(null));
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String f : fields) {
            if (node.has(f) && !node.get(f).isNull()) {
                String t = node.get(f).asText("").trim();
                if (!t.isEmpty()) {
                    return t;
                }
            }
        }
        return null;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        int v = node.get(field).asInt();
        return v;
    }

    public record NarrativeDomainImportResult(int storylinesUpserted, int confluencesCreated, ObjectNode domainSnapshot) {}
}
