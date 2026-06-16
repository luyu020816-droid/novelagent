package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NarrativePromptLinesBuilderTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void darkRevealEarly_showsGuardsOnly() throws Exception {
    NarrativeStorylineEntity dark = new NarrativeStorylineEntity();
    dark.setId("dark1");
    dark.setStorylineKey("dark");
    dark.setTitle("暗线");
    dark.setStatus("ACTIVE");
    dark.setStorylineRole("DARK");

    NarrativeConfluenceEntity cp = new NarrativeConfluenceEntity();
    cp.setId("cf1");
    cp.setPrimaryStorylineId("main1");
    cp.setSecondaryStorylineId("dark1");
    cp.setTargetChapter(20);
    cp.setConfluenceType("reveal");
    cp.setResolved(false);
    cp.setPreRevealHint("勿提前揭露");
    cp.setBehaviorGuards(mapper.readTree("[\"不得直呼真凶\"]"));

    List<String> lines = NarrativePromptLinesBuilder.build(
            5,
            List.of(dark),
            List.of(cp),
            Map.of("dark1", dark)
    );
    String joined = String.join("\n", lines);
    assertTrue(joined.contains("暗线"));
    assertTrue(joined.contains("禁忌"));
    assertTrue(joined.contains("勿提前揭露"));
  }
}
