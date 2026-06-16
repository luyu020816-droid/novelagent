package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mythosforge.project.Project;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NarrativeStoryAnchorBuilderTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void build_includesProtagonistAndGenre() throws Exception {
    var story = mapper.readTree(
        """
        {"positioning":{"genre":"玄幻","coreHook":"复仇"},"protagonist":{"name":"林凡","desire":"复仇"}}
        """
    );
    Project p = new Project();
    p.setName("测试书");
    String anchor = NarrativeStoryAnchorBuilder.build(story, p);
    assertTrue(anchor.contains("林凡"));
    assertTrue(anchor.contains("玄幻"));
    assertTrue(anchor.length() <= 320);
  }
}
