package com.mythosforge.chapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

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

    /** 某一章最新一条已定稿 commit（用于「上一章摘要」）。 */
    Optional<ChapterCommitEntity> findFirstByProjectIdAndChapterNoAndStatusOrderByVersionDesc(
            String projectId,
            int chapterNo,
            String status
    );

    List<ChapterCommitEntity> findByProjectIdAndChapterNoAndVersion(String projectId, int chapterNo, int version);

    long countByProjectIdAndStatus(String projectId, String status);

    @Query(
            "SELECT COALESCE(MAX(c.chapterNo), 0) FROM ChapterCommitEntity c "
                    + "WHERE c.projectId = :projectId AND c.status = :status"
    )
    int findMaxChapterNoByProjectIdAndStatus(@Param("projectId") String projectId, @Param("status") String status);
}
