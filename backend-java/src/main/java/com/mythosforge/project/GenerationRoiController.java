package com.mythosforge.project;

import com.mythosforge.chapter.GenerationJobEntity;
import com.mythosforge.chapter.GenerationJobRepository;
import com.mythosforge.chapter.dto.GenerationRoiJobRow;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Day 15：异步任务 Token ROI 看板数据。 */
@RestController
@RequestMapping("/api/projects/{projectId}/generation-roi")
public class GenerationRoiController {

    private final ProjectService projectService;
    private final GenerationJobRepository generationJobRepository;

    public GenerationRoiController(ProjectService projectService, GenerationJobRepository generationJobRepository) {
        this.projectService = projectService;
        this.generationJobRepository = generationJobRepository;
    }

    @GetMapping
    public List<GenerationRoiJobRow> list(@PathVariable String projectId) {
        projectService.getById(projectId);
        return generationJobRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(GenerationRoiController::toRow)
                .toList();
    }

    private static GenerationRoiJobRow toRow(GenerationJobEntity j) {
        return new GenerationRoiJobRow(
                j.getId(),
                j.getChapterNo(),
                j.getStatus(),
                j.getTotalTokens(),
                j.getRetryWasteTokens(),
                j.getTrimmedOptionalCount(),
                j.getCriticRejectRounds(),
                j.getLlmUsageSummaryJson(),
                j.getTokenBudgetStatusJson(),
                j.getChapterVersionId(),
                j.getCreatedAt() != null ? j.getCreatedAt().toString() : null
        );
    }
}
