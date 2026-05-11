package com.mythosforge.story;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data JPA：按项目列初始化快照（时间倒序）。 */
public interface StoryContractRepository extends JpaRepository<StoryContractEntity, String> {

    List<StoryContractEntity> findByProjectIdOrderByCreatedAtDesc(String projectId);
}
