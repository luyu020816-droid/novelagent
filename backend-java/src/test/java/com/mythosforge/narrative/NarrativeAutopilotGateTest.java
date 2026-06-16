package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.chapter.ChapterVersionEntity;
import com.mythosforge.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NarrativeAutopilotGateTest {

  @Mock
  ChapterObligationsService chapterObligationsService;

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void allows_when_fulfillment_pass_and_no_overdue() {
    Project p = new Project();
    p.setId("p1");
    ChapterVersionEntity ver = new ChapterVersionEntity();
    ver.setChapterNo(5);
    ObjectNode report = mapper.createObjectNode();
    report.put("overallPass", true);
    ver.setFulfillmentReportJson(report);

    when(chapterObligationsService.hasOverdueSubtext("p1", 5)).thenReturn(false);

    assertTrue(NarrativeAutopilotGate.allows(p, ver, chapterObligationsService, mapper, 1));
  }

  @Test
  void blocks_on_fulfillment_fail_even_if_confluence_chapter() {
    Project p = new Project();
    p.setId("p1");
    ChapterVersionEntity ver = new ChapterVersionEntity();
    ver.setChapterNo(5);
    ObjectNode report = mapper.createObjectNode();
    report.put("overallPass", false);
    ver.setFulfillmentReportJson(report);

    when(chapterObligationsService.hasOverdueSubtext("p1", 5)).thenReturn(false);

    assertFalse(NarrativeAutopilotGate.allows(p, ver, chapterObligationsService, mapper, 1));
  }

  @Test
  void criticNarrativeDimensionOk_false_when_dimension_fail() throws Exception {
    String json =
        """
        {"dimensions":[{"id":"narrative_obligations","ok":false,"note":"未落实汇合"}]}
        """;
    assertFalse(NarrativeAutopilotGate.criticNarrativeDimensionOk(mapper.readTree(json)));
  }
}
