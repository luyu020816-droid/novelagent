package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.mythosforge.narrative.dto.ConfluenceUpsertBody;
import com.mythosforge.narrative.dto.NarrativeValidationIssue;
import com.mythosforge.narrative.dto.NarrativeValidationResult;
import com.mythosforge.narrative.dto.StorylineUpsertBody;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 叙事结构真源的校验入口：编辑前断言（upsert）与项目级全量扫描（validate API）。
 * <p>
 * 错误（errors）阻断保存；警告（warnings）仅提示，如预估章号超出全书 targetChapters。
 * </p>
 */
@Service
public class NarrativeValidationService {

    private static final Set<String> CONFLUENCE_TYPES = Set.of("intersect", "absorb", "reveal");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DORMANT", "CLOSED", "COMPLETED", "PAUSED");

    private final ProjectRepository projectRepository;
    private final NarrativeStorylineRepository storylineRepository;
    private final NarrativeConfluenceRepository confluenceRepository;

    public NarrativeValidationService(
            ProjectRepository projectRepository,
            NarrativeStorylineRepository storylineRepository,
            NarrativeConfluenceRepository confluenceRepository
    ) {
        this.projectRepository = projectRepository;
        this.storylineRepository = storylineRepository;
        this.confluenceRepository = confluenceRepository;
    }

    /**
     * 扫描项目下全部故事线与汇合点，返回错误与警告列表（不抛异常）。
     */
    public NarrativeValidationResult validateProjectStructure(String projectId) {
        List<NarrativeValidationIssue> errors = new ArrayList<>();
        List<NarrativeValidationIssue> warnings = new ArrayList<>();
        Project p = projectRepository.findById(projectId).orElse(null);
        if (p == null) {
            errors.add(NarrativeValidationIssue.error("project_not_found", "项目不存在"));
            return new NarrativeValidationResult(errors, warnings);
        }
        List<NarrativeStorylineEntity> lines = storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId);
        for (NarrativeStorylineEntity s : lines) {
            validateStorylineEntity(s, lines, p.getTargetChapters(), errors, warnings);
            if (s.getParentStorylineId() != null && !s.getParentStorylineId().isBlank()) {
                try {
                    NarrativeStructureValidator.assertNoParentCycle(lines, s.getId(), s.getParentStorylineId().trim());
                } catch (IllegalArgumentException ex) {
                    errors.add(NarrativeValidationIssue.error("parent_cycle", s.getStorylineKey() + ": " + ex.getMessage()));
                }
            }
        }
        for (NarrativeConfluenceEntity c : confluenceRepository.findByProjectIdOrderByTargetChapterAscCreatedAtAsc(projectId)) {
            validateConfluenceEntity(c, lines, p.getTargetChapters(), errors, warnings);
        }
        return new NarrativeValidationResult(errors, warnings);
    }

    /**
     * 创建/更新故事线前的不变量检查；有问题时抛 {@link IllegalArgumentException}。
     *
     * @param selfId 更新时为本条故事线 id，创建时为 null
     */
    public void assertStorylineUpsert(String projectId, StorylineUpsertBody body, String selfId) {
        List<NarrativeValidationIssue> errors = new ArrayList<>();
        List<NarrativeValidationIssue> warnings = new ArrayList<>();
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
        List<NarrativeStorylineEntity> all = storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId);
        if (body.parentStorylineId() != null && !body.parentStorylineId().isBlank()) {
            NarrativeStructureValidator.assertNoParentCycle(all, selfId, body.parentStorylineId().trim());
        }
        if (body.status() != null && !body.status().isBlank()) {
            String st = body.status().trim().toUpperCase();
            if (!STATUSES.contains(st)) {
                errors.add(NarrativeValidationIssue.error("invalid_status", "status 无效: " + st));
            }
        }
        if (body.milestonesJson() != null && !body.milestonesJson().isNull()) {
            try {
                MilestoneSchemaValidator.validate(body.milestonesJson());
            } catch (IllegalArgumentException ex) {
                errors.add(NarrativeValidationIssue.error("invalid_milestones", ex.getMessage()));
            }
        }
        if (body.estStartChapter() != null && body.estEndChapter() != null
                && body.estEndChapter() < body.estStartChapter()) {
            errors.add(NarrativeValidationIssue.error("est_range", "estEndChapter 不得早于 estStartChapter"));
        }
        if (body.estEndChapter() != null && body.estEndChapter() > p.getTargetChapters()) {
            warnings.add(NarrativeValidationIssue.warning("est_beyond_target", "estEndChapter 超过全书 targetChapters"));
        }
        throwIfErrors(errors);
    }

    /**
     * 创建汇合点前的类型与 reveal 约束检查；故事线存在性由 {@link NarrativeStructureService} 另行保证。
     */
    public void assertConfluenceUpsert(String projectId, ConfluenceUpsertBody body) {
        List<NarrativeValidationIssue> errors = new ArrayList<>();
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
        List<NarrativeStorylineEntity> all = storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId);
        String type = body.confluenceType() == null || body.confluenceType().isBlank()
                ? "intersect"
                : body.confluenceType().trim().toLowerCase();
        if (!CONFLUENCE_TYPES.contains(type)) {
            errors.add(NarrativeValidationIssue.error("invalid_confluence_type", "confluenceType 须为 intersect|absorb|reveal"));
        }
        if ("reveal".equals(type) && !hasBehaviorGuards(body.behaviorGuards())) {
            errors.add(NarrativeValidationIssue.error("reveal_requires_guards", "reveal 类型须填写 behaviorGuards"));
        }
        if (body.targetChapter() != null && body.targetChapter() > p.getTargetChapters()) {
            errors.add(NarrativeValidationIssue.error("target_beyond_book", "targetChapter 超过全书 targetChapters"));
        }
        throwIfErrors(errors);
    }

    private static boolean hasBehaviorGuards(JsonNode guards) {
        if (guards == null || guards.isNull()) {
            return false;
        }
        if (guards.isArray()) {
            for (JsonNode g : guards) {
                if (g.isTextual() && !g.asText().isBlank()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void validateStorylineEntity(
            NarrativeStorylineEntity s,
            List<NarrativeStorylineEntity> all,
            int targetChapters,
            List<NarrativeValidationIssue> errors,
            List<NarrativeValidationIssue> warnings
    ) {
        if (s.getMilestonesJson() != null && !s.getMilestonesJson().isNull()) {
            try {
                MilestoneSchemaValidator.validate(s.getMilestonesJson());
            } catch (IllegalArgumentException ex) {
                errors.add(NarrativeValidationIssue.error("invalid_milestones", s.getStorylineKey() + ": " + ex.getMessage()));
            }
        }
        if (s.getEstEndChapter() != null && s.getEstEndChapter() > targetChapters) {
            warnings.add(NarrativeValidationIssue.warning(
                    "est_beyond_target",
                    s.getStorylineKey() + " estEndChapter > targetChapters"
            ));
        }
    }

    private void validateConfluenceEntity(
            NarrativeConfluenceEntity c,
            List<NarrativeStorylineEntity> lines,
            int targetChapters,
            List<NarrativeValidationIssue> errors,
            List<NarrativeValidationIssue> warnings
    ) {
        String type = c.getConfluenceType() == null ? "intersect" : c.getConfluenceType().toLowerCase();
        if ("reveal".equals(type) && !hasBehaviorGuards(c.getBehaviorGuards())) {
            errors.add(NarrativeValidationIssue.error("reveal_requires_guards", "汇合 " + c.getId() + " 缺少 behaviorGuards"));
        }
        if (c.getTargetChapter() > targetChapters) {
            warnings.add(NarrativeValidationIssue.warning("target_beyond_book", "汇合 targetChapter 超过全书上限"));
        }
        boolean primaryOk = lines.stream().anyMatch(l -> l.getId().equals(c.getPrimaryStorylineId()));
        boolean secondaryOk = lines.stream().anyMatch(l -> l.getId().equals(c.getSecondaryStorylineId()));
        if (!primaryOk || !secondaryOk) {
            errors.add(NarrativeValidationIssue.error("storyline_missing", "汇合引用故事线不存在: " + c.getId()));
        }
    }

    private static void throwIfErrors(List<NarrativeValidationIssue> errors) {
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(errors.get(0).message());
        }
    }
}
