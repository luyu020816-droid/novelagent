package com.mythosforge.chapter;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 已定稿 commit 的维护操作（如向量索引重试）。 */
@RestController
@RequestMapping("/api/chapters/commits")
public class ChapterCommitRestController {

    private final ChapterReviewService chapterReviewService;

    public ChapterCommitRestController(ChapterReviewService chapterReviewService) {
        this.chapterReviewService = chapterReviewService;
    }

    @PostMapping("/{commitId}/retry-vector-sync")
    public void retryVectorSync(@PathVariable String commitId) {
        chapterReviewService.retryVectorSyncForCommit(commitId);
    }
}
