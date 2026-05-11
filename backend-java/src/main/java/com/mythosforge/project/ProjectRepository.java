package com.mythosforge.project;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA：主键为 {@link Project#getId() String}，无手写 SQL。
 */
public interface ProjectRepository extends JpaRepository<Project, String> {
}
