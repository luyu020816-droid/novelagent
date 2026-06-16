package com.mythosforge.narrative.dto;

/** 结构校验单条结果；severity 为 error 或 warning。 */
public record NarrativeValidationIssue(String code, String message, String severity) {
    public static NarrativeValidationIssue error(String code, String message) {
        return new NarrativeValidationIssue(code, message, "error");
    }

    public static NarrativeValidationIssue warning(String code, String message) {
        return new NarrativeValidationIssue(code, message, "warning");
    }
}
