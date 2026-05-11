package com.mythosforge.writer.dto;

/** 单次 HTTP 探测结果（健康检查或 test-agent）。 */
public record WriterProbeResult(boolean ok, String responseBody, String error) {
}
