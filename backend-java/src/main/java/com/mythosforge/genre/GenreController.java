package com.mythosforge.genre;

import com.mythosforge.genre.dto.GenreContractDetailResponse;
import com.mythosforge.genre.dto.GenreContractUpdateRequest;
import com.mythosforge.genre.dto.GenreInterviewRequest;
import com.mythosforge.genre.dto.GenreInterviewResponse;
import com.mythosforge.genre.dto.GenreRecommendRequest;
import com.mythosforge.genre.dto.GenreRecommendResponse;
import com.mythosforge.genre.dto.GenreStoryHookStreamRequest;
import com.mythosforge.genre.dto.SelectGenreContractRequest;
import com.mythosforge.project.ProjectService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 题材 API：阻塞推荐、SSE 流（偏好/故事线）、路径 B 采访、选题与单条合同的查改删。
 * SSE 路径在返回 {@link SseEmitter} 后用 {@code applicationTaskExecutor} 异步透传 Writer 流。
 */
@RestController
@RequestMapping("/api/projects/{projectId}/genre")
public class GenreController {

    private final GenreService genreService;
    private final ProjectService projectService;
    private final TaskExecutor applicationTaskExecutor;

    public GenreController(
            GenreService genreService,
            ProjectService projectService,
            @Qualifier("applicationTaskExecutor") TaskExecutor applicationTaskExecutor
    ) {
        this.genreService = genreService;
        this.projectService = projectService;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @PostMapping("/recommend")
    public GenreRecommendResponse recommend(
            @PathVariable String projectId,
            @Valid @RequestBody GenreRecommendRequest body
    ) {
        return genreService.recommend(projectId, body);
    }

    @PostMapping(value = "/recommend/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter recommendStream(
            @PathVariable String projectId,
            @Valid @RequestBody GenreRecommendRequest body
    ) {
        genreService.requireProjectForGenreStream(projectId);
        SseEmitter emitter = new SseEmitter(900_000L);
        applicationTaskExecutor.execute(() -> genreService.recommendStream(projectId, body, emitter));
        return emitter;
    }

    /** 路径 B：多轮互动采访（非流式）；complete 时 Java 落库 novel_seed_contracts。 */
    @PostMapping("/interview")
    public GenreInterviewResponse interview(
            @PathVariable String projectId,
            @Valid @RequestBody GenreInterviewRequest body
    ) {
        return genreService.interview(projectId, body);
    }

    /** 根据一两句故事线走同一套 Writer 流水线（SSE）。 */
    @PostMapping(value = "/recommend/from-story/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter recommendFromStoryHookStream(
            @PathVariable String projectId,
            @Valid @RequestBody GenreStoryHookStreamRequest body
    ) {
        genreService.requireProjectForGenreStream(projectId);
        SseEmitter emitter = new SseEmitter(900_000L);
        applicationTaskExecutor.execute(() -> genreService.recommendFromStoryHookStream(projectId, body, emitter));
        return emitter;
    }

    /** 选定用于「初始化小说」的题材方案（多份并存时必选其一）。 */
    @PutMapping("/selected-contract")
    public void selectGenreContract(
            @PathVariable String projectId,
            @Valid @RequestBody SelectGenreContractRequest body
    ) {
        projectService.selectGenreContract(projectId, body.genreContractId());
    }

    @GetMapping("/{contractId}")
    public GenreContractDetailResponse getGenreContract(
            @PathVariable String projectId,
            @PathVariable String contractId
    ) {
        return genreService.getGenreContract(projectId, contractId);
    }

    @PutMapping("/{contractId}")
    public GenreContractDetailResponse updateGenreContract(
            @PathVariable String projectId,
            @PathVariable String contractId,
            @RequestBody GenreContractUpdateRequest body
    ) {
        return genreService.updateGenreContract(projectId, contractId, body);
    }

    @DeleteMapping("/{contractId}")
    public void deleteGenreContract(
            @PathVariable String projectId,
            @PathVariable String contractId
    ) {
        genreService.deleteGenreContract(projectId, contractId);
    }
}
