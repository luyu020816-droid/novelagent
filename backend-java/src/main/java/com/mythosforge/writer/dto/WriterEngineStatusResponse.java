package com.mythosforge.writer.dto;

/** 详情页组装：Writer {@code /health} + {@code /test} 两次探测。 */
public record WriterEngineStatusResponse(WriterProbeResult health, WriterProbeResult test) {
}
