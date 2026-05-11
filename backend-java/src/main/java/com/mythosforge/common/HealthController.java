package com.mythosforge.common;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 极简存活探针：{@code GET /api/health}，供编排或人工确认 Java 进程已监听。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping(value = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
    public String health() {
        return "ok";
    }
}
