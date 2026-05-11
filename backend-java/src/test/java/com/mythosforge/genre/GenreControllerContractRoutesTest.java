package com.mythosforge.genre;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mythosforge.genre.dto.GenreContractDetailResponse;
import com.mythosforge.project.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GenreController.class)
@Import(com.mythosforge.genre.GenreControllerContractRoutesTest.ExecutorStubConfig.class)
class GenreControllerContractRoutesTest {

    /** Minimal no-op executor so GenreController can construct (unused by these tests). */
    @org.springframework.boot.test.context.TestConfiguration
    static class ExecutorStubConfig {
        @org.springframework.context.annotation.Bean(name = "applicationTaskExecutor")
        org.springframework.core.task.TaskExecutor applicationTaskExecutor() {
            return Runnable::run;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GenreService genreService;

    @MockBean
    private ProjectService projectService;

    @Test
    void getGenreContract_isDispatchedToController() throws Exception {
        JsonNode raw = objectMapper.readTree("{\"selectedDirection\":{},\"candidateRankings\":[]}");
        when(genreService.getGenreContract(eq("p1"), eq("c1")))
                .thenReturn(new GenreContractDetailResponse("c1", "p1", Instant.parse("2026-01-01T00:00:00Z"), "preference", null, raw));

        mockMvc.perform(get("/api/projects/p1/genre/c1")).andExpect(status().isOk());
    }

    @Test
    void putGenreContract_isDispatchedToController() throws Exception {
        JsonNode raw = objectMapper.readTree("{\"selectedDirection\":{},\"candidateRankings\":[]}");
        when(genreService.updateGenreContract(eq("p1"), eq("c1"), org.mockito.Mockito.any()))
                .thenReturn(new GenreContractDetailResponse("c1", "p1", Instant.parse("2026-01-01T00:00:00Z"), "preference", null, raw));

        mockMvc.perform(
                        put("/api/projects/p1/genre/c1")
                                .contentType("application/json")
                                .content("{\"rawJson\":{\"selectedDirection\":{},\"candidateRankings\":[]}}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteGenreContract_isDispatchedToController() throws Exception {
        org.mockito.Mockito.doNothing().when(genreService).deleteGenreContract(eq("p1"), eq("c1"));

        mockMvc.perform(delete("/api/projects/p1/genre/c1")).andExpect(status().isOk());
    }
}
