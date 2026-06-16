package com.mythosforge.chapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GenerationJobRepository extends JpaRepository<GenerationJobEntity, String> {

    @Query(
            "SELECT CASE WHEN COUNT(j) > 0 THEN true ELSE false END FROM GenerationJobEntity j "
                    + "WHERE j.projectId = :projectId AND j.chapterNo = :chapterNo AND j.status IN :statuses"
    )
    boolean existsByProjectIdAndChapterNoAndStatusIn(
            @Param("projectId") String projectId,
            @Param("chapterNo") int chapterNo,
            @Param("statuses") Collection<String> statuses
    );

    Optional<GenerationJobEntity> findTopByProjectIdAndChapterNoOrderByCreatedAtDesc(String projectId, int chapterNo);

    List<GenerationJobEntity> findTop20ByProjectIdAndChapterNoOrderByCreatedAtDesc(String projectId, int chapterNo);

    List<GenerationJobEntity> findByProjectIdOrderByCreatedAtDesc(String projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    void deleteByProjectIdAndChapterNo(String projectId, int chapterNo);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    void deleteByChapterVersionId(String chapterVersionId);
}
