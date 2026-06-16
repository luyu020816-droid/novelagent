package com.mythosforge.chapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChapterVersionRepository extends JpaRepository<ChapterVersionEntity, String> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "DELETE FROM ChapterVersionEntity v WHERE v.projectId = :projectId AND v.chapterNo = :chapterNo "
                    + "AND v.status <> :accepted"
    )
    int deleteNonAcceptedByProjectIdAndChapterNo(
            @Param("projectId") String projectId,
            @Param("chapterNo") int chapterNo,
            @Param("accepted") String accepted
    );

    @Query("SELECT COALESCE(MAX(v.version), 0) FROM ChapterVersionEntity v WHERE v.projectId = :projectId AND v.chapterNo = :chapterNo")
    int findMaxVersion(@Param("projectId") String projectId, @Param("chapterNo") int chapterNo);

    Optional<ChapterVersionEntity> findFirstByProjectIdAndChapterNoOrderByVersionDesc(
            String projectId,
            int chapterNo
    );

    List<ChapterVersionEntity> findByProjectId(String projectId);

    long countByProjectId(String projectId);
}
