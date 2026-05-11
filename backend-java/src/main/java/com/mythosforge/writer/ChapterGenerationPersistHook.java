package com.mythosforge.writer;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 在 SSE 已转发 {@code chapter_generation_final} 之后执行落库等副作用（emitter 仍可用于追加事件）。 */
@FunctionalInterface
public interface ChapterGenerationPersistHook {

    void onChapterGenerationFinal(JsonNode finalArtifactData, SseEmitter emitter) throws Exception;
}
