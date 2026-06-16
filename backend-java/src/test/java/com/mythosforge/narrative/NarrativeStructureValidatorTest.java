package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NarrativeStructureValidatorTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void assertNoParentCycle_rejectsSelfParent() {
    NarrativeStorylineEntity s = new NarrativeStorylineEntity();
    s.setId("a");
    assertThrows(
        IllegalArgumentException.class,
        () -> NarrativeStructureValidator.assertNoParentCycle(List.of(s), "a", "a")
    );
  }

  @Test
  void milestoneSchema_rejectsDuplicateOrder() {
    ArrayNode arr = mapper.createArrayNode();
    arr.addObject().put("order", 1);
    arr.addObject().put("order", 1);
    assertThrows(IllegalArgumentException.class, () -> MilestoneSchemaValidator.validate(arr));
  }

  @Test
  void milestoneSchema_acceptsValid() {
    ArrayNode arr = mapper.createArrayNode();
    arr.addObject().put("order", 1).put("title", "x").put("targetChapter", 3);
    assertDoesNotThrow(() -> MilestoneSchemaValidator.validate(arr));
  }
}
