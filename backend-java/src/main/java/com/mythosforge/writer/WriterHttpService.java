package com.mythosforge.writer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Writer 普通 JSON API（非 SSE），如同步调 summarize。 */
@Service
public class WriterHttpService {

    private final RestClient writerRestClient;
    private final ObjectMapper objectMapper;

    public WriterHttpService(
            ObjectMapper objectMapper,
            @Value("${mythosforge.writer.base-url:http://127.0.0.1:8000}") String writerBaseUrl
    ) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setBufferRequestBody(true);
        rf.setConnectTimeout(10_000);
        rf.setReadTimeout(600_000);
        this.writerRestClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(writerBaseUrl))
                .requestFactory(rf)
                .build();
    }

    public JsonNode getJson(String path) {
        String raw = writerRestClient.get()
                .uri(path)
                .retrieve()
                .body(String.class);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Writer returned empty JSON body for GET " + path);
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Writer JSON parse failed: " + e.getMessage(), e);
        }
    }

    public JsonNode postJson(String path, JsonNode body) {
        String raw = writerRestClient.post()
                .uri(path)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body.toString())
                .retrieve()
                .body(String.class);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Writer returned empty JSON body for " + path);
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Writer JSON parse failed: " + e.getMessage(), e);
        }
    }

    private static String trimTrailingSlash(String base) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }
}
