package com.mythosforge.chapter.dto;

/** POST generate-async 立即返回。 */
public record GenerationJobQueuedResponse(String jobId, String status, String message) {}
