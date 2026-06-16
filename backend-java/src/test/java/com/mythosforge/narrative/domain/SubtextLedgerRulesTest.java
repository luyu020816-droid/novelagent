package com.mythosforge.narrative.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubtextLedgerRulesTest {

    @Test
    void allowsNullSuggested() {
        assertDoesNotThrow(() -> SubtextLedgerRules.validateSuggestedResolveWindow(5, null));
    }

    @Test
    void allowsSameOrLaterChapter() {
        assertDoesNotThrow(() -> SubtextLedgerRules.validateSuggestedResolveWindow(5, 5));
        assertDoesNotThrow(() -> SubtextLedgerRules.validateSuggestedResolveWindow(5, 12));
    }

    @Test
    void rejectsEarlierSuggested() {
        assertThrows(IllegalArgumentException.class, () -> SubtextLedgerRules.validateSuggestedResolveWindow(5, 3));
    }
}
