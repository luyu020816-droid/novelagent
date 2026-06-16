package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mythosforge.chapter.SubtextLedgerEntity;
import com.mythosforge.chapter.SubtextLedgerRepository;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChapterObligationsServiceTest {

  @Mock
  NarrativeStorylineRepository storylineRepository;
  @Mock
  NarrativeConfluenceRepository confluenceRepository;
  @Mock
  SubtextLedgerRepository subtextLedgerRepository;
  @Mock
  ProjectRepository projectRepository;
  @Mock
  NarrativeContextSlotsService narrativeContextSlotsService;

  private final ObjectMapper mapper = new ObjectMapper();

  private ChapterObligationsService service() {
    return new ChapterObligationsService(
        storylineRepository,
        confluenceRepository,
        subtextLedgerRepository,
        projectRepository,
        narrativeContextSlotsService,
        mapper,
        1);
  }

  @Test
  void build_includesDueMilestonesAndSubtextWindow() {
    ChapterObligationsService service = service();
    Project p = new Project();
    p.setId("p1");
    p.setTargetChapters(100);
    when(projectRepository.findById("p1")).thenReturn(Optional.of(p));

    NarrativeStorylineEntity sl = new NarrativeStorylineEntity();
    sl.setId("sl1");
    sl.setProjectId("p1");
    sl.setStorylineKey("main");
    sl.setTitle("主线");
    sl.setStatus("ACTIVE");
    sl.setCurrentMilestoneIndex(0);
    ArrayNode ms = mapper.createArrayNode();
    ms.addObject().put("order", 1).put("title", "节拍A").put("targetChapter", 5);
    sl.setMilestonesJson(ms);
    when(storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc("p1")).thenReturn(List.of(sl));
    when(confluenceRepository.findByProjectIdAndTargetChapterAndResolvedIsFalse(eq("p1"), anyInt()))
        .thenReturn(List.of());

    SubtextLedgerEntity st = new SubtextLedgerEntity();
    st.setId("st1");
    st.setChapterNo(3);
    st.setQuestion("谁下的手？");
    st.setStatus("pending");
    st.setSuggestedResolveChapter(5);
    when(subtextLedgerRepository.findByProjectIdAndStatusOrderByChapterNoAsc("p1", "pending"))
        .thenReturn(List.of(st));

    var root = service.buildChapterObligations("p1", 5);
    assertEquals(1, root.get("dueMilestonesThisChapter").size());
    assertEquals(1, root.get("dueSubtextInWindow").size());
    assertTrue(root.get("summaryLine").asText().contains("窗口内子文本"));
    assertEquals("opening", root.get("storyPhase").asText());
    assertTrue(root.has("phaseRules"));
    assertTrue(root.has("narrativePromptLines"));
    assertTrue(root.has("continuityBrief"));
    assertTrue(root.get("continuityBrief").asText().contains("谁下的手？"));
  }
}
