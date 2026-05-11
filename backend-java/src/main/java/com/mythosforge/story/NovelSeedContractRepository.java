package com.mythosforge.story;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data JPA：按项目取最新 Novel Seed（若业务需要）。 */
public interface NovelSeedContractRepository extends JpaRepository<NovelSeedContract, String> {

    Optional<NovelSeedContract> findFirstByProjectIdOrderByCreatedAtDesc(String projectId);
}
