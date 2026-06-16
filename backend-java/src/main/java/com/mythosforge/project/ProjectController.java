package com.mythosforge.project;

import com.mythosforge.project.dto.AutopilotSettingsBody;
import com.mythosforge.project.dto.EmergencyPauseBody;
import com.mythosforge.project.dto.EntityReplaceRequest;
import com.mythosforge.project.dto.NarrativeDomainPatchBody;
import com.mythosforge.project.dto.ProjectCreateRequest;
import com.mythosforge.project.dto.ProjectDetailResponse;
import com.mythosforge.project.dto.FanSeriesPresetBody;
import com.mythosforge.project.dto.ProjectResponse;
import com.mythosforge.project.dto.ProjectWorkspaceResponse;
import com.mythosforge.writer.WriterEngineClient;
import com.mythosforge.writer.WriterHttpService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * REST：项目列表/详情/创建、详情页附带 Writer 探测、{@code /workspace} 工作区数据。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final WriterEngineClient writerEngineClient;
    private final WriterHttpService writerHttpService;
    private final ProjectBookExportService projectBookExportService;
    private final EntityReplaceService entityReplaceService;

    public ProjectController(
            ProjectService projectService,
            WriterEngineClient writerEngineClient,
            WriterHttpService writerHttpService,
            ProjectBookExportService projectBookExportService,
            EntityReplaceService entityReplaceService
    ) {
        this.projectService = projectService;
        this.writerEngineClient = writerEngineClient;
        this.writerHttpService = writerHttpService;
        this.projectBookExportService = projectBookExportService;
        this.entityReplaceService = entityReplaceService;
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projectService.list();
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable String projectId) {
        return projectService.getById(projectId);
    }

    @GetMapping("/{projectId}/detail")
    public ProjectDetailResponse detail(@PathVariable String projectId) {
        ProjectResponse project = projectService.getById(projectId);
        return new ProjectDetailResponse(project, writerEngineClient.probeAll());
    }

    @GetMapping("/{projectId}/workspace")
    public ProjectWorkspaceResponse workspace(@PathVariable String projectId) {
        return projectService.workspace(projectId);
    }

    /** 已定稿章节一键合成 Markdown（按章节号排序）。 */
    @GetMapping(value = "/{projectId}/export/accepted-book.md", produces = "text/markdown;charset=UTF-8")
    public String exportAcceptedBook(@PathVariable String projectId) {
        projectService.getById(projectId);
        return projectBookExportService.buildAcceptedBookMarkdown(projectId);
    }

    /**
     * Neo4j 世界观图谱快照（代理 Writer），供前端「世界观状态」表格展示。
     */
    @GetMapping("/{projectId}/lore-graph")
    public JsonNode loreGraph(@PathVariable String projectId) {
        projectService.getById(projectId);
        return writerHttpService.getJson("/api/writer/lore/" + projectId + "/snapshot");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody ProjectCreateRequest body) {
        return projectService.create(body);
    }

    /** 设置丛书/同人预设（如 hp_fan）；请求体 {"fanSeriesPreset":null} 或空字符串表示清除。 */
    @PostMapping("/{projectId}/fan-series-preset")
    public ProjectResponse setFanSeriesPreset(
            @PathVariable String projectId,
            @RequestBody(required = false) FanSeriesPresetBody body
    ) {
        return projectService.setFanSeriesPreset(projectId, body);
    }

    /**
     * 全书字符串替换：快照大纲 / 章纲 JSON / 章节正文与 critic（越长字符串优先）。
     * 不更新 Neo4j 图谱；改名后请核对世界观一致性。
     */
    @PostMapping("/{projectId}/entities/replace")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceEntities(
            @PathVariable String projectId,
            @Valid @RequestBody EntityReplaceRequest body
    ) {
        projectService.getById(projectId);
        entityReplaceService.replaceEntities(projectId, body);
    }

    @PostMapping("/{projectId}/autopilot/settings")
    public ProjectResponse setAutopilotSettings(
            @PathVariable String projectId,
            @RequestBody(required = false) AutopilotSettingsBody body
    ) {
        return projectService.updateAutopilotSettings(projectId, body);
    }

    @PostMapping("/{projectId}/autopilot/emergency-pause")
    public ProjectResponse emergencyPauseAutopilot(
            @PathVariable String projectId,
            @RequestBody(required = false) EmergencyPauseBody body
    ) {
        String reason = body != null && body.reason() != null && !body.reason().isBlank()
                ? body.reason()
                : "emergency_pause";
        return projectService.pauseAutopilot(projectId, reason);
    }

    @PostMapping("/{projectId}/autopilot/start-run")
    public ProjectResponse startAutopilotRun(@PathVariable String projectId) {
        return projectService.startAutopilotRun(projectId);
    }

    @PostMapping("/{projectId}/narrative-domain")
    public ProjectResponse patchNarrativeDomain(
            @PathVariable String projectId,
            @Valid @RequestBody NarrativeDomainPatchBody body
    ) {
        return projectService.patchNarrativeDomain(projectId, body);
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String projectId) {
        projectService.deleteById(projectId);
    }
}
