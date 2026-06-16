package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.writer.WriterHttpService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 番茄编辑点评、合并意见润色等（经 Writer 非流式 JSON）。
 */
@Service
public class ChapterEditorAssistService {

    private final ChapterGenerationService chapterGenerationService;
    private final ChapterVersionRepository chapterVersionRepository;
    private final WriterHttpService writerHttpService;
    private final ObjectMapper objectMapper;

    public ChapterEditorAssistService(
            ChapterGenerationService chapterGenerationService,
            ChapterVersionRepository chapterVersionRepository,
            WriterHttpService writerHttpService,
            ObjectMapper objectMapper
    ) {
        this.chapterGenerationService = chapterGenerationService;
        this.chapterVersionRepository = chapterVersionRepository;
        this.writerHttpService = writerHttpService;
        this.objectMapper = objectMapper;
    }

    private String resolveChapterText(String projectId, int chapterNo, String override) {
        String o = override != null ? override.trim() : "";
        if (!o.isBlank()) {
            return o;
        }
        return chapterVersionRepository
                .findFirstByProjectIdAndChapterNoOrderByVersionDesc(projectId, chapterNo)
                .map(v -> {
                    String st = v.getStyledText();
                    if (st != null && !st.isBlank()) {
                        return st;
                    }
                    return v.getChapterText() != null ? v.getChapterText() : "";
                })
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂无本章正文，请先粘贴或生成一版"));
    }

    public JsonNode fanqieEditorReview(String projectId, int chapterNo, String chapterTextOverride) {
        chapterGenerationService.requireProjectStoryAndChapter(projectId, chapterNo);
        String text = resolveChapterText(projectId, chapterNo, chapterTextOverride);
        ObjectNode base = chapterGenerationService.buildWriterPayload(projectId, chapterNo, ChapterGenerateBody.empty());
        base.put("chapterText", text);
        return writerHttpService.postJson("/api/writer/chapters/fanqie-editor-review", base);
    }

    public JsonNode polishWithNotes(
            String projectId,
            int chapterNo,
            String chapterTextOverride,
            String tomatoReview,
            String authorNotes
    ) {
        chapterGenerationService.requireProjectStoryAndChapter(projectId, chapterNo);
        String tr = tomatoReview != null ? tomatoReview.trim() : "";
        String an = authorNotes != null ? authorNotes.trim() : "";
        if (tr.isBlank() && an.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少填写番茄编辑意见或作者补充意见之一");
        }
        String text = resolveChapterText(projectId, chapterNo, chapterTextOverride);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("chapterText", text);
        body.put("tomatoReview", tr);
        body.put("authorNotes", an);
        body.put("projectId", projectId);
        body.put("chapterNo", chapterNo);
        return writerHttpService.postJson("/api/writer/chapters/polish-with-notes", body);
    }
}
