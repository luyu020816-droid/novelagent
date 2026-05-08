package com.mythosforge.writer.dto;

public record WriterEngineStatusResponse(WriterProbeResult health, WriterProbeResult test) {
}
