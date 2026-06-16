package com.mythosforge.dag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.writer.WriterHttpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectDagServiceFallbackTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectDagVersionRepository dagVersionRepository;

    @Mock
    private WriterHttpService writerHttpService;

    private ProjectDagService projectDagService;

    @BeforeEach
    void setUp() {
        projectDagService = new ProjectDagService(
                projectRepository,
                dagVersionRepository,
                writerHttpService,
                new ObjectMapper()
        );
    }

    @Test
    void fetchWriterDefaultDagFallsBackToClasspathWhenWriterDown() throws Exception {
        when(writerHttpService.getJson("/api/writer/dag/default"))
                .thenThrow(new RuntimeException("connection refused"));

        JsonNode dag = projectDagService.fetchWriterDefaultDag();
        assertNotNull(dag);
        assertEquals("dag_default_chapter", dag.get("id").asText());
    }
}
