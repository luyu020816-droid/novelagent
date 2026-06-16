package com.mythosforge.autopilot;

/** 全书自动驾驶模式（存 projects.autopilot_mode）。 */
public final class AutopilotModes {

    public static final String MANUAL = "MANUAL";
    public static final String AUTO_QUEUE_GENERATE = "AUTO_QUEUE_GENERATE";
    public static final String FULL_UNATTENDED = "FULL_UNATTENDED";

    private AutopilotModes() {}

    public static boolean isKnown(String v) {
        return MANUAL.equals(v) || AUTO_QUEUE_GENERATE.equals(v) || FULL_UNATTENDED.equals(v);
    }
}
