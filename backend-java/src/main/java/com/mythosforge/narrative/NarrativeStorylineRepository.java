package com.mythosforge.narrative;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 按项目查询故事线，支持 key 唯一性与 projectId+id 定位。 */
public interface NarrativeStorylineRepository extends JpaRepository<NarrativeStorylineEntity, String> {

    List<NarrativeStorylineEntity> findByProjectIdOrderBySortOrderAscCreatedAtAsc(String projectId);

    boolean existsByProjectIdAndStorylineKey(String projectId, String storylineKey);

    java.util.Optional<NarrativeStorylineEntity> findByProjectIdAndId(String projectId, String id);
}
