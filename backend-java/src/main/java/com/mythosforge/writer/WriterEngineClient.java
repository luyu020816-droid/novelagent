package com.mythosforge.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mythosforge.writer.dto.WriterEngineStatusResponse;
import com.mythosforge.writer.dto.WriterProbeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;

/**
 * 出站 HTTP 客户端（Spring {@link RestClient}）：探测 Writer、阻塞 POST 题材/采访/初始化等非 SSE 接口。
 * init-novel 使用更长读超时单独 {@code RestClient}。
 */
@Component
public class WriterEngineClient {

    private static final Logger log = LoggerFactory.getLogger(WriterEngineClient.class);

    private final RestClient client;
    private final RestClient initNovelClient;
    private final RestClient chapterGenerateClient;
    private final ObjectMapper objectMapper;
    private final String writerBaseUrl;

    public WriterEngineClient(
            @Value("${mythosforge.writer.base-url:http://localhost:8000}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this.writerBaseUrl = baseUrl;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setBufferRequestBody(true);
        rf.setConnectTimeout(10_000);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(rf).build();

        SimpleClientHttpRequestFactory rfLong = new SimpleClientHttpRequestFactory();
        rfLong.setBufferRequestBody(true);
        rfLong.setConnectTimeout(10_000);
        rfLong.setReadTimeout(600_000);
        this.initNovelClient = RestClient.builder().baseUrl(baseUrl).requestFactory(rfLong).build();

        SimpleClientHttpRequestFactory rfChapter = new SimpleClientHttpRequestFactory();
        rfChapter.setBufferRequestBody(true);
        rfChapter.setConnectTimeout(30_000);
        rfChapter.setReadTimeout(900_000);
        this.chapterGenerateClient = RestClient.builder().baseUrl(baseUrl).requestFactory(rfChapter).build();

        this.objectMapper = objectMapper;
    }

    public WriterProbeResult fetchHealth() {
        try {
            String body = client.get()
                    .uri("/api/writer/health")
                    .retrieve()
                    .body(String.class);
            return new WriterProbeResult(true, body, null);
        } catch (RestClientException e) {
            return new WriterProbeResult(false, null, e.getMessage());
        }
    }

    public WriterProbeResult fetchTest() {
        try {
            String body = client.post()
                    .uri("/api/writer/test")
                    .retrieve()
                    .body(String.class);
            return new WriterProbeResult(true, body, null);
        } catch (RestClientException e) {
            return new WriterProbeResult(false, null, e.getMessage());
        }
    }

    public WriterEngineStatusResponse probeAll() {
        return new WriterEngineStatusResponse(fetchHealth(), fetchTest());
    }

    public JsonNode postInitNovel(JsonNode body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            String preview = json.length() > 600 ? json.substring(0, 600) + "..." : json;
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            log.debug(
                    "postInitNovel writerBaseUrl={} uri=/api/writer/init-novel bytes={} preview={}",
                    writerBaseUrl,
                    payload.length,
                    preview
            );
            return initNovelClient.post()
                    .uri("/api/writer/init-novel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONNECTION, "close")
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize init-novel request JSON", e);
        }
    }

    public JsonNode postGenreRecommend(JsonNode body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            String preview = json.length() > 600 ? json.substring(0, 600) + "..." : json;
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            log.debug(
                    "postGenreRecommend writerBaseUrl={} uri=/api/writer/genre/recommend bytes={} preview={}",
                    writerBaseUrl,
                    payload.length,
                    preview
            );
            return client.post()
                    .uri("/api/writer/genre/recommend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONNECTION, "close")
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize genre request JSON", e);
        }
    }

    /**
     * 路径 B：多轮互动采访（JSON 请求 / 响应）。
     */
    public JsonNode postGenreInterview(JsonNode body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            log.debug(
                    "postGenreInterview writerBaseUrl={} uri=/api/writer/genre/interview bytes={}",
                    writerBaseUrl,
                    payload.length
            );
            return client.post()
                    .uri("/api/writer/genre/interview")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONNECTION, "close")
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize genre interview JSON", e);
        }
    }

    /**
     * 同步整章 LangGraph（无 SSE）：供 Java 后台任务调用；读超时约 15 分钟。
     */
    public JsonNode postNarrativeSetupPropose(JsonNode body) {
        try {
            byte[] payload = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            return client.post()
                    .uri("/api/writer/setup/narrative-propose")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONNECTION, "close")
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize narrative propose JSON", e);
        }
    }

    public JsonNode postNarrativeSetupRevise(JsonNode body) {
        try {
            byte[] payload = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            return client.post()
                    .uri("/api/writer/setup/narrative-revise")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONNECTION, "close")
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize narrative revise JSON", e);
        }
    }

    public JsonNode postChapterGenerateComplete(JsonNode body, String jobId) {
        try {
            String json = objectMapper.writeValueAsString(body);
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            var spec = chapterGenerateClient.post()
                    .uri("/api/writer/chapters/generate-complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONNECTION, "close");
            if (jobId != null && !jobId.isBlank()) {
                spec = spec.header("X-Generation-Job-Id", jobId);
            }
            return spec.body(payload).retrieve().body(JsonNode.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize chapter generate payload", e);
        }
    }
}
