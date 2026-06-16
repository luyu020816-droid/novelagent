package com.mythosforge.chapter;

import com.mythosforge.chapter.dto.SubtextLedgerCreateRequest;
import com.mythosforge.narrative.NarrativePhaseGuard;
import com.mythosforge.narrative.domain.SubtextLedgerRules;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class SubtextLedgerService {

    private final SubtextLedgerRepository subtextLedgerRepository;
    private final ProjectRepository projectRepository;
    private final NarrativePhaseGuard narrativePhaseGuard;

    public SubtextLedgerService(
            SubtextLedgerRepository subtextLedgerRepository,
            ProjectRepository projectRepository,
            NarrativePhaseGuard narrativePhaseGuard
    ) {
        this.subtextLedgerRepository = subtextLedgerRepository;
        this.projectRepository = projectRepository;
        this.narrativePhaseGuard = narrativePhaseGuard;
    }

    @Transactional
    public SubtextLedgerEntity create(String projectId, SubtextLedgerCreateRequest req) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        narrativePhaseGuard.assertAllowNewSubtext(project, req.chapterNo());
        try {
            SubtextLedgerRules.validateSuggestedResolveWindow(req.chapterNo(), req.suggestedResolveChapter());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        SubtextLedgerEntity e = new SubtextLedgerEntity();
        e.setId(UUID.randomUUID().toString().replace("-", ""));
        e.setProjectId(projectId);
        e.setChapterNo(req.chapterNo());
        e.setCharacterRef(req.characterRef());
        e.setQuestion(req.question().trim());
        e.setStatus("pending");
        e.setSuggestedResolveChapter(req.suggestedResolveChapter());
        String imp = req.importance();
        if (imp != null && !imp.isBlank()) {
            e.setImportance(imp.trim());
        }
        return subtextLedgerRepository.save(e);
    }

    public List<SubtextLedgerEntity> listByProject(String projectId) {
        requireProject(projectId);
        return subtextLedgerRepository.findByProjectIdOrderByChapterNoAscCreatedAtAsc(projectId);
    }

    @Transactional
    public SubtextLedgerEntity markConsumed(String projectId, String entryId, int consumedAtChapter) {
        requireProject(projectId);
        SubtextLedgerEntity e = subtextLedgerRepository.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!projectId.equals(e.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        e.setStatus("consumed");
        e.setConsumedAtChapter(consumedAtChapter);
        return subtextLedgerRepository.save(e);
    }

    private void requireProject(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
