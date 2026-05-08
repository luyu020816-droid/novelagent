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

@Component
public class WriterEngineClient {

    private static final Logger log = LoggerFactory.getLogger(WriterEngineClient.class);

    private final RestClient client;
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
}
