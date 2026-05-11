package com.mythosforge.chapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 可选：用户打回重写意见；rewriteMode=anti_ai 弱化 AI 腔（剧情不变）。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChapterGenerateBody(
        @JsonProperty("userRewriteNotes") String userRewriteNotes,
        @JsonProperty("rewriteMode") String rewriteMode
) {
    public ChapterGenerateBody {
        if (rewriteMode != null && rewriteMode.isBlank()) {
            rewriteMode = null;
        }
    }

    public static ChapterGenerateBody empty() {
        return new ChapterGenerateBody(null, null);
    }
}
