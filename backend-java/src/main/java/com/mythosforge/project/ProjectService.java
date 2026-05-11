package com.mythosforge.project;

import com.mythosforge.genre.GenreDecisionContract;
import com.mythosforge.genre.GenreDecisionContractRepository;
import com.mythosforge.project.dto.GenreContractListItem;
import com.mythosforge.project.dto.ProjectCreateRequest;
import com.mythosforge.project.dto.FanSeriesPresetBody;
import com.mythosforge.project.dto.ProjectResponse;
import com.mythosforge.project.dto.ProjectWorkspaceResponse;
import com.mythosforge.project.dto.StoryInitListItem;
import com.mythosforge.story.StoryContractEntity;
import com.mythosforge.story.StoryContractRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * 项目 CRUD、工作区聚合（题材列表 + 初始化快照列表）、选定题材/选定快照的业务校验与落库。
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final GenreDecisionContractRepository genreDecisionContractRepository;
    private final StoryContractRepository storyContractRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ProjectBookExportService projectBookExportService;

    public ProjectService(
            ProjectRepository projectRepository,
            GenreDecisionContractRepository genreDecisionContractRepository,
            StoryContractRepository storyContractRepository,
            JdbcTemplate jdbcTemplate,
            ProjectBookExportService projectBookExportService
    ) {
        this.projectRepository = projectRepository;
        this.genreDecisionContractRepository = genreDecisionContractRepository;
        this.storyContractRepository = storyContractRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.projectBookExportService = projectBookExportService;
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

    @Transactional(readOnly = true)
    public ProjectWorkspaceResponse workspace(String projectId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<GenreContractListItem> genres = genreDecisionContractRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(GenreContractListItem::from)
                .toList();
        List<StoryInitListItem> inits = storyContractRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(StoryInitListItem::from)
                .toList();
        return new ProjectWorkspaceResponse(
                p.getSelectedGenreContractId(),
                p.getSelectedStoryContractId(),
                genres,
                inits
        );
    }

    @Transactional
    public void selectGenreContract(String projectId, String genreContractId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        GenreDecisionContract g = genreDecisionContractRepository.findById(genreContractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "题材方案不存在"));
        if (!projectId.equals(g.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "题材方案不属于该项目");
        }
        p.setSelectedGenreContractId(genreContractId);
        projectRepository.save(p);
    }

    @Transactional
    public void selectStoryBundle(String projectId, String storyContractId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        StoryContractEntity s = storyContractRepository.findById(storyContractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "初始化快照不存在"));
        if (!projectId.equals(s.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "快照不属于该项目");
        }
        p.setSelectedStoryContractId(storyContractId);
        projectRepository.save(p);
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
        if (req.fanSeriesPreset() != null && !req.fanSeriesPreset().isBlank()) {
            String fp = req.fanSeriesPreset().trim();
            if (fp.length() > 64) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fanSeriesPreset 过长（≤64）");
            }
            p.setFanSeriesPreset(fp);
        }
        projectRepository.save(p);
        return ProjectResponse.from(p);
    }

    @Transactional
    public ProjectResponse setFanSeriesPreset(String projectId, FanSeriesPresetBody body) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String raw = body != null ? body.fanSeriesPreset() : null;
        if (raw == null || raw.isBlank()) {
            p.setFanSeriesPreset(null);
        } else {
            String fp = raw.trim();
            if (fp.length() > 64) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fanSeriesPreset 过长（≤64）");
            }
            p.setFanSeriesPreset(fp);
        }
        projectRepository.save(p);
        return ProjectResponse.from(p);
    }

    /**
     * 删除项目及其关联数据（数据库无外键级联，按表手工清理）。
     */
    @Transactional
    public void deleteById(String projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        jdbcTemplate.update("DELETE FROM generation_jobs WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM chapter_commits WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM chapter_versions WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM chapter_contracts WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM story_contracts WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM novel_seed_contracts WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM genre_decision_contracts WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM llm_usage_log WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM memory_summaries WHERE project_id = ?", projectId);
        projectRepository.deleteById(projectId);
        projectBookExportService.deleteProjectExportsQuietly(projectId);
    }
}
