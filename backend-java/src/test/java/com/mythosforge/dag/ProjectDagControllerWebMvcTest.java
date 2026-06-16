package com.mythosforge.dag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.writer.WriterHttpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectDagController.class)
class ProjectDagControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectDagService projectDagService;

    @MockBean
    private ProjectRepository projectRepository;

    @MockBean
    private ProjectDagVersionRepository dagVersionRepository;

    @MockBean
    private WriterHttpService writerHttpService;

    @Test
    void getActiveReturnsDag() throws Exception {
        ProjectDagVersionEntity e = new ProjectDagVersionEntity();
        e.setId("dag-1");
        e.setProjectId("p1");
        e.setVersionNo(1);
        e.setLabel("v1");
        e.setActive(true);
        ObjectNode dag = objectMapper.createObjectNode();
        dag.put("id", "dag_default_chapter");
        e.setDagJson(dag);

        when(projectDagService.getActiveOrDefault("p1")).thenReturn(e);

        mockMvc.perform(get("/api/projects/p1/dag/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dag.id").value("dag_default_chapter"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void saveActiveDag() throws Exception {
        ProjectDagVersionEntity saved = new ProjectDagVersionEntity();
        saved.setId("dag-2");
        saved.setProjectId("p1");
        saved.setVersionNo(2);
        saved.setLabel("custom");
        saved.setActive(true);
        saved.setDagJson(objectMapper.createObjectNode().put("id", "custom"));

        when(projectDagService.saveActiveDag(eq("p1"), any(), eq("custom"))).thenReturn(saved);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("label", "custom");
        body.set("dag", objectMapper.createObjectNode().put("id", "custom"));

        mockMvc.perform(
                put("/api/projects/p1/dag/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString())
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(2));
    }
}
