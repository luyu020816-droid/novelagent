package com.mythosforge.narrative;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 汇合点仓储：按章查询未 resolved 列表；删除故事线时批量清理引用。
 */
public interface NarrativeConfluenceRepository extends JpaRepository<NarrativeConfluenceEntity, String> {

    List<NarrativeConfluenceEntity> findByProjectIdOrderByTargetChapterAscCreatedAtAsc(String projectId);

    List<NarrativeConfluenceEntity> findByProjectIdAndTargetChapterAndResolvedIsFalse(
            String projectId,
            int targetChapter
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "DELETE FROM NarrativeConfluenceEntity c WHERE c.projectId = :pid AND "
                    + "(c.primaryStorylineId = :sid OR c.secondaryStorylineId = :sid)"
    )
    void deleteAllReferencingStoryline(@Param("pid") String projectId, @Param("sid") String storylineId);
}
