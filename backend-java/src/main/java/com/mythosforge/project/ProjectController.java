package com.mythosforge.project;

import com.mythosforge.project.dto.ProjectCreateRequest;
import com.mythosforge.project.dto.ProjectDetailResponse;
import com.mythosforge.project.dto.ProjectResponse;
import com.mythosforge.writer.WriterEngineClient;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final WriterEngineClient writerEngineClient;

    public ProjectController(ProjectService projectService, WriterEngineClient writerEngineClient) {
        this.projectService = projectService;
        this.writerEngineClient = writerEngineClient;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody ProjectCreateRequest body) {
        return projectService.create(body);
    }
}
