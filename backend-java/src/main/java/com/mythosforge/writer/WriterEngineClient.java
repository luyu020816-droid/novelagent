package com.mythosforge.writer;

import com.mythosforge.writer.dto.WriterEngineStatusResponse;
import com.mythosforge.writer.dto.WriterProbeResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class WriterEngineClient {

    private final RestClient client;

    public WriterEngineClient(@Value("${mythosforge.writer.base-url:http://localhost:8000}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
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
}
