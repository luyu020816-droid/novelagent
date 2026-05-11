package com.mythosforge.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.mythosforge.chapter.GenerationJobService;
import com.mythosforge.chapter.dto.GenerationJobFailRequest;
import com.mythosforge.chapter.dto.GenerationJobProgressRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/generation-jobs")
public class InternalGenerationJobController {

    private final GenerationJobService generationJobService;

    public InternalGenerationJobController(GenerationJobService generationJobService) {
        this.generationJobService = generationJobService;
    }

    @GetMapping("/{jobId}/payload")
    public JsonNode payload(@PathVariable String jobId) {
        return generationJobService.getPayloadForWorker(jobId);
    }

    @PostMapping("/{jobId}/progress")
    public ResponseEntity<Void> progress(
            @PathVariable String jobId,
            @RequestBody GenerationJobProgressRequest body
    ) {
        generationJobService.applyProgress(jobId, body);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{jobId}/complete")
    public ResponseEntity<Void> complete(@PathVariable String jobId, @RequestBody JsonNode body) {
        generationJobService.applyComplete(jobId, body);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{jobId}/fail")
    public ResponseEntity<Void> fail(@PathVariable String jobId, @RequestBody(required = false) GenerationJobFailRequest body) {
        String msg = body != null && body.message() != null ? body.message() : "unknown error";
        generationJobService.applyFail(jobId, msg);
        return ResponseEntity.accepted().build();
    }
}
