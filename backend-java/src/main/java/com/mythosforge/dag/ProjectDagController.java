package com.mythosforge.dag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.dag.dto.ProjectDagSaveRequest;
import com.mythosforge.dag.dto.ProjectDagVersionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/dag")
public class ProjectDagController {

    private final ProjectDagService projectDagService;
    private final ObjectMapper objectMapper;

    public ProjectDagController(ProjectDagService projectDagService, ObjectMapper objectMapper) {
        this.projectDagService = projectDagService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/active")
    public ProjectDagVersionResponse active(@PathVariable String projectId) {
        ProjectDagVersionEntity e = projectDagService.getActiveOrDefault(projectId);
        return ProjectDagVersionResponse.from(e, projectId);
    }

    @GetMapping("/versions")
    public List<ProjectDagVersionResponse> versions(@PathVariable String projectId) {
        projectDagService.requireProject(projectId);
        return projectDagService.listVersions(projectId).stream()
                .map(e -> ProjectDagVersionResponse.from(e, projectId))
                .toList();
    }

    @PutMapping("/active")
    public ProjectDagVersionResponse saveActive(
            @PathVariable String projectId,
            @RequestBody ProjectDagSaveRequest body
    ) {
        ProjectDagVersionEntity saved = projectDagService.saveActiveDag(
                projectId,
                body.dag(),
                body.label()
        );
        return ProjectDagVersionResponse.from(saved, projectId);
    }

    @PostMapping("/validate")
    public JsonNode validate(@PathVariable String projectId, @RequestBody ProjectDagSaveRequest body) {
        projectDagService.requireProject(projectId);
        var errs = projectDagService.validateWithWriterSoft(body.dag());
        ObjectNode out = objectMapper.createObjectNode();
        out.put("ok", errs.isEmpty());
        out.set("errors", errs);
        return out;
    }

    @GetMapping("/node-types")
    public JsonNode nodeTypes(@PathVariable String projectId) {
        projectDagService.requireProject(projectId);
        return projectDagService.fetchWriterNodeTypes();
    }

    @GetMapping("/default-template")
    public JsonNode defaultTemplate(@PathVariable String projectId) {
        projectDagService.requireProject(projectId);
        return projectDagService.fetchWriterDefaultDag();
    }

    @PostMapping("/scaffold-node")
    public JsonNode scaffoldNode(@PathVariable String projectId, @RequestBody JsonNode body) {
        projectDagService.requireProject(projectId);
        return projectDagService.writerScaffoldNode(body);
    }
}
