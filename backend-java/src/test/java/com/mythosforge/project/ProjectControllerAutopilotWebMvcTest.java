package com.mythosforge.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mythosforge.project.dto.AutopilotSettingsBody;
import com.mythosforge.project.dto.NarrativeDomainPatchBody;
import com.mythosforge.project.dto.ProjectResponse;
import com.mythosforge.writer.WriterEngineClient;
import com.mythosforge.writer.WriterHttpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
class ProjectControllerAutopilotWebMvcTest {

    private static final Instant T0 = Instant.parse("2026-03-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private WriterEngineClient writerEngineClient;

    @MockBean
    private WriterHttpService writerHttpService;

    @MockBean
    private ProjectBookExportService projectBookExportService;

    @MockBean
    private EntityReplaceService entityReplaceService;

    private static ProjectResponse sampleProject(String id) {
        return new ProjectResponse(
                id,
                "Novel",
                "zh-CN",
                100,
                0,
                "created",
                null,
                "ACTIVE",
                null,
                "AUTO_QUEUE_GENERATE",
                "NEVER",
                20,
                0,
                false,
                null,
                true,
                null,
                null,
                T0,
                T0
        );
    }

    @Test
    void postAutopilotSettings_ok() throws Exception {
        ProjectResponse updated = new ProjectResponse(
                "p1",
                "Novel",
                "zh-CN",
                100,
                0,
                "created",
                null,
                "ACTIVE",
                null,
                "MANUAL",
                "NEVER",
                20,
                0,
                false,
                null,
                true,
                null,
                null,
                T0,
                T0
        );
        when(projectService.updateAutopilotSettings(eq("p1"), any(AutopilotSettingsBody.class))).thenReturn(updated);

        mockMvc.perform(
                        post("/api/projects/p1/autopilot/settings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"autopilotMode\":\"MANUAL\",\"autoAcceptPolicy\":\"NEVER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autopilotMode").value("MANUAL"));
    }

    @Test
    void postEmergencyPause_ok() throws Exception {
        when(projectService.pauseAutopilot(eq("p1"), eq("ui_stop"))).thenReturn(sampleProject("p1"));

        mockMvc.perform(
                        post("/api/projects/p1/autopilot/emergency-pause")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"ui_stop\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void postNarrativeDomain_ok() throws Exception {
        when(projectService.patchNarrativeDomain(eq("p1"), any(NarrativeDomainPatchBody.class)))
                .thenReturn(sampleProject("p1"));

        mockMvc.perform(
                        post("/api/projects/p1/narrative-domain")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"narrativeDomainJson\":{\"storylines\":[]}}"))
                .andExpect(status().isOk());
    }
}
