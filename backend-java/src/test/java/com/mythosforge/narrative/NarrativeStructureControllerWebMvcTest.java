package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.narrative.dto.NarrativeValidationIssue;
import com.mythosforge.narrative.dto.NarrativeValidationResult;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.writer.WriterEngineClient;
import com.mythosforge.writer.WriterHttpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NarrativeStructureController.class)
class NarrativeStructureControllerWebMvcTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private NarrativeStructureService narrativeStructureService;

  @MockBean
  private NarrativeDomainBridgeService narrativeDomainBridgeService;

  @MockBean
  private ProjectRepository projectRepository;

  @MockBean
  private NarrativeDebtService narrativeDebtService;

  @MockBean
  private NarrativeCausalEdgeService narrativeCausalEdgeService;

  @MockBean
  private WriterEngineClient writerEngineClient;

  @MockBean
  private WriterHttpService writerHttpService;

  @Test
  void chapterObligationsPreview_returnsJson() throws Exception {
    ObjectNode ob = objectMapper.createObjectNode();
    ob.put("summaryLine", "test");
    when(narrativeStructureService.chapterObligationsPreview(eq("p1"), eq(3))).thenReturn(ob);

    mockMvc.perform(get("/api/projects/p1/narrative/chapter-obligations-preview").param("chapterNo", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summaryLine").value("test"));
  }

  @Test
  void validate_returnsResult() throws Exception {
    when(narrativeStructureService.validateProject("p1"))
        .thenReturn(new NarrativeValidationResult(List.of(), List.of(NarrativeValidationIssue.warning("w", "warn"))));

    mockMvc.perform(get("/api/projects/p1/narrative/validate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.warnings.length()").value(1));
  }
}
