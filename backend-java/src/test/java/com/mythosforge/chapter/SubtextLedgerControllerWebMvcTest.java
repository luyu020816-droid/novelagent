package com.mythosforge.chapter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubtextLedgerController.class)
class SubtextLedgerControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubtextLedgerService subtextLedgerService;

    @Test
    void listSubtext_returnsOk() throws Exception {
        SubtextLedgerEntity e = new SubtextLedgerEntity();
        e.setId("e1");
        e.setProjectId("p1");
        e.setChapterNo(2);
        e.setCharacterRef("主角");
        e.setQuestion("他是否知道真相？");
        e.setStatus("pending");
        e.setSuggestedResolveChapter(5);
        e.setImportance("high");
        e.setCreatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        when(subtextLedgerService.listByProject("p1")).thenReturn(List.of(e));

        mockMvc.perform(get("/api/projects/p1/subtext"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("e1"))
                .andExpect(jsonPath("$[0].question").value("他是否知道真相？"));
    }

    @Test
    void createSubtext_returnsOk() throws Exception {
        SubtextLedgerEntity saved = new SubtextLedgerEntity();
        saved.setId("new1");
        saved.setProjectId("p1");
        saved.setChapterNo(3);
        saved.setQuestion("误会何时解除？");
        saved.setStatus("pending");
        saved.setCreatedAt(Instant.parse("2026-01-03T00:00:00Z"));
        when(subtextLedgerService.create(eq("p1"), any())).thenReturn(saved);

        mockMvc.perform(
                        post("/api/projects/p1/subtext")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"chapterNo\":3,\"question\":\"误会何时解除？\",\"suggestedResolveChapter\":8}"
                                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("new1"));
    }

    @Test
    void consumeSubtext_returnsOk() throws Exception {
        SubtextLedgerEntity after = new SubtextLedgerEntity();
        after.setId("e1");
        after.setProjectId("p1");
        after.setChapterNo(1);
        after.setQuestion("q");
        after.setStatus("consumed");
        after.setConsumedAtChapter(4);
        after.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(subtextLedgerService.markConsumed("p1", "e1", 4)).thenReturn(after);

        mockMvc.perform(
                        post("/api/projects/p1/subtext/e1/consume")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"consumedAtChapter\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("consumed"));
    }
}
