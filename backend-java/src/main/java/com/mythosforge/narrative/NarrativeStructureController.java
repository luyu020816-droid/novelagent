package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.narrative.dto.NarrativeValidationResult;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.narrative.dto.ConfluenceUpsertBody;
import com.mythosforge.narrative.dto.NarrativeConfluenceResponse;
import com.mythosforge.narrative.dto.NarrativeStorylineResponse;
import com.mythosforge.narrative.dto.ResolveConfluenceBody;
import com.mythosforge.narrative.dto.StorylineUpsertBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST：PostgreSQL 叙事结构真源（故事线、汇合点、任务单、校验、导出、接受策略）。
 * <p>
 * 路径前缀：{@code /api/projects/{projectId}/narrative}。汇合点无 PATCH，需改字段时删后重建。
 * </p>
 */
@RestController
@RequestMapping("/api/projects/{projectId}/narrative")
public class NarrativeStructureController {

    private final NarrativeStructureService narrativeStructureService;
    private final NarrativeDomainBridgeService narrativeDomainBridgeService;
    private final NarrativeDebtService narrativeDebtService;
    private final NarrativeCausalEdgeService narrativeCausalEdgeService;
    private final ProjectRepository projectRepository;

    public NarrativeStructureController(
            NarrativeStructureService narrativeStructureService,
            NarrativeDomainBridgeService narrativeDomainBridgeService,
            NarrativeDebtService narrativeDebtService,
            NarrativeCausalEdgeService narrativeCausalEdgeService,
            ProjectRepository projectRepository
    ) {
        this.narrativeStructureService = narrativeStructureService;
        this.narrativeDomainBridgeService = narrativeDomainBridgeService;
        this.narrativeDebtService = narrativeDebtService;
        this.narrativeCausalEdgeService = narrativeCausalEdgeService;
        this.projectRepository = projectRepository;
    }

    @GetMapping("/storylines")
    public List<NarrativeStorylineResponse> listStorylines(@PathVariable String projectId) {
        return narrativeStructureService.listStorylines(projectId);
    }

    @PostMapping("/storylines")
    public NarrativeStorylineResponse createStoryline(
            @PathVariable String projectId,
            @Valid @RequestBody StorylineUpsertBody body
    ) {
        return narrativeStructureService.createStoryline(projectId, body);
    }

    @PatchMapping("/storylines/{storylineId}")
    public NarrativeStorylineResponse updateStoryline(
            @PathVariable String projectId,
            @PathVariable String storylineId,
            @Valid @RequestBody StorylineUpsertBody body
    ) {
        return narrativeStructureService.updateStoryline(projectId, storylineId, body);
    }

    @DeleteMapping("/storylines/{storylineId}")
    public void deleteStoryline(@PathVariable String projectId, @PathVariable String storylineId) {
        narrativeStructureService.deleteStoryline(projectId, storylineId);
    }

    @GetMapping("/confluences")
    public List<NarrativeConfluenceResponse> listConfluences(@PathVariable String projectId) {
        return narrativeStructureService.listConfluences(projectId);
    }

    @PostMapping("/confluences")
    public NarrativeConfluenceResponse createConfluence(
            @PathVariable String projectId,
            @Valid @RequestBody ConfluenceUpsertBody body
    ) {
        return narrativeStructureService.createConfluence(projectId, body);
    }

    @PostMapping("/confluences/{confluenceId}/resolve")
    public NarrativeConfluenceResponse resolveConfluence(
            @PathVariable String projectId,
            @PathVariable String confluenceId,
            @Valid @RequestBody ResolveConfluenceBody body
    ) {
        return narrativeStructureService.resolveConfluence(projectId, confluenceId, body.resolved());
    }

    @DeleteMapping("/confluences/{confluenceId}")
    public void deleteConfluence(@PathVariable String projectId, @PathVariable String confluenceId) {
        narrativeStructureService.deleteConfluence(projectId, confluenceId);
    }

    /** 按章号预览 Writer 将收到的任务单 JSON（与生成管线一致）。 */
    @GetMapping("/chapter-obligations-preview")
    public ObjectNode chapterObligationsPreview(
            @PathVariable String projectId,
            @RequestParam("chapterNo") int chapterNo
    ) {
        return narrativeStructureService.chapterObligationsPreview(projectId, chapterNo);
    }

    /** 开放叙事债务列表（PlotPilot DEBT_DUE 真源）。 */
    @GetMapping("/open-debts")
    public ObjectNode listOpenDebts(
            @PathVariable String projectId,
            @RequestParam(value = "chapterNo", defaultValue = "1") int chapterNo
    ) {
        narrativeDebtService.syncFromProjectSources(projectId, chapterNo);
        ObjectNode root = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        root.set("debts", narrativeDebtService.openDebtsJson(projectId, chapterNo));
        root.set("causalEdges", narrativeCausalEdgeService.openEdgesJson(projectId, chapterNo));
        return root;
    }

    /** 全量结构校验，返回 errors / warnings，不修改数据。 */
    @GetMapping("/validate")
    public NarrativeValidationResult validate(@PathVariable String projectId) {
        return narrativeStructureService.validateProject(projectId);
    }

    /** 将 PG 表导出为与旧 narrative_domain_json 兼容的快照（含 _source=postgresql）。 */
    @GetMapping("/export-domain-json")
    public ObjectNode exportDomainFromPg(@PathVariable String projectId) {
        return narrativeStructureService.exportDomainFromPg(projectId);
    }

    /**
     * 写入 projects.narrative_accept_policy_json（定稿回写、子文本窗口、Autopilot 硬检查模式等）。
     */
    /** 将 PG 真源导出并写入 projects.narrative_domain_json（镜像，供备份/高级 Tab 展示）。 */
    @PostMapping("/sync-domain-json")
    public ObjectNode syncDomainJson(@PathVariable String projectId) {
        return narrativeDomainBridgeService.syncDomainJsonToProject(projectId);
    }

    /**
     * 从请求体或项目已存的 narrative_domain_json 导入 PG 表，并回写镜像 JSON。
     */
    @PostMapping("/import-from-domain-json")
    public NarrativeDomainBridgeService.NarrativeDomainImportResult importFromDomainJson(
            @PathVariable String projectId,
            @RequestBody(required = false) JsonNode body
    ) {
        JsonNode domain = body;
        if (domain == null || domain.isNull()) {
            domain = projectRepository.findById(projectId)
                    .map(com.mythosforge.project.Project::getNarrativeDomainJson)
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND
                    ));
        }
        if (domain == null || domain.isNull()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "叙事域 JSON 为空"
            );
        }
        try {
            return narrativeDomainBridgeService.importFromDomainJson(projectId, domain);
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    ex.getMessage()
            );
        }
    }

    @PostMapping("/accept-policy")
    public JsonNode patchAcceptPolicy(@PathVariable String projectId, @RequestBody JsonNode body) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND
                ));
        p.setNarrativeAcceptPolicyJson(body);
        projectRepository.save(p);
        return body;
    }
}
