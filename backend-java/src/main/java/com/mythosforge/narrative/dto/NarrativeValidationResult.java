package com.mythosforge.narrative.dto;

import java.util.List;

/** GET /narrative/validate 的响应；errors 非空时 ok() 为 false。 */
public record NarrativeValidationResult(List<NarrativeValidationIssue> errors, List<NarrativeValidationIssue> warnings) {
    /** 是否无阻断级错误。 */
    public boolean ok() {
        return errors == null || errors.isEmpty();
    }
}
