package com.mythosforge.chapter;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Day 8.5：人工裁定（路径按文档 {@code /api/chapters/versions/{versionId}/…}）。
 */
@RestController
@RequestMapping("/api/chapters/versions")
public class ChapterReviewController {

    private final ChapterReviewService chapterReviewService;

    public ChapterReviewController(ChapterReviewService chapterReviewService) {
        this.chapterReviewService = chapterReviewService;
    }

    @PostMapping("/{versionId}/accept")
    public void accept(@PathVariable String versionId) {
        chapterReviewService.acceptVersion(versionId);
    }

    @PostMapping("/{versionId}/reject")
    public void reject(@PathVariable String versionId) {
        chapterReviewService.rejectVersion(versionId);
    }

    @DeleteMapping("/{versionId}")
    public void delete(@PathVariable String versionId) {
        chapterReviewService.deletePendingVersion(versionId);
    }
}
