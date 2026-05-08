package com.mythosforge.project;

import com.mythosforge.project.dto.ProjectCreateRequest;
import com.mythosforge.project.dto.ProjectResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list() {
        return projectRepository.findAll().stream().map(ProjectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(String id) {
        return projectRepository.findById(id)
                .map(ProjectResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public ProjectResponse create(ProjectCreateRequest req) {
        Project p = new Project();
        p.setId(UUID.randomUUID().toString().replace("-", ""));
        p.setName(req.name().trim());
        if (req.language() != null && !req.language().isBlank()) {
            p.setLanguage(req.language().trim());
        }
        if (req.targetChapters() != null && req.targetChapters() > 0) {
            p.setTargetChapters(req.targetChapters());
        }
        projectRepository.save(p);
        return ProjectResponse.from(p);
    }
}
