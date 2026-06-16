package com.mythosforge.narrative.domain;

/** PlotPilot 式子文本账本不变量：建议回收章不得早于埋设章。 */
public final class SubtextLedgerRules {

    private SubtextLedgerRules() {}

    public static void validateSuggestedResolveWindow(int plantedChapter, Integer suggestedResolveChapter) {
        if (suggestedResolveChapter == null) {
            return;
        }
        if (suggestedResolveChapter < plantedChapter) {
            throw new IllegalArgumentException(
                    "suggestedResolveChapter (" + suggestedResolveChapter + ") must be >= planted chapter (" + plantedChapter + ")"
            );
        }
    }
}
