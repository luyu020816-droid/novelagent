package com.mythosforge.chapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChapterCommitRepository extends JpaRepository<ChapterCommitEntity, String> {

    /**
     * 当前章之前的 accepted commit（按章节号、版本序），用于组装 {@code historySummaries}。
     */
    List<ChapterCommitEntity> findByProjectIdAndChapterNoLessThanAndStatusOrderByChapterNoAscVersionAsc(
            String projectId,
            int chapterNo,
            String status
    );

    /** 全书已定稿导出（按章节号升序）。 */
    List<ChapterCommitEntity> findByProjectIdAndStatusOrderByChapterNoAscVersionAsc(String projectId, String status);
}
