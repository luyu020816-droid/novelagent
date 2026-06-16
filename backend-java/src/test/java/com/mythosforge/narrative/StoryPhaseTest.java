package com.mythosforge.narrative;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryPhaseTest {

  @Test
  void fromProgress_boundaries() {
    assertEquals(StoryPhase.OPENING, StoryPhase.fromProgress(0.0));
    assertEquals(StoryPhase.OPENING, StoryPhase.fromProgress(0.24));
    assertEquals(StoryPhase.DEVELOPMENT, StoryPhase.fromProgress(0.25));
    assertEquals(StoryPhase.DEVELOPMENT, StoryPhase.fromProgress(0.74));
    assertEquals(StoryPhase.CONVERGENCE, StoryPhase.fromProgress(0.75));
    assertEquals(StoryPhase.CONVERGENCE, StoryPhase.fromProgress(0.89));
    assertEquals(StoryPhase.FINALE, StoryPhase.fromProgress(0.90));
  }

  @Test
  void allowsNewSubtext_onlyEarlyPhases() {
    assertTrue(StoryPhase.OPENING.allowsNewSubtext());
    assertTrue(StoryPhase.DEVELOPMENT.allowsNewSubtext());
    assertFalse(StoryPhase.CONVERGENCE.allowsNewSubtext());
    assertFalse(StoryPhase.FINALE.allowsNewSubtext());
  }

  @Test
  void policy_forChapter_includesGuidance() {
    StoryPhasePolicy p = StoryPhasePolicy.forChapter(80, 100);
    assertEquals(StoryPhase.CONVERGENCE, p.phase());
    assertFalse(p.allowNewSubtext());
    assertTrue(p.guidanceLine().contains("收敛"));
  }
}
