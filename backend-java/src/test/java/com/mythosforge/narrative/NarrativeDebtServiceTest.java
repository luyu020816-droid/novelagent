package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mythosforge.chapter.SubtextLedgerEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NarrativeDebtServiceTest {

  @Mock
  NarrativeDebtRepository debtRepository;
  @Mock
  com.mythosforge.chapter.SubtextLedgerRepository subtextLedgerRepository;
  @Mock
  NarrativeConfluenceRepository confluenceRepository;
  @Mock
  NarrativeStorylineRepository storylineRepository;

  private final ObjectMapper mapper = new ObjectMapper();

  private NarrativeDebtService service() {
    return new NarrativeDebtService(
        debtRepository,
        subtextLedgerRepository,
        confluenceRepository,
        storylineRepository,
        mapper);
  }

  @Test
  void formatDebtDueBlock_listsOverdue() {
    NarrativeDebtEntity d = new NarrativeDebtEntity();
    d.setDebtType("subtext");
    d.setDescription("谁下的手？");
    d.setPlantedChapter(3);
    d.setDueChapter(5);
    d.setImportance(3);
    String block = service().formatDebtDueBlock(List.of(d), 6);
    assertTrue(block.contains("叙事备忘"));
    assertTrue(block.contains("逾期"));
    assertTrue(block.contains("谁下的手"));
  }

  @Test
  void syncFromProjectSources_upsertsSubtextDebt() {
    SubtextLedgerEntity st = new SubtextLedgerEntity();
    st.setId("st1");
    st.setChapterNo(2);
    st.setQuestion("秘密是什么？");
    st.setStatus("pending");
    st.setSuggestedResolveChapter(4);
    when(subtextLedgerRepository.findByProjectIdAndStatusOrderByChapterNoAsc("p1", "pending"))
        .thenReturn(List.of(st));
    when(confluenceRepository.findByProjectIdOrderByTargetChapterAscCreatedAtAsc("p1"))
        .thenReturn(List.of());
    when(storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc("p1"))
        .thenReturn(List.of());
    when(debtRepository.findByProjectIdAndSourceRef("p1", "subtext:st1"))
        .thenReturn(Optional.empty());
    when(debtRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service().syncFromProjectSources("p1", 5);

    ArgumentCaptor<NarrativeDebtEntity> cap = ArgumentCaptor.forClass(NarrativeDebtEntity.class);
    verify(debtRepository).save(cap.capture());
    assertEquals("subtext", cap.getValue().getDebtType());
    assertEquals("秘密是什么？", cap.getValue().getDescription());
  }
}
