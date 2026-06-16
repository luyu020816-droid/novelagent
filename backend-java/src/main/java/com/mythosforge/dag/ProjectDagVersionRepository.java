package com.mythosforge.dag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectDagVersionRepository extends JpaRepository<ProjectDagVersionEntity, String> {

    Optional<ProjectDagVersionEntity> findByProjectIdAndActiveTrue(String projectId);

    List<ProjectDagVersionEntity> findByProjectIdOrderByVersionNoDesc(String projectId);

    @Query("SELECT COALESCE(MAX(e.versionNo), 0) FROM ProjectDagVersionEntity e WHERE e.projectId = :projectId")
    int findMaxVersionNo(@Param("projectId") String projectId);

    @Modifying
    @Query("UPDATE ProjectDagVersionEntity e SET e.active = false WHERE e.projectId = :projectId AND e.active = true")
    int deactivateAll(@Param("projectId") String projectId);
}
