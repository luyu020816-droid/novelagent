package com.mythosforge.project.dto;

import com.mythosforge.writer.dto.WriterEngineStatusResponse;

public record ProjectDetailResponse(ProjectResponse project, WriterEngineStatusResponse writerEngine) {
}
