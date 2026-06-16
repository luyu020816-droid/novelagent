package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mythosforge.project.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectNarrativeMetricsController.class)
class ProjectNarrativeMetricsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectRepository projectRepository;

    @MockBean
    private ChapterNarrativeMetricsRepository chapterNarrativeMetricsRepository;

    @Test
    void listMetrics_projectMissing_returns404() throws Exception {
        when(projectRepository.existsById("missing")).thenReturn(false);
        mockMvc.perform(get("/api/projects/missing/narrative-metrics")).andExpect(status().isNotFound());
    }

    @Test
    void listMetrics_returnsRows() throws Exception {
        when(projectRepository.existsById("p1")).thenReturn(true);
        ChapterNarrativeMetricsEntity m = new ChapterNarrativeMetricsEntity();
        m.setId("m1");
        m.setProjectId("p1");
        m.setChapterNo(2);
        m.setCommitId("c1");
        m.setTensionScore(6.5);
        m.setStyleSimilarity(0.82);
        JsonNode raw = objectMapper.readTree("{\"stub\":true}");
        m.setRawJson(raw);
        m.setCreatedAt(Instant.parse("2026-02-01T12:00:00Z"));
        when(chapterNarrativeMetricsRepository.findTop200ByProjectIdOrderByChapterNoDescCreatedAtDesc("p1"))
                .thenReturn(List.of(m));

        mockMvc.perform(get("/api/projects/p1/narrative-metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chapterNo").value(2))
                .andExpect(jsonPath("$[0].tensionScore").value(6.5))
                .andExpect(jsonPath("$[0].styleSimilarity").value(0.82));
    }
}
