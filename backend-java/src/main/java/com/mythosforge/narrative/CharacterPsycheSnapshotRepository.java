package com.mythosforge.narrative;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CharacterPsycheSnapshotRepository extends JpaRepository<CharacterPsycheSnapshotEntity, String> {

    List<CharacterPsycheSnapshotEntity> findByProjectIdAndChapterNoLessThanEqualOrderByChapterNoDescCharacterRefAsc(
            String projectId,
            int chapterNo
    );

    Optional<CharacterPsycheSnapshotEntity> findByProjectIdAndChapterNoAndCharacterRef(
            String projectId,
            int chapterNo,
            String characterRef
    );
}
