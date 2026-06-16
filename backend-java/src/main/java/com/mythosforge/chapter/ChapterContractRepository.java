package com.mythosforge.chapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Spring Data JPA：同一快照下按章节号升序取章纲。 */
public interface ChapterContractRepository extends JpaRepository<ChapterContractEntity, String> {

    List<ChapterContractEntity> findByStoryContractIdOrderByChapterNoAsc(String storyContractId);

    Optional<ChapterContractEntity> findByStoryContractIdAndChapterNo(String storyContractId, int chapterNo);

    List<ChapterContractEntity> findByProjectId(String projectId);

    void deleteByStoryContractId(String storyContractId);
}
