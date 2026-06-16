package com.mythosforge.chapter;

import com.mythosforge.chapter.dto.ChapterNarrativeMetricsRowResponse;
import com.mythosforge.project.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 项目维度章后叙事指标历史（只读）。 */
@RestController
@RequestMapping("/api/projects/{projectId}/narrative-metrics")
public class ProjectNarrativeMetricsController {

    private final ProjectRepository projectRepository;
    private final ChapterNarrativeMetricsRepository chapterNarrativeMetricsRepository;

    public ProjectNarrativeMetricsController(
            ProjectRepository projectRepository,
            ChapterNarrativeMetricsRepository chapterNarrativeMetricsRepository
    ) {
        this.projectRepository = projectRepository;
        this.chapterNarrativeMetricsRepository = chapterNarrativeMetricsRepository;
    }

    @GetMapping
    public List<ChapterNarrativeMetricsRowResponse> list(@PathVariable String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return chapterNarrativeMetricsRepository
                .findTop200ByProjectIdOrderByChapterNoDescCreatedAtDesc(projectId)
                .stream()
                .map(ChapterNarrativeMetricsRowResponse::from)
                .toList();
    }
}
