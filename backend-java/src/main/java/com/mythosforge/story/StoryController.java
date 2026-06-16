package com.mythosforge.story;

import com.mythosforge.project.ProjectService;
import com.mythosforge.story.dto.SelectStoryBundleRequest;
import com.mythosforge.story.dto.StoryGovernanceAppendIntentRequest;
import com.mythosforge.story.dto.StoryGovernanceUpdateRequest;
import com.mythosforge.story.dto.StoryInitOptionsBody;
import com.mythosforge.story.dto.StoryInitResponse;
import com.mythosforge.story.dto.StoryOutlineUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 初始化小说 API：阻塞 {@code /init}、SSE {@code /init/stream}、加载/切换当前选中快照 {@code selected-bundle}。
 */
@RestController
@RequestMapping("/api/projects/{projectId}/story")
public class StoryController {

    private final StoryService storyService;
    private final ProjectService projectService;
    private final TaskExecutor applicationTaskExecutor;

    public StoryController(
            StoryService storyService,
            ProjectService projectService,
            @Qualifier("applicationTaskExecutor") TaskExecutor applicationTaskExecutor
    ) {
        this.storyService = storyService;
        this.projectService = projectService;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @PostMapping("/init")
    public StoryInitResponse init(
            @PathVariable String projectId,
            @RequestBody(required = false) StoryInitOptionsBody body
    ) {
        return storyService.init(projectId, body != null ? body.wizardNotes() : null);
    }

    @PostMapping(value = "/init/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter initStream(
            @PathVariable String projectId,
            @RequestBody(required = false) StoryInitOptionsBody body
    ) {
        storyService.requireProjectAndGenreForInitStream(projectId);
        SseEmitter emitter = new SseEmitter(900_000L);
        String wizardNotes = body != null ? body.wizardNotes() : null;
        applicationTaskExecutor.execute(() -> storyService.initStream(projectId, wizardNotes, emitter));
        return emitter;
    }

    @GetMapping("/selected-bundle")
    public ResponseEntity<StoryInitResponse> selectedBundle(@PathVariable String projectId) {
        return storyService.loadSelectedStoryBundle(projectId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/selected-bundle")
    public void selectStoryBundle(
            @PathVariable String projectId,
            @Valid @RequestBody SelectStoryBundleRequest body
    ) {
        projectService.selectStoryBundle(projectId, body.storyContractId());
    }

    /** 修改当前选中快照的第一卷大纲正文（不影响章纲 JSON）。 */
    @PutMapping("/selected-bundle/outline")
    public void updateSelectedOutline(
            @PathVariable String projectId,
            @RequestBody StoryOutlineUpdateRequest body
    ) {
        String text = body != null && body.firstVolumeOutline() != null ? body.firstVolumeOutline() : "";
        storyService.updateSelectedFirstVolumeOutline(projectId, text);
    }

    /** 作者意图与不可违背条目（写入当前选中 story_contracts，并由章节生成注入 Writer）。 */
    @PutMapping("/selected-bundle/governance")
    public void updateSelectedGovernance(
            @PathVariable String projectId,
            @RequestBody(required = false) StoryGovernanceUpdateRequest body
    ) {
        String intent = body != null && body.authorIntent() != null ? body.authorIntent() : "";
        com.fasterxml.jackson.databind.JsonNode nn =
                body != null ? body.nonNegotiables() : null;
        String styleGuideMd = body != null ? body.styleGuideMd() : null;
        storyService.updateSelectedGovernance(projectId, intent, nn, styleGuideMd);
    }

    /** 在作者意图末尾追加一行（服务端自动加「【全局】」前缀）。 */
    @PostMapping("/selected-bundle/governance/append-intent")
    public void appendGovernanceIntentLine(
            @PathVariable String projectId,
            @RequestBody(required = false) StoryGovernanceAppendIntentRequest body
    ) {
        String line = body != null ? body.line() : "";
        storyService.appendSelectedAuthorIntentLine(projectId, line);
    }

    /** 删除一条已保存的初始化快照；若为当前选中会先清空项目选中再删；级联删除该快照下的章纲与动笔前摘要行。 */
    @DeleteMapping("/contracts/{storyContractId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStoryContract(
            @PathVariable String projectId,
            @PathVariable String storyContractId
    ) {
        storyService.deleteStoryContract(projectId, storyContractId);
    }
}
