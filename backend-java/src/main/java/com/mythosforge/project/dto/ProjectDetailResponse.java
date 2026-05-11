package com.mythosforge.project.dto;

import com.mythosforge.writer.dto.WriterEngineStatusResponse;

/** 详情页：项目 + Writer health/test 探测结果。 */
public record ProjectDetailResponse(ProjectResponse project, WriterEngineStatusResponse writerEngine) {
}
