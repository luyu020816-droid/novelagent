package com.mythosforge.narrative;

/**
 * 全书叙事生命周期（对齐 PlotPilot 收敛沙漏四段）。
 */
public enum StoryPhase {
    OPENING("opening", "开局期"),
    DEVELOPMENT("development", "发展期"),
    CONVERGENCE("convergence", "收敛期"),
    FINALE("finale", "终局期");

    private final String jsonValue;
    private final String displayName;

    StoryPhase(String jsonValue, String displayName) {
        this.jsonValue = jsonValue;
        this.displayName = displayName;
    }

    public String jsonValue() {
        return jsonValue;
    }

    public String displayName() {
        return displayName;
    }

    /** 根据全书进度 0.0～1.0 判定阶段。 */
    public static StoryPhase fromProgress(double progress) {
        double p = Math.max(0.0, Math.min(1.0, progress));
        if (p < 0.25) {
            return OPENING;
        }
        if (p < 0.75) {
            return DEVELOPMENT;
        }
        if (p < 0.90) {
            return CONVERGENCE;
        }
        return FINALE;
    }

    public static StoryPhase fromProgress(int chapterNo, int targetChapters) {
        if (targetChapters < 1) {
            return OPENING;
        }
        return fromProgress((double) chapterNo / (double) targetChapters);
    }

    public boolean allowsNewSubtext() {
        return this == OPENING || this == DEVELOPMENT;
    }

    public boolean allowsNewForeshadowing() {
        return allowsNewSubtext();
    }
}
