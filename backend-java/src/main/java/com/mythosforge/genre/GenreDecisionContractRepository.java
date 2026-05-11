package com.mythosforge.genre;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Spring Data JPA：按项目查题材列表或最新一条（初始化默认题材）。 */
public interface GenreDecisionContractRepository extends JpaRepository<GenreDecisionContract, String> {

    Optional<GenreDecisionContract> findFirstByProjectIdOrderByCreatedAtDesc(String projectId);

    List<GenreDecisionContract> findByProjectIdOrderByCreatedAtDesc(String projectId);
}
