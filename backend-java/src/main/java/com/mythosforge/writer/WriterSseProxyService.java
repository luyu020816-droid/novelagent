package com.mythosforge.writer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

/**
 * 消费 Writer 的 {@code text/event-stream}：按行解析 SSE，写入 {@link SseEmitter}，并在收到成品 JSON 时回调 {@code onArtifact}。
 */
@Service
public class WriterSseProxyService {

    private final RestClient writerRestClient;
    private final ObjectMapper objectMapper;

    public WriterSseProxyService(
            ObjectMapper objectMapper,
            @Value("${mythosforge.writer.base-url:http://127.0.0.1:8000}") String writerBaseUrl
    ) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setBufferRequestBody(true);
        rf.setConnectTimeout(10_000);
        rf.setReadTimeout(900_000);
        this.writerRestClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(writerBaseUrl))
                .requestFactory(rf)
                .build();
    }

    /**
     * POST JSON body to Writer SSE endpoint and forward events to the browser emitter.
     */
    public void proxySsePost(
            String path,
            String jsonBody,
            SseEmitter emitter,
            BiConsumer<String, JsonNode> onArtifact
    ) {
        proxySsePost(path, jsonBody, emitter, onArtifact, null);
    }

    /**
     * 与 {@link #proxySsePost} 相同，额外在转发 {@code chapter_generation_final} artifact 之后调用 {@code persistHook}
     *（此时 SSE 仍未关闭，可继续向客户端推送事件）。
     */
    public void proxySsePost(
            String path,
            String jsonBody,
            SseEmitter emitter,
            BiConsumer<String, JsonNode> onArtifact,
            ChapterGenerationPersistHook persistHook
    ) {
        try {
            writerRestClient.post()
                    .uri(path)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .header(HttpHeaders.CONNECTION, "close")
                    .body(jsonBody)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            String errBody = "";
                            try (InputStream in = response.getBody()) {
                                if (in != null) {
                                    errBody = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                                }
                            }
                            sendSseErrorAndComplete(
                                    emitter,
                                    response.getStatusCode().value(),
                                    errBody
                            );
                            return null;
                        }
                        try (InputStream in = response.getBody()) {
                            if (in == null) {
                                sendSseErrorAndComplete(emitter, 502, "Writer returned empty body");
                                return null;
                            }
                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                                try {
                                    parseAndForward(reader, emitter, onArtifact, persistHook);
                                } catch (Exception ex) {
                                    sendSseErrorAndComplete(emitter, 502, ex.getMessage());
                                    return null;
                                }
                            }
                        }
                        emitter.complete();
                        return null;
                    });
        } catch (Exception e) {
            try {
                ObjectNode err = objectMapper.createObjectNode();
                err.put("message", e.getMessage());
                emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(err)));
            } catch (Exception ignored) {
                // ignore
            }
            emitter.complete();
        }
    }

    private void sendSseErrorAndComplete(SseEmitter emitter, int status, String errBody) {
        try {
            ObjectNode err = objectMapper.createObjectNode();
            err.put("message", "Writer HTTP " + status);
            err.put("body", errBody.length() > 2000 ? errBody.substring(0, 2000) : errBody);
            emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(err)));
        } catch (Exception ignored) {
            // ignore
        }
        emitter.complete();
    }

    private static String trimTrailingSlash(String base) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    private void parseAndForward(
            BufferedReader reader,
            SseEmitter emitter,
            BiConsumer<String, JsonNode> onArtifact,
            ChapterGenerationPersistHook persistHook
    ) throws Exception {
        String eventName = "message";
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("event:")) {
                eventName = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(line.substring(5).trim());
            } else if (line.isEmpty()) {
                if (data.length() > 0) {
                    forwardPayload(data.toString(), eventName, emitter, onArtifact, persistHook);
                    data.setLength(0);
                }
                eventName = "message";
            }
        }
        if (data.length() > 0) {
            forwardPayload(data.toString(), eventName, emitter, onArtifact, persistHook);
        }
    }

    private void forwardPayload(
            String payload,
            String eventName,
            SseEmitter emitter,
            BiConsumer<String, JsonNode> onArtifact,
            ChapterGenerationPersistHook persistHook
    ) throws Exception {
        emitter.send(SseEmitter.event().name(eventName).data(payload));
        if (!"artifact".equals(eventName) || onArtifact == null) {
            return;
        }
        JsonNode node = objectMapper.readTree(payload);
        JsonNode inner = node.get("data");
        String kind = node.has("kind") && !node.get("kind").isNull()
                ? node.get("kind").asText()
                : "";
        if (inner != null) {
            onArtifact.accept(kind, inner);
            if (persistHook != null && "chapter_generation_final".equals(kind)) {
                persistHook.onChapterGenerationFinal(inner, emitter);
            }
        }
    }
}
