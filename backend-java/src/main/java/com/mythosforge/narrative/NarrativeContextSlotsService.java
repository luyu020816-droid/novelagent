package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.chapter.MemorySummaryEntity;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.story.StoryContractEntity;
import com.mythosforge.story.StoryContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PlotPilot A/B 上下文槽：STORY_ANCHOR、SCARS、DEBT_DUE、CAUSAL_CHAINS（写入本章任务单）。
 */
@Service
public class NarrativeContextSlotsService {

    private final ProjectRepository projectRepository;
    private final StoryContractRepository storyContractRepository;
    private final NarrativeDebtService narrativeDebtService;
    private final NarrativeCausalEdgeService narrativeCausalEdgeService;
    private final CharacterPsycheService characterPsycheService;
    private final ObjectMapper objectMapper;

    public NarrativeContextSlotsService(
            ProjectRepository projectRepository,
            StoryContractRepository storyContractRepository,
            NarrativeDebtService narrativeDebtService,
            NarrativeCausalEdgeService narrativeCausalEdgeService,
            CharacterPsycheService characterPsycheService,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.storyContractRepository = storyContractRepository;
        this.narrativeDebtService = narrativeDebtService;
        this.narrativeCausalEdgeService = narrativeCausalEdgeService;
        this.characterPsycheService = characterPsycheService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enrichChapterObligations(ObjectNode root, String projectId, int chapterNo) {
        narrativeDebtService.syncFromProjectSources(projectId, chapterNo);
        Project project = projectRepository.findById(projectId).orElse(null);
        JsonNode storyJson = resolveStoryJson(project);

        String anchor = NarrativeStoryAnchorBuilder.build(storyJson, project);
        if (!anchor.isBlank()) {
            root.put("storyAnchor", anchor);
        }

        List<MemorySummaryEntity> mems = characterPsycheService.recentMemoriesForScars(projectId, chapterNo - 1);
        String scars = NarrativeScarsAndMotivationsBuilder.build(storyJson, mems, 1200);
        if (!scars.isBlank()) {
            root.put("scarsAndMotivations", scars);
        }

        var debts = narrativeDebtService.listOpenDebts(projectId);
        root.set("openNarrativeDebts", narrativeDebtService.openDebtsJson(projectId, chapterNo));
        String debtBlock = narrativeDebtService.formatDebtDueBlock(debts, chapterNo);
        if (!debtBlock.isBlank()) {
            root.put("debtDueBlock", debtBlock);
        }

        var edges = narrativeCausalEdgeService.listOpen(projectId);
        root.set("openCausalEdges", narrativeCausalEdgeService.openEdgesJson(projectId, chapterNo));
        String causalBlock = narrativeCausalEdgeService.formatCausalChainsBlock(edges, chapterNo);
        if (!causalBlock.isBlank()) {
            root.put("causalChainsBlock", causalBlock);
        }
    }

    private JsonNode resolveStoryJson(Project project) {
        if (project == null) {
            return objectMapper.createObjectNode();
        }
        String sid = project.getSelectedStoryContractId();
        if (sid == null || sid.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return storyContractRepository.findById(sid)
                .map(StoryContractEntity::getRawJson)
                .orElse(objectMapper.createObjectNode());
    }
}
