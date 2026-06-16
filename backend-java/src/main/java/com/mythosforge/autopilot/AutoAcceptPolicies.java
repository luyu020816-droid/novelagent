package com.mythosforge.autopilot;

/** 自动定稿策略（存 projects.auto_accept_policy）。 */
public final class AutoAcceptPolicies {

    public static final String NEVER = "NEVER";
    public static final String CRITIC_PASS = "CRITIC_PASS";
    public static final String CRITIC_AND_METRICS = "CRITIC_AND_METRICS";
    /** Critic 通过 + 叙事任务单硬条件（汇合/逾期子文本/履约报告） */
    public static final String CRITIC_PASS_AND_NARRATIVE = "CRITIC_PASS_AND_NARRATIVE";

    private AutoAcceptPolicies() {}

    public static boolean isKnown(String v) {
        return NEVER.equals(v)
                || CRITIC_PASS.equals(v)
                || CRITIC_AND_METRICS.equals(v)
                || CRITIC_PASS_AND_NARRATIVE.equals(v);
    }
}
